package com.flashsale.order.application.port.out;

import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrderRepository {

    /**
     * Without an id: inserts the order and its lines and returns it with the id filled in.
     * With an id: updates only status, fail reason and updated-at. Lines are never rewritten.
     */
    Order save(Order order);

    Optional<Order> findById(long orderId);

    /** One page of a user's orders with their lines, newest (highest id) first, read with a fixed number of queries. */
    List<Order> findByUserId(long userId, int page, int size);

    long countByUserId(long userId);

    /** Empty when the order does not exist or belongs to another user. */
    Optional<Order> findByIdAndUserId(long orderId, long userId);

    /** Order counts per status for one event; statuses with no orders are absent. */
    Map<OrderStatus, Long> countByStatus(long eventId);

    /** FAILED order counts per reason for one event; reasons with no orders are absent. */
    Map<FailReason, Long> countFailedByReason(long eventId);

    /** Removes every order and, with it, every order line. */
    void deleteAll();
}
