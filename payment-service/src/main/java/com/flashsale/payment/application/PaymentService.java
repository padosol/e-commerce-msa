package com.flashsale.payment.application;

import com.flashsale.payment.application.port.out.PaymentGateway;
import com.flashsale.payment.application.port.out.PaymentRepository;
import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.domain.PaymentStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository repository;

    public PaymentService(PaymentGateway gateway, PaymentRepository repository) {
        this.gateway = gateway;
        this.repository = repository;
    }

    /** Not transactional: the gateway call must not hold a DB connection. save() is its own short transaction. */
    public PaymentResult pay(long orderId, long userId, long amount) {
        PaymentStatus status = gateway.pay(orderId, userId, amount);
        Payment saved = repository.save(Payment.record(orderId, userId, amount, status, Instant.now()));
        return new PaymentResult(saved.id(), status);
    }
}
