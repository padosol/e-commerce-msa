package com.flashsale.order.api;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(@NotNull Long eventId) {
}
