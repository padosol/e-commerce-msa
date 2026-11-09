package com.flashsale.order.application;

import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderNotFoundException;
import org.springframework.stereotype.Service;

/** Reads a user's own order history. */
@Service
public class OrderQueryService {

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    public OrderPage myOrders(long userId, int page, int size) {
        return new OrderPage(orders.findByUserId(userId, page, size), page, size, orders.countByUserId(userId));
    }

    /** An order that does not exist and one that belongs to someone else look the same. */
    public Order myOrder(long userId, long orderId) {
        return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
