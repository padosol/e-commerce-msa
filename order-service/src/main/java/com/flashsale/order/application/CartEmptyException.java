package com.flashsale.order.application;

public class CartEmptyException extends RuntimeException {

    public CartEmptyException() {
        super("cart is empty");
    }
}
