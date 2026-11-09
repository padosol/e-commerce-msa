package com.flashsale.inventory.adapter.out.persistence;

import com.flashsale.inventory.domain.Inventory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** version 컬럼은 일부러 매핑하지 않는다(@Version 금지). 낙관적 전략이 자기 SQL 로 직접 다룬다. */
@Entity
@Table(name = "inventory")
public class InventoryJpaEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    protected InventoryJpaEntity() {
    }

    public InventoryJpaEntity(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    static InventoryJpaEntity from(Inventory inventory) {
        return new InventoryJpaEntity(inventory.productId(), inventory.quantity());
    }

    Inventory toDomain() {
        return new Inventory(productId, quantity);
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
