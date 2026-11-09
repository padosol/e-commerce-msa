package com.flashsale.order.adapter.in.web;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(@NotNull Long eventId) {
}
