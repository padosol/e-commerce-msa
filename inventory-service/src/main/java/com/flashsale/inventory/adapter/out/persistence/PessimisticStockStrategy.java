package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.application.port.out.StockStrategy;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SELECT ... FOR UPDATE 로 행 락을 잡고 읽은 뒤 비교·갱신하는 전략.
 * 락은 메서드 트랜잭션이 커밋될 때까지 유지되므로 같은 상품의 요청은 직렬화된다.
 */
@Component
@ConditionalOnProperty(name = "inventory.strategy", havingValue = "pessimistic")
public class PessimisticStockStrategy implements StockStrategy {

    private final JdbcTemplate jdbc;

    public PessimisticStockStrategy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "pessimistic";
    }

    @Override
    @Transactional
    public DecreaseResult decrease(long productId, int quantity) {
        int current = lockAndRead(productId);
        if (current < quantity) {
            return DecreaseResult.OUT_OF_STOCK;
        }
        write(productId, current - quantity);
        return DecreaseResult.SUCCESS;
    }

    @Override
    @Transactional
    public void increase(long productId, int quantity) {
        write(productId, lockAndRead(productId) + quantity);
    }

    private int lockAndRead(long productId) {
        List<Integer> rows = jdbc.queryForList(
                "SELECT quantity FROM inventory WHERE product_id = ? FOR UPDATE", Integer.class, productId);
        if (rows.isEmpty()) {
            throw new InventoryNotFoundException(productId);
        }
        return rows.get(0);
    }

    private void write(long productId, int quantity) {
        jdbc.update("UPDATE inventory SET quantity = ? WHERE product_id = ?", quantity, productId);
    }
}
