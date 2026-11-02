package com.flashsale.inventory.stock;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 낙관적 전략의 "읽기 직후·UPDATE 직전" 훅으로 끼워 넣어, 상품 1의 행을 한 번만 다른 쪽에서 갱신해
 * 충돌을 확정적으로 만든다. 갱신은 커넥션 풀 밖의 별도 커넥션으로 하므로 풀의 커넥션 수에 섞이지 않는다.
 */
final class ForcedConflict implements Runnable, AutoCloseable {

    private final SingleConnectionDataSource other;
    private final AtomicBoolean fired = new AtomicBoolean();

    ForcedConflict(MySQLContainer mysql) {
        this.other = new SingleConnectionDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), true);
    }

    @Override
    public void run() {
        if (fired.compareAndSet(false, true)) {
            new JdbcTemplate(other).update(
                    "UPDATE inventory SET quantity = quantity - 1, version = version + 1 WHERE product_id = 1");
        }
    }

    @Override
    public void close() {
        other.destroy();
    }
}
