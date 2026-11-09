package com.flashsale.order.domain;

public class CartFullException extends RuntimeException {

    public CartFullException() {
        super("cart can hold at most " + Cart.MAX_PRODUCTS + " products");
    }
}
