package com.flashsale.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.EventInfo;
import com.flashsale.order.application.port.out.EventNotFoundException;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import com.flashsale.order.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.sql.SQLTransientConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderServiceTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final long USER = 5;
    static final long EVENT = 1;

    ProductPort productPort = mock(ProductPort.class);
    InventoryPort inventoryPort = mock(InventoryPort.class);
    PaymentPort paymentPort = mock(PaymentPort.class);
    OrderWriter orderWriter = mock(OrderWriter.class);
    OrderRepository orderRepository = mock(OrderRepository.class);
    PreOrderFailures preOrderFailures = new PreOrderFailures();
    OrderService service = new OrderService(productPort, inventoryPort, paymentPort, orderWriter,
            orderRepository, preOrderFailures, Clock.fixed(NOW, ZoneOffset.UTC));

    EventInfo event = new EventInfo(EVENT, 7, "Sneakers", 10000, Instant.parse("2026-11-01T09:59:00Z"), 100);

    @BeforeEach
    void stubs() {
        when(productPort.getEvent(EVENT)).thenReturn(event);
        Order order = Order.restore(99, USER, EVENT, List.of(new OrderLine(7, "Sneakers", 10000, 1)),
                OrderStatus.PENDING, null, NOW, NOW);
        when(orderWriter.createPending(any(Order.class))).thenReturn(order);
    }

    @Test
    void createsPendingOrderWithOneEventLine() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(99, USER, 10000)).thenReturn(PaymentOutcome.APPROVED);

        service.placeOrder(USER, EVENT);

        ArgumentCaptor<Order> pending = ArgumentCaptor.forClass(Order.class);
        verify(orderWriter).createPending(pending.capture());
        Order o = pending.getValue();
        assertThat(o.id()).isNull();
        assertThat(o.userId()).isEqualTo(USER);
        assertThat(o.eventId()).isEqualTo(EVENT);
        assertThat(o.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.lines()).containsExactly(new OrderLine(7, "Sneakers", 10000, 1));
        assertThat(o.createdAt()).isEqualTo(NOW);
    }

    @Test
    void paidWhenStockAndPaymentSucceed() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(99, USER, 10000)).thenReturn(PaymentOutcome.APPROVED);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.PAID, null));
        verify(orderWriter).markPaid(99);
        verify(inventoryPort, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void soldOutSkipsPayment() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.OUT_OF_STOCK);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.SOLD_OUT));
        verify(orderWriter).markFailed(99, FailReason.SOLD_OUT);
        verifyNoInteractions(paymentPort);
    }

    @Test
    void declinedPaymentRestoresStock() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(99, USER, 10000)).thenReturn(PaymentOutcome.DECLINED);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_DECLINED));
        verify(inventoryPort, times(1)).increase(7, 1);
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_DECLINED);
    }

    @Test
    void restoreFailureStillReportsDeclined() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(99, USER, 10000)).thenReturn(PaymentOutcome.DECLINED);
        doThrow(new DownstreamException("boom")).when(inventoryPort).increase(7, 1);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_DECLINED));
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_DECLINED);
    }

    @Test
    void inventoryErrorDoesNotRestoreOrPay() {
        when(inventoryPort.decrease(7, 1)).thenThrow(new DownstreamException("down"));

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.INVENTORY_ERROR));
        verify(inventoryPort, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verifyNoInteractions(paymentPort);
    }

    @Test
    void paymentErrorDoesNotRestoreStock() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(99, USER, 10000)).thenThrow(new DownstreamException("down"));

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_ERROR));
        verify(inventoryPort, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_ERROR);
    }

    @Test
    void notOpenRejectsWithoutOrder() {
        when(productPort.getEvent(EVENT))
                .thenReturn(new EventInfo(EVENT, 7, "Sneakers", 10000, Instant.parse("2026-11-01T10:00:01Z"), 100));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT)).isInstanceOf(EventNotOpenException.class);

        org.mockito.Mockito.verifyNoInteractions(orderWriter, inventoryPort);
    }

    @Test
    void eventNotFoundPropagates() {
        when(productPort.getEvent(EVENT)).thenThrow(new EventNotFoundException(EVENT));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT)).isInstanceOf(EventNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(orderWriter);
    }

    private static DownstreamException timeout() {
        return new DownstreamException("t", new java.net.http.HttpTimeoutException("t"));
    }

    @Test
    void conflictMarksStockConflict() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.CONFLICT);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.STOCK_CONFLICT));
        verifyNoInteractions(paymentPort);
    }

    @Test
    void inventoryTimeoutMarksInventoryTimeout() {
        when(inventoryPort.decrease(7, 1)).thenThrow(timeout());

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.INVENTORY_TIMEOUT));
        verify(inventoryPort, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verifyNoInteractions(paymentPort);
    }

    @Test
    void paymentTimeoutMarksPaymentTimeout() {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(99, USER, 10000)).thenThrow(timeout());

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_TIMEOUT));
        verify(inventoryPort, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void productTimeoutCountsPreOrderFailureWithoutOrder() {
        when(productPort.getEvent(EVENT)).thenThrow(timeout());

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT))
                .isInstanceOfSatisfying(PreOrderFailedException.class,
                        e -> assertThat(e.reason()).isEqualTo(FailReason.PRODUCT_TIMEOUT));

        verifyNoInteractions(orderWriter, inventoryPort);
        assertThat(preOrderFailures.snapshot(EVENT)).isEqualTo(Map.of(FailReason.PRODUCT_TIMEOUT, 1L));
    }

    @Test
    void productErrorCountsAsProductError() {
        when(productPort.getEvent(EVENT)).thenThrow(new DownstreamException("500"));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT))
                .isInstanceOfSatisfying(PreOrderFailedException.class,
                        e -> assertThat(e.reason()).isEqualTo(FailReason.PRODUCT_ERROR));

        assertThat(preOrderFailures.snapshot(EVENT)).isEqualTo(Map.of(FailReason.PRODUCT_ERROR, 1L));
    }

    @Test
    void orderDbTimeoutCountsPreOrderFailure() {
        when(orderWriter.createPending(any(Order.class))).thenThrow(new CannotCreateTransactionException("x",
                new SQLTransientConnectionException("Connection is not available")));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT))
                .isInstanceOfSatisfying(PreOrderFailedException.class,
                        e -> assertThat(e.reason()).isEqualTo(FailReason.ORDER_DB_TIMEOUT));

        verifyNoInteractions(inventoryPort);
        assertThat(preOrderFailures.snapshot(EVENT)).isEqualTo(Map.of(FailReason.ORDER_DB_TIMEOUT, 1L));
    }

    @Test
    void otherCreatePendingFailurePropagates() {
        when(orderWriter.createPending(any(Order.class))).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT)).isInstanceOf(IllegalStateException.class);

        assertThat(preOrderFailures.snapshot(EVENT)).isEmpty();
    }
}
