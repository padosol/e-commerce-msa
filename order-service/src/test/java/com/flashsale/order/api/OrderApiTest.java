package com.flashsale.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.order.TestcontainersConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderRepository repository;

    @MockitoBean
    ProductClient productClient;

    @MockitoBean
    InventoryClient inventoryClient;

    @MockitoBean
    PaymentClient paymentClient;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(clock.instant()).thenReturn(NOW);
        when(productClient.getEvent(1)).thenReturn(event(Instant.parse("2026-11-01T09:59:00Z")));
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentClient.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.APPROVED);
    }

    private static EventInfo event(Instant openAt) {
        return new EventInfo(1, 7, 10000, openAt, 100);
    }

    private ResultActions order() throws Exception {
        return mvc.perform(post("/api/orders")
                .header("X-User-Id", "5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":1}"));
    }

    @Test
    void paidReturns201() throws Exception {
        order().andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PAID"));

        List<Order> orders = repository.findAll();
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(orders.get(0).getQuantity()).isEqualTo(1);
        assertThat(orders.get(0).getAmount()).isEqualTo(10000);
    }

    @Test
    void soldOutReturns409WithReason() throws Exception {
        when(inventoryClient.decrease(7, 1)).thenReturn(StockDecreaseResult.OUT_OF_STOCK);
        order().andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("SOLD_OUT"));
    }

    @Test
    void declinedReturns402() throws Exception {
        when(paymentClient.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.DECLINED);
        order().andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.reason").value("PAYMENT_DECLINED"));
    }

    @Test
    void downstreamErrorReturns502() throws Exception {
        when(inventoryClient.decrease(7, 1)).thenThrow(new DownstreamException("down"));
        order().andExpect(status().isBadGateway()).andExpect(jsonPath("$.reason").value("INVENTORY_ERROR"));
    }

    @Test
    void notOpenReturns400() throws Exception {
        when(productClient.getEvent(1)).thenReturn(event(Instant.parse("2026-11-01T10:00:01Z")));
        order().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void unknownEventReturns404() throws Exception {
        when(productClient.getEvent(1)).thenThrow(new EventNotFoundException(1));
        order().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void missingUserIdHeaderReturns401() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void remoteCallsRunOutsideTransactions() throws Exception {
        List<Boolean> active = new ArrayList<>();
        when(productClient.getEvent(1)).thenAnswer(inv -> {
            active.add(TransactionSynchronizationManager.isActualTransactionActive());
            return event(Instant.parse("2026-11-01T09:59:00Z"));
        });
        when(inventoryClient.decrease(anyLong(), anyInt())).thenAnswer(inv -> {
            active.add(TransactionSynchronizationManager.isActualTransactionActive());
            return StockDecreaseResult.SUCCESS;
        });
        when(paymentClient.pay(anyLong(), anyLong(), anyLong())).thenAnswer(inv -> {
            active.add(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentOutcome.APPROVED;
        });

        order().andExpect(status().isCreated());

        assertThat(active).hasSize(3).containsOnly(false);
    }

    private void save(long eventId, OrderStatus status, FailReason reason) {
        Order o = new Order(5, eventId, 7, 1, 10000, NOW);
        if (status == OrderStatus.PAID) {
            o.markPaid(NOW);
        } else if (status == OrderStatus.FAILED) {
            o.markFailed(reason, NOW);
        }
        repository.save(o);
    }

    @Test
    void statsCountOnlyRequestedEvent() throws Exception {
        save(1, OrderStatus.PAID, null);
        save(1, OrderStatus.PAID, null);
        save(1, OrderStatus.FAILED, FailReason.SOLD_OUT);
        save(2, OrderStatus.PAID, null);

        mvc.perform(get("/internal/orders/stats").param("eventId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.paid").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.failedByReason.SOLD_OUT").value(1))
                .andExpect(jsonPath("$.failedByReason.length()").value(1));
    }

    @Test
    void deleteClearsOrders() throws Exception {
        save(1, OrderStatus.PAID, null);
        mvc.perform(delete("/internal/orders")).andExpect(status().isNoContent());
        assertThat(repository.count()).isZero();
    }
}
