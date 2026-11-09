package com.flashsale.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flashsale.order.application.port.out.CartRepository;
import com.flashsale.order.application.port.out.CatalogProduct;
import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.application.port.out.StockLine;
import com.flashsale.order.domain.Cart;
import com.flashsale.order.domain.CartItem;
import com.flashsale.order.domain.FailReason;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import com.flashsale.order.domain.OrderStatus;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CheckoutServiceTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final long USER = 5;
    static final CatalogProduct A = new CatalogProduct(1, "A", 1000);
    static final CatalogProduct B = new CatalogProduct(2, "B", 500);
    static final List<StockLine> LINES = List.of(new StockLine(1, 2), new StockLine(2, 1));

    CartRepository carts = mock(CartRepository.class);
    ProductPort productPort = mock(ProductPort.class);
    InventoryPort inventoryPort = mock(InventoryPort.class);
    PaymentPort paymentPort = mock(PaymentPort.class);
    OrderWriter orderWriter = mock(OrderWriter.class);
    CheckoutService service = new CheckoutService(carts, productPort, inventoryPort, paymentPort, orderWriter,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void stubs() {
        when(carts.findByUserId(USER)).thenReturn(cart(new CartItem(1, 2), new CartItem(2, 1)));
        when(productPort.findListed(any())).thenReturn(List.of(A, B));
        when(orderWriter.createPending(any(Order.class))).thenReturn(Order.restore(99, USER, null,
                List.of(new OrderLine(1, "A", 1000, 2), new OrderLine(2, "B", 500, 1)),
                OrderStatus.PENDING, null, NOW, NOW));
        when(inventoryPort.decreaseAll(anyList())).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.APPROVED);
    }

    private static Cart cart(CartItem... items) {
        return Cart.restore(USER, List.of(items));
    }

    @Test
    void paidCopiesPricesAndChargesTotal() {
        OrderResult result = service.checkout(USER);

        ArgumentCaptor<Order> pending = ArgumentCaptor.forClass(Order.class);
        verify(orderWriter).createPending(pending.capture());
        assertThat(pending.getValue().eventId()).isNull();
        assertThat(pending.getValue().lines())
                .containsExactly(new OrderLine(1, "A", 1000, 2), new OrderLine(2, "B", 500, 1));
        verify(inventoryPort).decreaseAll(LINES);
        verify(paymentPort).pay(99, USER, 2500);
        verify(orderWriter).markPaid(99);
        assertThat(result).isEqualTo(new OrderResult(99, OrderStatus.PAID, null));
    }

    @Test
    void paidRemovesOnlyOrderedLines() {
        Cart afterPayment = cart(new CartItem(1, 2), new CartItem(2, 1), new CartItem(3, 4));
        when(carts.findByUserId(USER)).thenReturn(cart(new CartItem(1, 2), new CartItem(2, 1)), afterPayment);

        service.checkout(USER);

        ArgumentCaptor<Cart> saved = ArgumentCaptor.forClass(Cart.class);
        verify(carts).save(saved.capture());
        assertThat(saved.getValue().items()).containsExactly(new CartItem(3, 4));
    }

    @Test
    void cartSaveFailureAfterPaidStillReturnsPaid() {
        org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(carts).save(any());

        assertThat(service.checkout(USER).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void declinedRestoresAllAndKeepsCart() {
        when(paymentPort.pay(99, USER, 2500)).thenReturn(PaymentOutcome.DECLINED);

        OrderResult result = service.checkout(USER);

        verify(inventoryPort, times(1)).increaseAll(LINES);
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_DECLINED);
        verify(carts, never()).save(any());
        assertThat(result).isEqualTo(new OrderResult(99, OrderStatus.FAILED, FailReason.PAYMENT_DECLINED));
    }

    @Test
    void declinedStillFailsWhenRestoreFails() {
        when(paymentPort.pay(99, USER, 2500)).thenReturn(PaymentOutcome.DECLINED);
        org.mockito.Mockito.doThrow(new DownstreamException("down")).when(inventoryPort).increaseAll(anyList());

        assertThat(service.checkout(USER).reason()).isEqualTo(FailReason.PAYMENT_DECLINED);
        verify(orderWriter).markFailed(99, FailReason.PAYMENT_DECLINED);
    }

    @Test
    void soldOutDoesNotPayOrRestore() {
        when(inventoryPort.decreaseAll(anyList())).thenReturn(StockDecreaseResult.OUT_OF_STOCK);

        OrderResult result = service.checkout(USER);

        verifyNoInteractions(paymentPort);
        verify(inventoryPort, never()).increaseAll(anyList());
        assertThat(result.reason()).isEqualTo(FailReason.SOLD_OUT);
        verify(orderWriter).markFailed(99, FailReason.SOLD_OUT);
    }

    @Test
    void conflictFailsWithoutPayOrRestore() {
        when(inventoryPort.decreaseAll(anyList())).thenReturn(StockDecreaseResult.CONFLICT);

        assertThat(service.checkout(USER).reason()).isEqualTo(FailReason.STOCK_CONFLICT);
        verifyNoInteractions(paymentPort);
        verify(inventoryPort, never()).increaseAll(anyList());
    }

    @Test
    void inventoryUnknownDoesNotRestore() {
        when(inventoryPort.decreaseAll(anyList())).thenThrow(new DownstreamException("slow", new java.net.SocketTimeoutException("t")));

        OrderResult result = service.checkout(USER);

        assertThat(result.reason()).isEqualTo(FailReason.INVENTORY_TIMEOUT);
        verify(inventoryPort, never()).increaseAll(anyList());
        verifyNoInteractions(paymentPort);
        verify(orderWriter).markFailed(99, FailReason.INVENTORY_TIMEOUT);
    }

    @Test
    void paymentUnknownDoesNotRestore() {
        when(paymentPort.pay(99, USER, 2500)).thenThrow(new DownstreamException("boom"));

        OrderResult result = service.checkout(USER);

        assertThat(result.reason()).isEqualTo(FailReason.PAYMENT_ERROR);
        verify(inventoryPort, never()).increaseAll(anyList());
        verify(carts, never()).save(any());
    }

    @Test
    void paymentTimeoutIsReported() {
        when(paymentPort.pay(99, USER, 2500)).thenThrow(new DownstreamException("slow", new java.net.SocketTimeoutException("t")));

        assertThat(service.checkout(USER).reason()).isEqualTo(FailReason.PAYMENT_TIMEOUT);
    }

    @Test
    void emptyCartCreatesNoOrder() {
        when(carts.findByUserId(USER)).thenReturn(Cart.empty(USER));

        assertThatThrownBy(() -> service.checkout(USER)).isInstanceOf(CartEmptyException.class);
        verifyNoInteractions(orderWriter, productPort, inventoryPort, paymentPort);
    }

    @Test
    void unlistedProductCreatesNoOrderAndNoStockCall() {
        when(productPort.findListed(any())).thenReturn(List.of(A));

        assertThatThrownBy(() -> service.checkout(USER)).isInstanceOf(ProductUnavailableException.class);
        verifyNoInteractions(orderWriter, inventoryPort, paymentPort);
    }

    @Test
    void productCallFailureIsPreOrderFailure() {
        when(productPort.findListed(any())).thenThrow(new DownstreamException("slow", new java.net.SocketTimeoutException("t")));

        assertThatThrownBy(() -> service.checkout(USER)).isInstanceOfSatisfying(PreOrderFailedException.class,
                e -> assertThat(e.reason()).isEqualTo(FailReason.PRODUCT_TIMEOUT));
        verifyNoInteractions(orderWriter, inventoryPort, paymentPort);
    }

    @Test
    void connectionTimeoutOnCreateIsPreOrderFailure() {
        when(orderWriter.createPending(any(Order.class)))
                .thenThrow(new RuntimeException(new SQLTransientConnectionException("pool")));

        assertThatThrownBy(() -> service.checkout(USER)).isInstanceOfSatisfying(PreOrderFailedException.class,
                e -> assertThat(e.reason()).isEqualTo(FailReason.ORDER_DB_TIMEOUT));
        verifyNoInteractions(inventoryPort, paymentPort);
    }
}
