package com.flashsale.order.application.port.out;

/** Failures (error response, timeout, dropped connection) surface as DownstreamException. No retries. */
public interface PaymentPort {

    PaymentOutcome pay(long orderId, long userId, long amount);
}
