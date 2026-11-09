package com.flashsale.order.adapter.in.web;

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
import com.flashsale.order.adapter.out.persistence.OrderJpaEntity;
import com.flashsale.order.adapter.out.persistence.OrderJpaRepository;
import com.flashsale.order.application.PreOrderFailures;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    OrderJpaRepository jpa;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ProductPort productPort;

    @MockitoBean
    InventoryPort inventoryPort;

    @MockitoBean
    PaymentPort paymentPort;

    @MockitoBean
    Clock clock;

    @Autowired
    PreOrderFailures preOrderFailures;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        preOrderFailures.reset();
        when(clock.instant()).thenReturn(NOW);
        when(productPort.getEvent(1)).thenReturn(event(Instant.parse("2026-11-01T09:59:00Z")));
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.APPROVED);
    }

    private static EventInfo event(Instant openAt) {
        return new EventInfo(1, 7, "Sneakers", 10000, openAt, 100);
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

        List<OrderJpaEntity> orders = jpa.findAll();
        assertThat(orders).hasSize(1);
        Order order = repository.findById(orders.get(0).getId()).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.eventId()).isEqualTo(1L);
        assertThat(order.lines()).containsExactly(new OrderLine(7, "Sneakers", 10000, 1));
        assertThat(order.amount()).isEqualTo(10000);
    }

    @Test
    void soldOutReturns409WithReason() throws Exception {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.OUT_OF_STOCK);
        order().andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("SOLD_OUT"));
    }

    @Test
    void declinedReturns402() throws Exception {
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.DECLINED);
        order().andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.reason").value("PAYMENT_DECLINED"));
    }

    @Test
    void downstreamErrorReturns502() throws Exception {
        when(inventoryPort.decrease(7, 1)).thenThrow(new DownstreamException("down"));
        order().andExpect(status().isBadGateway()).andExpect(jsonPath("$.reason").value("INVENTORY_ERROR"));
    }

    @Test
    void notOpenReturns400() throws Exception {
        when(productPort.getEvent(1)).thenReturn(event(Instant.parse("2026-11-01T10:00:01Z")));
        order().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
        assertThat(jpa.count()).isZero();
    }

    @Test
    void unknownEventReturns404() throws Exception {
        when(productPort.getEvent(1)).thenThrow(new EventNotFoundException(1));
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
        when(productPort.getEvent(1)).thenAnswer(inv -> {
            active.add(TransactionSynchronizationManager.isActualTransactionActive());
            return event(Instant.parse("2026-11-01T09:59:00Z"));
        });
        when(inventoryPort.decrease(anyLong(), anyInt())).thenAnswer(inv -> {
            active.add(TransactionSynchronizationManager.isActualTransactionActive());
            return StockDecreaseResult.SUCCESS;
        });
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenAnswer(inv -> {
            active.add(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentOutcome.APPROVED;
        });

        order().andExpect(status().isCreated());

        assertThat(active).hasSize(3).containsOnly(false);
    }

    private void save(long eventId, OrderStatus status, FailReason reason) {
        Order o = Order.pending(5, eventId, List.of(new OrderLine(7, "Sneakers", 10000, 1)), NOW);
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
        assertThat(jpa.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_items", Long.class)).isZero();
    }

    private static DownstreamException timeout() {
        return new DownstreamException("t", new java.net.http.HttpTimeoutException("t"));
    }

    @Test
    void conflictReturns409WithReason() throws Exception {
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.CONFLICT);
        order().andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("STOCK_CONFLICT"));
    }

    @Test
    void inventoryTimeoutReturns504() throws Exception {
        when(inventoryPort.decrease(7, 1)).thenThrow(timeout());
        order().andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.reason").value("INVENTORY_TIMEOUT"));
    }

    @Test
    void productTimeoutReturns504WithoutOrder() throws Exception {
        when(productPort.getEvent(1)).thenThrow(timeout());
        order().andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.reason").value("PRODUCT_TIMEOUT"))
                .andExpect(jsonPath("$.code").value("PRODUCT_TIMEOUT"));
        assertThat(jpa.count()).isZero();

        mvc.perform(get("/internal/orders/stats").param("eventId", "1"))
                .andExpect(jsonPath("$.preOrderFailedByReason.PRODUCT_TIMEOUT").value(1));
    }

    @Test
    void preOrderFailuresCountOnlyRequestedEvent() throws Exception {
        preOrderFailures.record(1, FailReason.PRODUCT_TIMEOUT);
        preOrderFailures.record(2, FailReason.ORDER_DB_TIMEOUT);

        mvc.perform(get("/internal/orders/stats").param("eventId", "1"))
                .andExpect(jsonPath("$.preOrderFailedByReason.PRODUCT_TIMEOUT").value(1))
                .andExpect(jsonPath("$.preOrderFailedByReason.ORDER_DB_TIMEOUT").doesNotExist());
    }

    @Test
    void deleteResetsPreOrderFailures() throws Exception {
        preOrderFailures.record(1, FailReason.PRODUCT_TIMEOUT);
        mvc.perform(delete("/internal/orders")).andExpect(status().isNoContent());

        mvc.perform(get("/internal/orders/stats").param("eventId", "1"))
                .andExpect(jsonPath("$.preOrderFailedByReason.length()").value(0));
    }
}
