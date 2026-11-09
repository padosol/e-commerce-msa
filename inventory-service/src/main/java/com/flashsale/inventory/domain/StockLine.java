package com.flashsale.inventory.domain;

public record StockLine(long productId, int quantity) {

    public StockLine {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
    }
}
