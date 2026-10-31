package com.flashsale.payment.simulation;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentSimulator {

    private final AtomicReference<SimulationConfig> config;

    @Autowired
    public PaymentSimulator(
        @Value("${payment.delay-ms}") long delayMs,
        @Value("${payment.jitter-ms}") long jitterMs,
        @Value("${payment.failure-rate}") double failureRate) {
        this(new SimulationConfig(delayMs, jitterMs, failureRate));
    }

    public PaymentSimulator(SimulationConfig initial) {
        this.config = new AtomicReference<>(initial);
    }

    public SimulationConfig current() {
        return config.get();
    }

    public void update(SimulationConfig c) {
        config.set(c);
    }

    public Duration nextDelay() {
        SimulationConfig c = config.get();
        long jitter = c.jitterMs() == 0 ? 0
            : ThreadLocalRandom.current().nextLong(-c.jitterMs(), c.jitterMs() + 1);
        return Duration.ofMillis(Math.max(0, c.delayMs() + jitter));
    }

    public boolean shouldDecline() {
        return ThreadLocalRandom.current().nextDouble() < config.get().failureRate();
    }
}
