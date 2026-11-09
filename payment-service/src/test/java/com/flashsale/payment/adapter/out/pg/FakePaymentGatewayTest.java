package com.flashsale.payment.adapter.out.pg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FakePaymentGatewayTest {

    private FakePaymentGateway simulator(long delay, long jitter, double failureRate) {
        return new FakePaymentGateway(new FakePgConfig(delay, jitter, failureRate));
    }

    @Test
    void delayStaysWithinJitterBounds() {
        FakePaymentGateway simulator = simulator(200, 50, 0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.nextDelay())
                .isBetween(Duration.ofMillis(150), Duration.ofMillis(250));
        }
    }

    @Test
    void delayNeverNegative() {
        FakePaymentGateway simulator = simulator(10, 50, 0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.nextDelay()).isGreaterThanOrEqualTo(Duration.ZERO);
        }
    }

    @Test
    void zeroFailureRateNeverDeclines() {
        FakePaymentGateway simulator = simulator(0, 0, 0.0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.shouldDecline()).isFalse();
        }
    }

    @Test
    void fullFailureRateAlwaysDeclines() {
        FakePaymentGateway simulator = simulator(0, 0, 1.0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.shouldDecline()).isTrue();
        }
    }

    @Test
    void updateReplacesConfig() {
        FakePaymentGateway simulator = simulator(0, 0, 0.0);
        simulator.update(new FakePgConfig(5, 1, 0.5));
        assertThat(simulator.current()).isEqualTo(new FakePgConfig(5, 1, 0.5));
    }
}
