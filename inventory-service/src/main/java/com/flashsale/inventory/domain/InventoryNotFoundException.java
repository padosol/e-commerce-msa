package com.flashsale.inventory.domain;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(long productId) {
        super("Inventory not found for product " + productId);
    }
}
