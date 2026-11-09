package com.flashsale.payment.adapter.out.pg;

import com.flashsale.payment.application.port.out.PaymentGateway;
import com.flashsale.payment.domain.PaymentStatus;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulated PG: waits a configurable delay, then approves or declines at a configurable rate. */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    private final AtomicReference<FakePgConfig> config;

    @Autowired
    public FakePaymentGateway(
        @Value("${payment.delay-ms}") long delayMs,
        @Value("${payment.jitter-ms}") long jitterMs,
        @Value("${payment.failure-rate}") double failureRate) {
        this(new FakePgConfig(delayMs, jitterMs, failureRate));
    }

    public FakePaymentGateway(FakePgConfig initial) {
        this.config = new AtomicReference<>(initial);
    }

    @Override
    public PaymentStatus pay(long orderId, long userId, long amount) {
        try {
            Thread.sleep(nextDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while simulating payment", e);
        }
        return shouldDecline() ? PaymentStatus.DECLINED : PaymentStatus.APPROVED;
    }

    public FakePgConfig current() {
        return config.get();
    }

    public void update(FakePgConfig c) {
        config.set(c);
    }

    public Duration nextDelay() {
        FakePgConfig c = config.get();
        long jitter = c.jitterMs() == 0 ? 0
            : ThreadLocalRandom.current().nextLong(-c.jitterMs(), c.jitterMs() + 1);
        return Duration.ofMillis(Math.max(0, c.delayMs() + jitter));
    }

    public boolean shouldDecline() {
        return ThreadLocalRandom.current().nextDouble() < config.get().failureRate();
    }
}
