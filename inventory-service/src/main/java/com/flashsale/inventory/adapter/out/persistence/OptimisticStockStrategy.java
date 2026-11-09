package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.application.StockStats;
import com.flashsale.inventory.application.port.out.StockStrategy;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 락 없이 {@code quantity, version} 을 읽고 {@code version} 이 그대로일 때만 갱신하는 전략.
 * 갱신된 행이 0이면 충돌이고, 최대 {@code maxRetries} 번까지 기다리지 않고 바로 다시 읽고 시도한다.
 *
 * <p>재시도 규칙(설계 §4):
 * <ul>
 *   <li>한 요청의 모든 시도는 풀에서 빌린 커넥션 하나로 한다. 시도 사이에 커넥션을 돌려주면 풀 대기가
 *       숨은 백오프가 된다. 그래서 콜백 안에서는 {@code JdbcTemplate} 메서드(호출마다 커넥션을 새로 빌림)가
 *       아니라 받은 커넥션에 직접 문장을 실행한다.</li>
 *   <li>시도마다 autocommit 문장으로 새로 읽는다. {@code @Transactional} 로 묶으면 REPEATABLE READ 가
 *       처음 스냅샷을 계속 보여 줘 재시도가 영원히 충돌한다. 같은 이유로 JPA 엔티티를 쓰지 않는다.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "inventory.strategy", havingValue = "optimistic")
@EnableConfigurationProperties(OptimisticProperties.class)
public class OptimisticStockStrategy implements StockStrategy {

    private static final String READ = "SELECT quantity, version FROM inventory WHERE product_id = ?";
    private static final String WRITE =
            "UPDATE inventory SET quantity = ?, version = version + 1 WHERE product_id = ? AND version = ?";

    private final JdbcTemplate jdbc;
    private final StockStats stats;
    private final int maxRetries;
    private volatile Runnable afterRead = () -> { };

    public OptimisticStockStrategy(JdbcTemplate jdbc, StockStats stats, OptimisticProperties properties) {
        this.jdbc = jdbc;
        this.stats = stats;
        this.maxRetries = properties.maxRetries();
    }

    @Override
    public String name() {
        return "optimistic-r" + maxRetries;
    }

    @Override
    public DecreaseResult decrease(long productId, int quantity) {
        return jdbc.execute((ConnectionCallback<DecreaseResult>) con -> decreaseOn(con, productId, quantity));
    }

    private DecreaseResult decreaseOn(Connection con, long productId, int quantity) throws SQLException {
        try (PreparedStatement read = con.prepareStatement(READ);
             PreparedStatement write = con.prepareStatement(WRITE)) {
            for (int attempt = 0; ; attempt++) {
                read.setLong(1, productId);
                int current;
                long version;
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) {
                        throw new InventoryNotFoundException(productId);
                    }
                    current = rs.getInt(1);
                    version = rs.getLong(2);
                }
                if (current < quantity) {
                    return DecreaseResult.OUT_OF_STOCK;
                }
                afterRead.run();
                write.setInt(1, current - quantity);
                write.setLong(2, productId);
                write.setLong(3, version);
                if (write.executeUpdate() == 1) {
                    return DecreaseResult.SUCCESS;
                }
                stats.conflict();
                if (attempt == maxRetries) {
                    stats.exhausted();
                    return DecreaseResult.CONFLICT;
                }
                stats.retry();
            }
        }
    }

    @Override
    public void increase(long productId, int quantity) {
        int updated = jdbc.update(
                "UPDATE inventory SET quantity = quantity + ?, version = version + 1 WHERE product_id = ?",
                quantity, productId);
        if (updated == 0) {
            throw new InventoryNotFoundException(productId);
        }
    }

    /** 테스트 전용: 읽기 직후·UPDATE 직전에 실행할 훅. 충돌을 확정적으로 만들 때 쓴다. 기본은 아무것도 안 함. */
    void setAfterReadForTest(Runnable hook) {
        this.afterRead = hook;
    }
}
