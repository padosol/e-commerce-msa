package com.flashsale.product.adapter.in.web;

import com.flashsale.product.domain.Product;

public record ProductResponse(long productId, String name, long price) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.id(), p.name(), p.price());
    }
}
