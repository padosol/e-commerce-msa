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

import com.flashsale.order.client.DownstreamException;
import com.flashsale.order.client.EventInfo;
import com.flashsale.order.client.EventNotFoundException;
import com.flashsale.order.client.InventoryClient;
import com.flashsale.order.client.PaymentClient;
import com.flashsale.order.client.PaymentOutcome;
import com.flashsale.order.client.ProductClient;
import com.flashsale.order.client.StockDecreaseResult;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderRepository;
import com.flashsale.order.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderServiceTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final long USER = 5;
    static final long EVENT = 1;

    ProductClient productClient = mock(ProductClient.class);
    InventoryClient inventoryClient = mock(InventoryClient.class);
    PaymentClient paymentClient = mock(PaymentClient.class);
    OrderWriter orderWriter = mock(OrderWriter.class);
    OrderRepository orderRepository = mock(OrderRepository.class);
    OrderService service = new OrderService(productClient, inventoryClient, paymentClient, orderWriter,
            orderRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    EventInfo event = new EventInfo(EVENT, 7, 10000, Instant.parse("2026-11-01T09:59:00Z"), 100);

    @BeforeEach
    void stubs() {
        when(productClient.getEvent(EVENT)).thenReturn(event);
        Order order = new Order(USER, EVENT, 7, 1, 10000, NOW);
        ReflectionTestUtils.setField(order, "id", 99L);
        when(orderWriter.createPending(USER, event)).thenReturn(order);
    }

    @Test
    void paidWhenStockAndPaymentSucceed() {
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentClient.pay(99, USER, 10000)).thenReturn(PaymentOutcome.APPROVED);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.PAID, null));
        verify(orderWriter).markPaid(99);
        verify(inventoryClient, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void soldOutSkipsPayment() {
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.OUT_OF_STOCK);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.SOLD_OUT));
        verify(orderWriter).markFailed(99, FailReason.SOLD_OUT);
        verifyNoInteractions(paymentClient);
    }

    @Test
    void declinedPaymentRestoresStock() {
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentClient.pay(99, USER, 10000)).thenReturn(PaymentOutcome.DECLINED);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_DECLINED));
        verify(inventoryClient, times(1)).increase(7, 1);
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_DECLINED);
    }

    @Test
    void restoreFailureStillReportsDeclined() {
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentClient.pay(99, USER, 10000)).thenReturn(PaymentOutcome.DECLINED);
        doThrow(new DownstreamException("boom")).when(inventoryClient).increase(7, 1);

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_DECLINED));
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_DECLINED);
    }

    @Test
    void inventoryErrorDoesNotRestoreOrPay() {
        when(inventoryClient.decrease(7, 1)).thenThrow(new DownstreamException("down"));

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.INVENTORY_ERROR));
        verify(inventoryClient, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verifyNoInteractions(paymentClient);
    }

    @Test
    void paymentErrorDoesNotRestoreStock() {
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentClient.pay(99, USER, 10000)).thenThrow(new DownstreamException("timeout"));

        OrderResult r = service.placeOrder(USER, EVENT);

        assertThat(r).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_ERROR));
        verify(inventoryClient, never()).increase(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_ERROR);
    }

    @Test
    void notOpenRejectsWithoutOrder() {
        when(productClient.getEvent(EVENT))
                .thenReturn(new EventInfo(EVENT, 7, 10000, Instant.parse("2026-11-01T10:00:01Z"), 100));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT)).isInstanceOf(EventNotOpenException.class);

        org.mockito.Mockito.verifyNoInteractions(orderWriter, inventoryClient);
    }

    @Test
    void eventNotFoundPropagates() {
        when(productClient.getEvent(EVENT)).thenThrow(new EventNotFoundException(EVENT));

        assertThatThrownBy(() -> service.placeOrder(USER, EVENT)).isInstanceOf(EventNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(orderWriter);
    }
}
