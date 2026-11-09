package com.flashsale.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.application.StockStats;
import com.flashsale.inventory.domain.DecreaseResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"inventory.strategy=optimistic", "inventory.optimistic.max-retries=3"})
@Import(TestcontainersConfiguration.class)
class OptimisticStockStrategyTest extends StockStrategyContract {

    @Autowired
    StockStats stats;

    @Override
    protected boolean sellsOutUnderContention() {
        return false;
    }

    @Test
    void nameIncludesRetries() {
        assertThat(strategy.name()).isEqualTo("optimistic-r3");
    }

    @Test
    void conflictsEqualRetriesPlusExhausted() throws Exception {
        repository.save(new InventoryJpaEntity(1L, 50));
        stats.reset();

        Map<DecreaseResult, Long> results = decreaseConcurrently(200, 32);
        long successes = results.getOrDefault(DecreaseResult.SUCCESS, 0L);
        long outOfStock = results.getOrDefault(DecreaseResult.OUT_OF_STOCK, 0L);
        long conflictResults = results.getOrDefault(DecreaseResult.CONFLICT, 0L);
        int remaining = quantity(1L);
        StockStats.Snapshot s = stats.snapshot();

        assertThat(successes + remaining).isEqualTo(50);
        assertThat(s.conflicts()).isEqualTo(s.retries() + s.exhausted());
        assertThat(s.exhausted()).isEqualTo(conflictResults);
        assertThat(s.retries()).isLessThanOrEqualTo(200L * 3);
        assertThat(successes + outOfStock + conflictResults).isEqualTo(200);
    }
}
