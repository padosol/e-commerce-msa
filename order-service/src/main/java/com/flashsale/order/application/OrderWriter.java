package com.flashsale.order.application;

import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
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
    public Order createPending(Order pending) {
        return repository.save(pending);
    }

    @Transactional
    public void markPaid(long orderId) {
        Order order = repository.findById(orderId).orElseThrow();
        order.markPaid(clock.instant());
        repository.save(order);
    }

    @Transactional
    public void markFailed(long orderId, FailReason reason) {
        Order order = repository.findById(orderId).orElseThrow();
        order.markFailed(reason, clock.instant());
        repository.save(order);
    }
}
