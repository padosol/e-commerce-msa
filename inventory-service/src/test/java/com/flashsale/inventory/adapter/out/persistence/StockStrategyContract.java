package com.flashsale.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.inventory.application.port.out.StockStrategy;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    InventoryJpaRepository repository;

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
        repository.save(new InventoryJpaEntity(1L, 5));

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(quantity(1L)).isEqualTo(4);
    }

    @Test
    void decreaseReturnsOutOfStock() {
        repository.save(new InventoryJpaEntity(1L, 0));

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(1L)).isZero();
    }

    @Test
    void decreaseNeverGoesBelowZero() {
        repository.save(new InventoryJpaEntity(1L, 1));

        assertThat(strategy.decrease(1L, 2)).isEqualTo(DecreaseResult.OUT_OF_STOCK);
        assertThat(quantity(1L)).isEqualTo(1);
    }

    @Test
    void increaseAdds() {
        repository.save(new InventoryJpaEntity(1L, 3));

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
        repository.save(new InventoryJpaEntity(1L, 50));

        long successes = decreaseConcurrently(200, 32).getOrDefault(DecreaseResult.SUCCESS, 0L);
        int remaining = quantity(1L);

        assertThat(successes).isLessThanOrEqualTo(50);
        assertThat(successes + remaining).isEqualTo(50);
        if (sellsOutUnderContention()) {
            assertThat(successes).isEqualTo(50);
            assertThat(remaining).isZero();
        }
    }

    /** 스레드 {@code threads} 개를 동시에 출발시켜 상품 1을 1개씩 {@code requests} 번 차감하고, 결과별 개수를 돌려준다. */
    protected Map<DecreaseResult, Long> decreaseConcurrently(int requests, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<DecreaseResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return strategy.decrease(1L, 1);
                }));
            }
            start.countDown();
            Map<DecreaseResult, Long> counts = new EnumMap<>(DecreaseResult.class);
            for (Future<DecreaseResult> f : futures) {
                counts.merge(f.get(), 1L, Long::sum);
            }
            return counts;
        } finally {
            pool.shutdown();
        }
    }

    protected int quantity(long productId) {
        return repository.findById(productId).orElseThrow().getQuantity();
    }
}
