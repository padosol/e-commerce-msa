package com.flashsale.inventory.stock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 조건부 UPDATE 로 차감하는 전략. 비교와 차감이 한 문장 안에서 원자적으로 일어나므로
 * 별도 트랜잭션 없이(문장 하나가 곧 트랜잭션) 초과 판매가 생기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "inventory.strategy", havingValue = "conditional")
public class ConditionalStockStrategy implements StockStrategy {

    private final JdbcTemplate jdbc;

    public ConditionalStockStrategy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "conditional";
    }

    @Override
    public DecreaseResult decrease(long productId, int quantity) {
        int updated = jdbc.update(
                "UPDATE inventory SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?",
                quantity, productId, quantity);
        if (updated == 1) {
            return DecreaseResult.SUCCESS;
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE product_id = ?", Integer.class, productId);
        if (exists == null || exists == 0) {
            throw new InventoryNotFoundException(productId);
        }
        return DecreaseResult.OUT_OF_STOCK;
    }

    @Override
    public void increase(long productId, int quantity) {
        int updated = jdbc.update("UPDATE inventory SET quantity = quantity + ? WHERE product_id = ?", quantity, productId);
        if (updated == 0) {
            throw new InventoryNotFoundException(productId);
        }
    }
}
