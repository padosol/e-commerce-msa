package com.flashsale.payment;

import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.domain.PaymentRepository;
import com.flashsale.payment.domain.PaymentStatus;
import com.flashsale.payment.simulation.PaymentSimulator;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentSimulator simulator;
    private final PaymentRepository repository;

    public PaymentService(PaymentSimulator simulator, PaymentRepository repository) {
        this.simulator = simulator;
        this.repository = repository;
    }

    /** Not transactional: the simulated delay must not hold a DB connection. save() is its own short transaction. */
    public PaymentResult pay(long orderId, long userId, long amount) {
        try {
            Thread.sleep(simulator.nextDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while simulating payment", e);
        }
        PaymentStatus status = simulator.shouldDecline() ? PaymentStatus.DECLINED : PaymentStatus.APPROVED;
        Payment saved = repository.save(new Payment(orderId, userId, amount, status));
        return new PaymentResult(saved.getId(), status);
    }
}
