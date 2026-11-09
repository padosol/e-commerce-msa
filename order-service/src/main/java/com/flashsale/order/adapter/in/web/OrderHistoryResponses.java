package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.OrderPage;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import com.flashsale.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

public final class OrderHistoryResponses {

    private OrderHistoryResponses() {
    }

    public record Summary(long orderId, OrderStatus status, FailReason reason, long amount, Instant createdAt,
                          int itemCount, String firstProductName) {

        static Summary from(Order order) {
            return new Summary(order.id(), order.status(), order.failReason(), order.amount(), order.createdAt(),
                    order.lines().size(), order.lines().get(0).productName());
        }
    }

    public record Page(List<Summary> orders, int page, int size, long total) {

        static Page from(OrderPage page) {
            return new Page(page.orders().stream().map(Summary::from).toList(), page.page(), page.size(),
                    page.total());
        }
    }

    public record Item(long productId, String productName, long unitPrice, int quantity, long subtotal) {

        static Item from(OrderLine line) {
            return new Item(line.productId(), line.productName(), line.unitPrice(), line.quantity(),
                    line.subtotal());
        }
    }

    public record Detail(long orderId, OrderStatus status, FailReason reason, long amount, Instant createdAt,
                         Long eventId, List<Item> items) {

        static Detail from(Order order) {
            return new Detail(order.id(), order.status(), order.failReason(), order.amount(), order.createdAt(),
                    order.eventId(), order.lines().stream().map(Item::from).toList());
        }
    }
}
