package com.flashsale.payment.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaymentSimulatorTest {

    private PaymentSimulator simulator(long delay, long jitter, double failureRate) {
        return new PaymentSimulator(new SimulationConfig(delay, jitter, failureRate));
    }

    @Test
    void delayStaysWithinJitterBounds() {
        PaymentSimulator simulator = simulator(200, 50, 0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.nextDelay())
                .isBetween(Duration.ofMillis(150), Duration.ofMillis(250));
        }
    }

    @Test
    void delayNeverNegative() {
        PaymentSimulator simulator = simulator(10, 50, 0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.nextDelay()).isGreaterThanOrEqualTo(Duration.ZERO);
        }
    }

    @Test
    void zeroFailureRateNeverDeclines() {
        PaymentSimulator simulator = simulator(0, 0, 0.0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.shouldDecline()).isFalse();
        }
    }

    @Test
    void fullFailureRateAlwaysDeclines() {
        PaymentSimulator simulator = simulator(0, 0, 1.0);
        for (int i = 0; i < 1000; i++) {
            assertThat(simulator.shouldDecline()).isTrue();
        }
    }

    @Test
    void updateReplacesConfig() {
        PaymentSimulator simulator = simulator(0, 0, 0.0);
        simulator.update(new SimulationConfig(5, 1, 0.5));
        assertThat(simulator.current()).isEqualTo(new SimulationConfig(5, 1, 0.5));
    }
}
