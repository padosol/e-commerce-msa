package com.flashsale.product.api;

import com.flashsale.product.domain.FlashSaleEvent;
import java.time.Instant;

public record EventResponse(
    long eventId, long productId, String productName, long price, Instant openAt, int saleQuantity) {

    public static EventResponse from(FlashSaleEvent e) {
        return new EventResponse(e.getId(), e.getProduct().getId(), e.getProduct().getName(),
            e.getProduct().getPrice(), e.getOpenAt(), e.getSaleQuantity());
    }
}
