package com.flashsale.payment.adapter.out.persistence;

import com.flashsale.payment.domain.Payment;
import com.flashsale.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private long orderId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentJpaEntity() {
    }

    static PaymentJpaEntity from(Payment payment) {
        PaymentJpaEntity e = new PaymentJpaEntity();
        e.id = payment.id();
        e.orderId = payment.orderId();
        e.userId = payment.userId();
        e.amount = payment.amount();
        e.status = payment.status();
        e.createdAt = payment.createdAt();
        return e;
    }

    Payment toDomain() {
        return Payment.restore(id, orderId, userId, amount, status, createdAt);
    }

    public Long getId() {
        return id;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
