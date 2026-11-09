package com.flashsale.order.adapter.out.persistence;

import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Only status, fail_reason and updated_at are updatable, so a status change is a single
 * UPDATE of those three columns. Lines are inserted with the order (cascade PERSIST only) and never rewritten.
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "event_id", updatable = false)
    private Long eventId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", length = 32)
    private FailReason failReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("id")
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    /** For a new order only; an existing order is changed through {@link #changeStatus}. */
    static OrderJpaEntity from(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.userId = order.userId();
        e.eventId = order.eventId();
        e.amount = order.amount();
        e.status = order.status();
        e.failReason = order.failReason();
        e.createdAt = order.createdAt();
        e.updatedAt = order.updatedAt();
        order.lines().forEach(line -> e.lines.add(OrderLineJpaEntity.from(line)));
        return e;
    }

    void changeStatus(OrderStatus status, FailReason failReason, Instant updatedAt) {
        this.status = status;
        this.failReason = failReason;
        this.updatedAt = updatedAt;
    }

    Order toDomain() {
        return Order.restore(id, userId, eventId, lines.stream().map(OrderLineJpaEntity::toDomain).toList(),
                status, failReason, createdAt, updatedAt);
    }

    public Long getId() {
        return id;
    }
}
