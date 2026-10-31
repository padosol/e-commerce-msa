package com.flashsale.order.domain;

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
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", length = 32)
    private FailReason failReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(long userId, long eventId, long productId, int quantity, long amount, Instant now) {
        this.userId = userId;
        this.eventId = eventId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markPaid(Instant now) {
        this.status = OrderStatus.PAID;
        this.failReason = null;
        this.updatedAt = now;
    }

    public void markFailed(FailReason reason, Instant now) {
        this.status = OrderStatus.FAILED;
        this.failReason = reason;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public long getEventId() { return eventId; }
    public long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public long getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public FailReason getFailReason() { return failReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
