package com.flashsale.payment.adapter.in.web;

import jakarta.validation.constraints.NotNull;

public record PayRequest(@NotNull Long orderId, @NotNull Long userId, @NotNull Long amount) {
}
