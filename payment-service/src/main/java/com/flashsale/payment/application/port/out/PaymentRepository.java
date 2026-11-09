package com.flashsale.payment.application.port.out;

import com.flashsale.payment.domain.Payment;

public interface PaymentRepository {

    Payment save(Payment payment);

    void deleteAll();
}
