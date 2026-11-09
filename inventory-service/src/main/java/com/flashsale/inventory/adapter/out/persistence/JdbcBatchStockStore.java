package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.application.port.out.BatchStockStore;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import com.flashsale.inventory.domain.StockLine;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 전략(StockStrategy, INVENTORY_STRATEGY)과 무관한 일괄 차감·복구.
 *
 * 트랜잭션 하나에서 조건부 UPDATE 를 productId 오름차순으로 실행한다. 모든 호출이 같은 순서로
 * 행 잠금을 잡으므로 서로 반대 순서의 주문끼리도 교착이 생기지 않고, 조건(quantity >= ?)이
 * 초과 판매를 막는다.
 */
@Component
class JdbcBatchStockStore implements BatchStockStore {

    private static final String DECREASE =
        "UPDATE inventory SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?";
    private static final String INCREASE =
        "UPDATE inventory SET quantity = quantity + ? WHERE product_id = ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    JdbcBatchStockStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public DecreaseResult decreaseAll(List<StockLine> lines) {
        return tx.execute(status -> {
            for (StockLine line : sorted(lines)) {
                int updated = jdbc.update(DECREASE, line.quantity(), line.productId(), line.quantity());
                if (updated == 0) {
                    status.setRollbackOnly();
                    return DecreaseResult.OUT_OF_STOCK;
                }
            }
            return DecreaseResult.SUCCESS;
        });
    }

    @Override
    public void increaseAll(List<StockLine> lines) {
        tx.executeWithoutResult(status -> {
            for (StockLine line : sorted(lines)) {
                if (jdbc.update(INCREASE, line.quantity(), line.productId()) == 0) {
                    throw new InventoryNotFoundException(line.productId());
                }
            }
        });
    }

    private static List<StockLine> sorted(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparingLong(StockLine::productId)).toList();
    }
}
