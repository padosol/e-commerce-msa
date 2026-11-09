package com.flashsale.order.application.port.out;

import java.time.Instant;

public record EventInfo(long eventId, long productId, String productName, long price, Instant openAt,
                        int saleQuantity) {
}
