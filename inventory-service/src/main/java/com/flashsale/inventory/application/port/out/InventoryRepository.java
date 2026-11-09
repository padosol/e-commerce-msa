package com.flashsale.inventory.application.port.out;

import com.flashsale.inventory.domain.Inventory;
import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findById(long productId);

    /** 있으면 수량만 갱신하고, 없으면 만든다. version 은 건드리지 않는다. */
    void save(Inventory inventory);
}
