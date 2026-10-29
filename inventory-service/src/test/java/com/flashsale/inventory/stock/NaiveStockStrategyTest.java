package com.flashsale.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.domain.Inventory;
import com.flashsale.inventory.domain.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NaiveStockStrategyTest {

    @Autowired
    StockStrategy strategy;

    @Autowired
    InventoryRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void decreaseSubtractsWhenEnough() {
        repository.save(new Inventory(1L, 5));

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(repository.findById(1L).orElseThrow().getQuantity()).isEqualTo(4);
    }

    @Test
    void decreaseReturnsOutOfStock() {
        repository.save(new Inventory(1L, 0));

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(repository.findById(1L).orElseThrow().getQuantity()).isZero();
    }

    @Test
    void increaseAdds() {
        repository.save(new Inventory(1L, 3));

        strategy.increase(1L, 2);

        assertThat(repository.findById(1L).orElseThrow().getQuantity()).isEqualTo(5);
    }

    @Test
    void unknownProductThrows() {
        assertThatThrownBy(() -> strategy.decrease(999L, 1)).isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void concurrentDecreasesLoseUpdates() throws Exception {
        repository.save(new Inventory(1L, 50));
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DecreaseResult>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return strategy.decrease(1L, 1);
            }));
        }
        start.countDown();
        long successes = 0;
        for (Future<DecreaseResult> f : futures) {
            if (f.get() == DecreaseResult.SUCCESS) {
                successes++;
            }
        }
        pool.shutdown();
        int remaining = repository.findById(1L).orElseThrow().getQuantity();

        assertThat(successes + remaining).isGreaterThan(50); // 차감 유실
        assertThat(successes).isGreaterThan(50);             // 초과 판매
    }
}
