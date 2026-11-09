package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.application.port.out.StockStrategy;
import com.flashsale.inventory.domain.DecreaseResult;
import com.flashsale.inventory.domain.InventoryNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 0단계의 의도된 "깨진" 재고 차감 전략.
 *
 * 수량을 읽고 → 애플리케이션에서 비교하고 → 절대값으로 덮어쓴다. 락, 버전(@Version),
 * 조건부 UPDATE 를 일부러 쓰지 않으므로 동시 요청에서 차감 유실과 초과 판매가 발생한다.
 * 이 경쟁 조건이 이후 단계에서 해결할 문제의 기준선이다. 안전하게 "고치지" 말 것.
 */
@Component
@ConditionalOnProperty(name = "inventory.strategy", havingValue = "naive", matchIfMissing = true)
public class NaiveStockStrategy implements StockStrategy {

    private final InventoryJpaRepository repository;

    public NaiveStockStrategy(InventoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "naive";
    }

    @Override
    @Transactional
    public DecreaseResult decrease(long productId, int quantity) {
        InventoryJpaEntity inventory = find(productId);
        if (inventory.getQuantity() < quantity) {
            return DecreaseResult.OUT_OF_STOCK;
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        return DecreaseResult.SUCCESS;
    }

    @Override
    @Transactional
    public void increase(long productId, int quantity) {
        InventoryJpaEntity inventory = find(productId);
        inventory.setQuantity(inventory.getQuantity() + quantity);
    }

    private InventoryJpaEntity find(long productId) {
        return repository.findById(productId).orElseThrow(() -> new InventoryNotFoundException(productId));
    }
}
