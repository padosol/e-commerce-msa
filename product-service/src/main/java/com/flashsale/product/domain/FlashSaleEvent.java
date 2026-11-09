package com.flashsale.product.domain;

import java.time.Instant;

public class FlashSaleEvent {

    private final Long id;
    private final Product product;
    private final Instant openAt;
    private final int saleQuantity;

    private FlashSaleEvent(Long id, Product product, Instant openAt, int saleQuantity) {
        this.id = id;
        this.product = product;
        this.openAt = openAt;
        this.saleQuantity = saleQuantity;
    }

    public static FlashSaleEvent create(Product product, Instant openAt, int saleQuantity) {
        return new FlashSaleEvent(null, product, openAt, saleQuantity);
    }

    public static FlashSaleEvent restore(long id, Product product, Instant openAt, int saleQuantity) {
        return new FlashSaleEvent(id, product, openAt, saleQuantity);
    }

    public Long id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public Instant openAt() {
        return openAt;
    }

    public int saleQuantity() {
        return saleQuantity;
    }
}
