package com.flashsale.order.adapter.in.web;

import com.flashsale.order.application.OrderResult;
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

    /** The HTTP status of a finished order: shared by the event order and the cart checkout. */
    static HttpStatus of(OrderResult r) {
        return switch (r.status()) {
            case PAID -> HttpStatus.CREATED;
            case FAILED -> of(r.reason());
            case PENDING -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
