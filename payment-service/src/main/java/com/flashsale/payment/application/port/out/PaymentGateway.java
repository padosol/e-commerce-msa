package com.flashsale.payment.application.port.out;

import com.flashsale.payment.domain.PaymentStatus;

/** Talks to the payment provider. Blocks for as long as the provider takes. */
public interface PaymentGateway {

    PaymentStatus pay(long orderId, long userId, long amount);
}
