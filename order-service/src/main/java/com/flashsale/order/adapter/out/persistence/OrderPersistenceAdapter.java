package com.flashsale.order.adapter.out.persistence;

import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OrderPersistenceAdapter implements OrderRepository {

    private final OrderJpaRepository jpa;

    OrderPersistenceAdapter(OrderJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * An existing order is loaded as a managed entity and only its status fields are set, so the flush is one
     * UPDATE of status, fail_reason and updated_at. Merging a detached copy instead would cascade into the lines.
     * Inside OrderWriter's transaction the entity is already in the persistence context, so no extra SELECT runs.
     */
    @Override
    @Transactional
    public Order save(Order order) {
        if (order.id() == null) {
            return jpa.save(OrderJpaEntity.from(order)).toDomain();
        }
        OrderJpaEntity entity = jpa.findById(order.id())
                .orElseThrow(() -> new IllegalStateException("order not found: " + order.id()));
        entity.changeStatus(order.status(), order.failReason(), order.updatedAt());
        return order;
    }

    @Override
    public Optional<Order> findById(long orderId) {
        return jpa.findWithLinesById(orderId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Map<OrderStatus, Long> countByStatus(long eventId) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (var c : jpa.countByStatus(eventId)) {
            counts.put(c.getStatus(), c.getCnt());
        }
        return counts;
    }

    @Override
    public Map<FailReason, Long> countFailedByReason(long eventId) {
        Map<FailReason, Long> counts = new EnumMap<>(FailReason.class);
        for (var c : jpa.countByFailReason(eventId)) {
            if (c.getReason() != null) {
                counts.put(c.getReason(), c.getCnt());
            }
        }
        return counts;
    }

    /** One DELETE on orders; order_items rows go with it through the ON DELETE CASCADE foreign key. */
    @Override
    public void deleteAll() {
        jpa.deleteAllInBatch();
    }
}
