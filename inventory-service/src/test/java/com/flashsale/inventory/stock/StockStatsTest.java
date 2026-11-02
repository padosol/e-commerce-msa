package com.flashsale.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class StockStatsTest {

    @Test
    void countsAndResets() {
        StockStats stats = new StockStats();
        stats.conflict();
        stats.conflict();
        stats.retry();
        stats.exhausted();

        assertThat(stats.snapshot()).isEqualTo(new StockStats.Snapshot(2, 1, 1));

        stats.reset();

        assertThat(stats.snapshot()).isEqualTo(new StockStats.Snapshot(0, 0, 0));
    }

    @Test
    void exposesThreeFunctionCounters() {
        StockStats stats = new StockStats();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        stats.bindTo(registry);
        stats.conflict();
        stats.conflict();
        stats.retry();
        stats.exhausted();

        assertThat(registry.get("inventory.optimistic.conflicts").functionCounter().count()).isEqualTo(2);
        assertThat(registry.get("inventory.optimistic.retries").functionCounter().count()).isEqualTo(1);
        assertThat(registry.get("inventory.optimistic.exhausted").functionCounter().count()).isEqualTo(1);
    }
}
