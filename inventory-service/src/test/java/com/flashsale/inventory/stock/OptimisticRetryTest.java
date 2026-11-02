package com.flashsale.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.domain.Inventory;
import com.flashsale.inventory.domain.InventoryRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"inventory.strategy=optimistic", "inventory.optimistic.max-retries=1"})
@Import({TestcontainersConfiguration.class, OptimisticRetryTest.CountingDataSourceConfig.class})
class OptimisticRetryTest {

    @Autowired
    OptimisticStockStrategy strategy;

    @Autowired
    InventoryRepository repository;

    @Autowired
    StockStats stats;

    @Autowired
    MySQLContainer mysql;

    @Autowired
    CountingDataSource counting;

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
    void forcedConflictIsRetriedAndSucceeds() {
        repository.save(new Inventory(1L, 10));
        stats.reset();
        strategy.setAfterReadForTest(onceConflict);

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(quantity(1L)).isEqualTo(8);
        assertThat(stats.snapshot()).isEqualTo(new StockStats.Snapshot(1, 1, 0));
    }

    @Test
    void retriesReuseOneConnection() {
        repository.save(new Inventory(1L, 10));
        strategy.setAfterReadForTest(onceConflict);
        long before = counting.count();

        assertThat(strategy.decrease(1L, 1)).isEqualTo(DecreaseResult.SUCCESS);
        assertThat(counting.count() - before).isEqualTo(1);
        assertThat(quantity(1L)).isEqualTo(8); // 충돌이 실제로 일어나 재시도까지 갔다는 증거
    }

    private int quantity(long productId) {
        return repository.findById(productId).orElseThrow().getQuantity();
    }

    /** 풀에서 커넥션을 빌린 횟수를 센다. */
    static class CountingDataSource extends DelegatingDataSource {

        private final AtomicLong count = new AtomicLong();

        CountingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            count.incrementAndGet();
            return super.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            count.incrementAndGet();
            return super.getConnection(username, password);
        }

        long count() {
            return count.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CountingDataSourceConfig {

        @Bean
        static BeanPostProcessor countingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource && !(bean instanceof CountingDataSource)) {
                        return new CountingDataSource(dataSource);
                    }
                    return bean;
                }
            };
        }
    }
}
