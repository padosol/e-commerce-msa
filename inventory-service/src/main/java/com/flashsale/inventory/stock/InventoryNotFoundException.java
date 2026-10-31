package com.flashsale.inventory.stock;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(long productId) {
        super("Inventory not found for product " + productId);
    }
}
