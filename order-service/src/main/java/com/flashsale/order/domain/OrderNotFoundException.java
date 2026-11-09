package com.flashsale.order.domain;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long orderId) {
        super("order not found: " + orderId);
    }
}
