package com.flashsale.inventory.application.port.out;

import com.flashsale.inventory.domain.DecreaseResult;

public interface StockStrategy {

    String name();

    DecreaseResult decrease(long productId, int quantity);

    void increase(long productId, int quantity);
}
