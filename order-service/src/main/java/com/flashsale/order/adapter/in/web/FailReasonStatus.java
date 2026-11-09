package com.flashsale.order.adapter.in.web;

import com.flashsale.order.domain.FailReason;
import org.springframework.http.HttpStatus;

final class FailReasonStatus {

    private FailReasonStatus() {
    }

    static HttpStatus of(FailReason reason) {
        return switch (reason) {
            case SOLD_OUT, STOCK_CONFLICT -> HttpStatus.CONFLICT;
            case PAYMENT_DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case INVENTORY_TIMEOUT, PAYMENT_TIMEOUT, PRODUCT_TIMEOUT, ORDER_DB_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INVENTORY_ERROR, PAYMENT_ERROR, PRODUCT_ERROR -> HttpStatus.BAD_GATEWAY;
        };
    }
}
