package com.flashsale.payment.domain;

import java.time.Instant;

public final class Payment {

    private final Long id;
    private final long orderId;
    private final long userId;
    private final long amount;
    private final PaymentStatus status;
    private final Instant createdAt;

    private Payment(Long id, long orderId, long userId, long amount, PaymentStatus status, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Payment record(long orderId, long userId, long amount, PaymentStatus status, Instant now) {
        return new Payment(null, orderId, userId, amount, status, now);
    }

    public static Payment restore(Long id, long orderId, long userId, long amount, PaymentStatus status,
                                  Instant createdAt) {
        return new Payment(id, orderId, userId, amount, status, createdAt);
    }

    public Long id() {
        return id;
    }

    public long orderId() {
        return orderId;
    }

    public long userId() {
        return userId;
    }

    public long amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
