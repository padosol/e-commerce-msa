package com.flashsale.payment.simulation;

public record SimulationConfig(long delayMs, long jitterMs, double failureRate) {
}
