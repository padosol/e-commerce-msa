package com.flashsale.product.adapter.in.web;

import com.flashsale.product.domain.FlashSaleEvent;
import java.time.Instant;

public record EventResponse(
    long eventId, long productId, String productName, long price, Instant openAt, int saleQuantity) {

    public static EventResponse from(FlashSaleEvent e) {
        return new EventResponse(e.id(), e.product().id(), e.product().name(),
            e.product().price(), e.openAt(), e.saleQuantity());
    }
}
