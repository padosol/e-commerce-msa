package com.flashsale.order.application;

import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.OrderStatus;

public record OrderResult(long orderId, OrderStatus status, FailReason reason) {
}
