package com.flashsale.order.adapter.out.persistence;

import com.flashsale.order.domain.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row of order_items. Written once with its order and never updated; order_id is set by OrderJpaEntity. */
@Entity
@Table(name = "order_items")
public class OrderLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private long productId;

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    protected OrderLineJpaEntity() {
    }

    static OrderLineJpaEntity from(OrderLine line) {
        OrderLineJpaEntity e = new OrderLineJpaEntity();
        e.productId = line.productId();
        e.productName = line.productName();
        e.unitPrice = line.unitPrice();
        e.quantity = line.quantity();
        return e;
    }

    OrderLine toDomain() {
        return new OrderLine(productId, productName, unitPrice, quantity);
    }
}
