package com.flashsale.order.domain;

public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(int quantity) {
        super("quantity must be between " + Cart.MIN_QUANTITY + " and " + Cart.MAX_QUANTITY + ": " + quantity);
    }
}
