package com.flashsale.product.domain;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("product not found: " + productId);
    }
}
