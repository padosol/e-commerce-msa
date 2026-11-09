package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.application.port.out.InventoryRepository;
import com.flashsale.inventory.domain.Inventory;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class InventoryPersistenceAdapter implements InventoryRepository {

    private final InventoryJpaRepository jpa;

    InventoryPersistenceAdapter(InventoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Inventory> findById(long productId) {
        return jpa.findById(productId).map(InventoryJpaEntity::toDomain);
    }

    /** 있으면 수량만 갱신하고 없으면 만든다. version 컬럼은 매핑되어 있지 않아 건드리지 않는다. */
    @Override
    public void save(Inventory inventory) {
        jpa.save(InventoryJpaEntity.from(inventory));
    }
}
