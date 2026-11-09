package com.flashsale.inventory.domain;

public record Inventory(long productId, int quantity) {

    public Inventory {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative: " + quantity);
        }
    }
}
