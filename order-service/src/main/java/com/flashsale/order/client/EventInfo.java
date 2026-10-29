package com.flashsale.order.client;

import java.time.Instant;

public record EventInfo(long eventId, long productId, long price, Instant openAt, int saleQuantity) {
}
