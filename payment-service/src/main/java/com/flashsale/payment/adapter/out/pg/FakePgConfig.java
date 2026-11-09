package com.flashsale.payment.adapter.out.pg;

public record FakePgConfig(long delayMs, long jitterMs, double failureRate) {
}
