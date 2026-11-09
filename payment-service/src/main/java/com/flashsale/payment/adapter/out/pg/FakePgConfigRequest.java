package com.flashsale.payment.adapter.out.pg;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FakePgConfigRequest(
    @NotNull @Min(0) Long delayMs,
    @NotNull @Min(0) Long jitterMs,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double failureRate) {

    FakePgConfig toConfig() {
        return new FakePgConfig(delayMs, jitterMs, failureRate);
    }
}
