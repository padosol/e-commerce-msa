package com.flashsale.payment.api;

import com.flashsale.payment.simulation.SimulationConfig;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConfigRequest(
    @NotNull @Min(0) Long delayMs,
    @NotNull @Min(0) Long jitterMs,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double failureRate) {

    SimulationConfig toConfig() {
        return new SimulationConfig(delayMs, jitterMs, failureRate);
    }
}
