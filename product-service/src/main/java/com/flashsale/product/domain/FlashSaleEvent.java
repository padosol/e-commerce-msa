package com.flashsale.product.domain;

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
public class FlashSaleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "open_at", nullable = false)
    private Instant openAt;

    @Column(name = "sale_quantity", nullable = false)
    private int saleQuantity;

    protected FlashSaleEvent() {
    }

    public FlashSaleEvent(Product product, Instant openAt, int saleQuantity) {
        this.product = product;
        this.openAt = openAt;
        this.saleQuantity = saleQuantity;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Instant getOpenAt() {
        return openAt;
    }

    public int getSaleQuantity() {
        return saleQuantity;
    }
}
