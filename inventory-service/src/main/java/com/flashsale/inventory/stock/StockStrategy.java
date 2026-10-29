package com.flashsale.inventory.stock;

public interface StockStrategy {

    String name();

    DecreaseResult decrease(long productId, int quantity);

    void increase(long productId, int quantity);
}
