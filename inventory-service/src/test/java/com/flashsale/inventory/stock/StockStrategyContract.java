package com.flashsale.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * 올바른 재고 차감 전략이 공통으로 지켜야 하는 계약.
 * 하위 클래스는 {@code @SpringBootTest(properties = "inventory.strategy=<값>")} 와
 * {@code @Import(TestcontainersConfiguration.class)} 만 붙인다.
 */
abstract class StockStrategyContract {

    @Autowired
    StockStrategy strategy;

    @Autowired
    InventoryRepository repository;

    /** 경합 상황에서 재고를 끝까지 다 파는 전략이면 true. 충돌로 일부를 포기하는 전략은 false 로 바꾼다. */
    protected boolean sellsOutUnderContention() {
        return true;
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void decreaseSubtractsWhenEnough() {
        repository.save(new Inventory(1L, 5));

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(quantity(1L)).isEqualTo(4);
    }

    @Test
    void decreaseReturnsOutOfStock() {
        repository.save(new Inventory(1L, 0));

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(1L)).isZero();
    }

    @Test
    void decreaseNeverGoesBelowZero() {
        repository.save(new Inventory(1L, 1));

        assertThat(strategy.decrease(1L, 2)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(1L)).isEqualTo(1);
    }

    @Test
    void increaseAdds() {
        repository.save(new Inventory(1L, 3));

        strategy.increase(1L, 2);

        assertThat(quantity(1L)).isEqualTo(5);
    }

    @Test
    void unknownProductThrows() {
        assertThatThrownBy(() -> strategy.decrease(999L, 1)).isInstanceOf(InventoryNotFoundException.class);
        assertThatThrownBy(() -> strategy.increase(999L, 1)).isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void concurrentDecreasesNeverOversell() throws Exception {
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
        int remaining = quantity(1L);

        assertThat(successes).isLessThanOrEqualTo(50);
        assertThat(successes + remaining).isEqualTo(50);
        if (sellsOutUnderContention()) {
            assertThat(successes).isEqualTo(50);
            assertThat(remaining).isZero();
        }
    }

    private int quantity(long productId) {
        return repository.findById(productId).orElseThrow().getQuantity();
    }
}
