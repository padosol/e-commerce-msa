package com.flashsale.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.domain.Inventory;
import com.flashsale.inventory.domain.InventoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"inventory.strategy=optimistic", "inventory.optimistic.max-retries=0"})
@Import(TestcontainersConfiguration.class)
class OptimisticNoRetryTest {

    @Autowired
    OptimisticStockStrategy strategy;

    @Autowired
    InventoryRepository repository;

    @Autowired
    StockStats stats;

    @Autowired
    MySQLContainer mysql;

    ForcedConflict onceConflict;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        onceConflict = new ForcedConflict(mysql);
    }

    @AfterEach
    void tearDown() {
        strategy.setAfterReadForTest(() -> { });
        onceConflict.close();
    }

    @Test
    void forcedConflictWithoutRetryReturnsConflict() {
        repository.save(new Inventory(1L, 10));
        stats.reset();
        strategy.setAfterReadForTest(onceConflict);

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.CONFLICT);
        assertThat(quantity(1L)).isEqualTo(9);
        assertThat(stats.snapshot()).isEqualTo(new StockStats.Snapshot(1, 0, 1));
    }

    @Test
    void nameIsR0() {
        assertThat(strategy.name()).isEqualTo("optimistic-r0");
    }

    private int quantity(long productId) {
        return repository.findById(productId).orElseThrow().getQuantity();
    }
}
