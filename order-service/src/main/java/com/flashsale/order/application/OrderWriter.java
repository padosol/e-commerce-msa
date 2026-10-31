package com.flashsale.order.application;

import com.flashsale.order.client.EventInfo;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Each method is its own short transaction; never wrap remote calls around these. */
@Component
public class OrderWriter {

    private final OrderRepository repository;
    private final Clock clock;

    public OrderWriter(OrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Order createPending(long userId, EventInfo event) {
        return repository.save(new Order(userId, event.eventId(), event.productId(), 1, event.price(), clock.instant()));
    }

    @Transactional
    public void markPaid(long orderId) {
        repository.findById(orderId).orElseThrow().markPaid(clock.instant());
    }

    @Transactional
    public void markFailed(long orderId, FailReason reason) {
        repository.findById(orderId).orElseThrow().markFailed(reason, clock.instant());
    }
}
