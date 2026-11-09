package com.flashsale.order.domain;

import java.time.Instant;
import java.util.List;

/** An order with one or more lines. It moves from PENDING to PAID or FAILED exactly once. */
public final class Order {

    private final Long id;
    private final long userId;
    private final Long eventId;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private FailReason failReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(Long id, long userId, Long eventId, List<OrderLine> lines, OrderStatus status,
                  FailReason failReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.lines = List.copyOf(lines);
        this.status = status;
        this.failReason = failReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new order. eventId is null for an order that is not a flash-sale event order. */
    public static Order pending(long userId, Long eventId, List<OrderLine> lines, Instant now) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        return new Order(null, userId, eventId, lines, OrderStatus.PENDING, null, now, now);
    }

    public static Order restore(long id, long userId, Long eventId, List<OrderLine> lines, OrderStatus status,
                                FailReason failReason, Instant createdAt, Instant updatedAt) {
        return new Order(id, userId, eventId, lines, status, failReason, createdAt, updatedAt);
    }

    public void markPaid(Instant now) {
        requirePending();
        this.status = OrderStatus.PAID;
        this.failReason = null;
        this.updatedAt = now;
    }

    public void markFailed(FailReason reason, Instant now) {
        requirePending();
        this.status = OrderStatus.FAILED;
        this.failReason = reason;
        this.updatedAt = now;
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("order " + id + " is " + status + ", not PENDING");
        }
    }

    public Long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public Long eventId() {
        return eventId;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public long amount() {
        long sum = 0;
        for (OrderLine line : lines) {
            sum += line.subtotal();
        }
        return sum;
    }

    public OrderStatus status() {
        return status;
    }

    public FailReason failReason() {
        return failReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
