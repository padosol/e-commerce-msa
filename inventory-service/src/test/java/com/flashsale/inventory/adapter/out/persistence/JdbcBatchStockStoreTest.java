package com.flashsale.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.application.port.out.BatchStockStore;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import com.flashsale.inventory.domain.StockLine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "inventory.strategy=naive")
@Import(TestcontainersConfiguration.class)
class JdbcBatchStockStoreTest {

    private static final long A = 1L;
    private static final long B = 2L;

    @Autowired
    BatchStockStore store;

    @Autowired
    InventoryJpaRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private void stock(long productId, int quantity) {
        repository.save(new InventoryJpaEntity(productId, quantity));
    }

    private int quantity(long productId) {
        return repository.findById(productId).orElseThrow().getQuantity();
    }

    @Test
    void decreasesEveryLineWhenEnough() {
        stock(A, 5);
        stock(B, 5);

        DecreaseResult result = store.decreaseAll(List.of(new StockLine(A, 2), new StockLine(B, 1)));

        assertThat(result).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(quantity(A)).isEqualTo(3);
        assertThat(quantity(B)).isEqualTo(4);
    }

    @Test
    void outOfStockLeavesEveryRowUnchanged() {
        stock(A, 5);
        stock(B, 0);

        DecreaseResult result = store.decreaseAll(List.of(new StockLine(A, 2), new StockLine(B, 1)));

        assertThat(result).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(A)).isEqualTo(5);
        assertThat(quantity(B)).isZero();
    }

    @Test
    void missingRowIsOutOfStock() {
        stock(A, 5);

        DecreaseResult result = store.decreaseAll(List.of(new StockLine(A, 1), new StockLine(999L, 1)));

        assertThat(result).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(A)).isEqualTo(5);
    }

    @Test
    void opposingOrderBatchesNeitherDeadlockNorOversell() throws Exception {
        stock(A, 30);
        stock(B, 30);
        int threads = 80;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DecreaseResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                List<StockLine> lines = i % 2 == 0
                    ? List.of(new StockLine(A, 1), new StockLine(B, 1))
                    : List.of(new StockLine(B, 1), new StockLine(A, 1));
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.decreaseAll(lines);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long successes = 0;
            for (Future<DecreaseResult> f : futures) {
                if (f.get(60, TimeUnit.SECONDS) == DecreaseResult.SUCCESS) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(30);
        } finally {
            pool.shutdownNow();
        }
        assertThat(quantity(A)).isZero();
        assertThat(quantity(B)).isZero();
    }

    @Test
    void increaseAllRestoresEveryLine() {
        stock(A, 3);
        stock(B, 4);

        store.increaseAll(List.of(new StockLine(A, 2), new StockLine(B, 1)));

        assertThat(quantity(A)).isEqualTo(5);
        assertThat(quantity(B)).isEqualTo(5);
    }

    @Test
    void increaseAllWithUnknownProductRollsBack() {
        stock(A, 3);

        assertThatThrownBy(() -> store.increaseAll(List.of(new StockLine(A, 1), new StockLine(999L, 1))))
            .isInstanceOf(InventoryNotFoundException.class);

        assertThat(quantity(A)).isEqualTo(3);
    }
}
