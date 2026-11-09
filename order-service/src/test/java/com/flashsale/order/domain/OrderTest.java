package com.flashsale.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final Instant LATER = Instant.parse("2026-11-01T10:00:05Z");

    private static Order pending() {
        return Order.pending(1, 3L, List.of(new OrderLine(7, "A", 1000, 1)), NOW);
    }

    @Test
    void amountIsSumOfLineSubtotals() {
        Order order = Order.pending(1, null,
                List.of(new OrderLine(7, "A", 1000, 2), new OrderLine(8, "B", 500, 1)), NOW);

        assertThat(order.amount()).isEqualTo(2500);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.id()).isNull();
    }

    @Test
    void pendingRequiresAtLeastOneLine() {
        assertThatThrownBy(() -> Order.pending(1, null, List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markPaidOnlyFromPending() {
        Order order = pending();
        order.markPaid(LATER);

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.updatedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> order.markPaid(LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> order.markFailed(FailReason.SOLD_OUT, LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markFailedKeepsReason() {
        Order order = pending();
        order.markFailed(FailReason.SOLD_OUT, LATER);

        assertThat(order.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.failReason()).isEqualTo(FailReason.SOLD_OUT);
        assertThatThrownBy(() -> order.markPaid(LATER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linesCannotBeChanged() {
        assertThatThrownBy(() -> pending().lines().add(new OrderLine(9, "C", 1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void lineRejectsZeroQuantityAndNegativePrice() {
        assertThat(new OrderLine(7, "A", 0, 3).subtotal()).isZero();
        assertThatThrownBy(() -> new OrderLine(7, "A", 1000, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(7, "A", -1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
