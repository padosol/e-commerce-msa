package com.flashsale.order.application.port.out;

import java.util.List;

/** Failures (error response, timeout, dropped connection) surface as DownstreamException. No retries. */
public interface InventoryPort {

    StockDecreaseResult decrease(long productId, int quantity);

    void increase(long productId, int quantity);

    /** All lines or none are decreased. */
    StockDecreaseResult decreaseAll(List<StockLine> lines);

    void increaseAll(List<StockLine> lines);
}
