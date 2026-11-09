package com.flashsale.order.domain;

/** One product in an order, with the name and unit price copied at the moment the order was made. */
public record OrderLine(long productId, String productName, long unitPrice, int quantity) {

    public OrderLine {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1: " + quantity);
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative: " + unitPrice);
        }
    }

    public long subtotal() {
        return unitPrice * quantity;
    }
}
