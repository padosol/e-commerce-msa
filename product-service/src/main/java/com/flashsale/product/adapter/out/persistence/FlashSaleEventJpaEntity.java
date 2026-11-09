package com.flashsale.product.adapter.out.persistence;

import com.flashsale.product.domain.FlashSaleEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "flash_sale_events")
public class FlashSaleEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "open_at", nullable = false)
    private Instant openAt;

    @Column(name = "sale_quantity", nullable = false)
    private int saleQuantity;

    protected FlashSaleEventJpaEntity() {
    }

    static FlashSaleEventJpaEntity from(FlashSaleEvent event) {
        FlashSaleEventJpaEntity entity = new FlashSaleEventJpaEntity();
        entity.id = event.id();
        entity.product = ProductJpaEntity.from(event.product());
        entity.openAt = event.openAt();
        entity.saleQuantity = event.saleQuantity();
        return entity;
    }

    FlashSaleEvent toDomain() {
        return FlashSaleEvent.restore(id, product.toDomain(), openAt, saleQuantity);
    }
}
