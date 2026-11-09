package com.flashsale.payment.adapter.out.persistence;

import com.flashsale.payment.application.port.out.PaymentRepository;
import com.flashsale.payment.domain.Payment;
import org.springframework.stereotype.Component;

@Component
class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    PaymentPersistenceAdapter(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Payment save(Payment payment) {
        return jpa.save(PaymentJpaEntity.from(payment)).toDomain();
    }

    @Override
    public void deleteAll() {
        jpa.deleteAllInBatch();
    }
}
