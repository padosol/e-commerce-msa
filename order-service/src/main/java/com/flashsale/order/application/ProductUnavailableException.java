package com.flashsale.order.application;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(long productId) {
        super("product is not available: " + productId);
    }
}
