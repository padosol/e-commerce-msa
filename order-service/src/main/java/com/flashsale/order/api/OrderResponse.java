package com.flashsale.order.api;

import com.flashsale.order.application.OrderResult;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.OrderStatus;

public record OrderResponse(long orderId, OrderStatus status, FailReason reason) {

    static OrderResponse from(OrderResult r) {
        return new OrderResponse(r.orderId(), r.status(), r.reason());
    }
}
