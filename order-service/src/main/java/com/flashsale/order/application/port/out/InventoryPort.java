package com.flashsale.order.application.port.out;

/** Failures (error response, timeout, dropped connection) surface as DownstreamException. No retries. */
public interface InventoryPort {

    StockDecreaseResult decrease(long productId, int quantity);

    void increase(long productId, int quantity);
}
