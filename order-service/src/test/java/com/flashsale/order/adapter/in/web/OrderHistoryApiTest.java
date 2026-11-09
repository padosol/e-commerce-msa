package com.flashsale.order.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.order.TestcontainersConfiguration;
import com.flashsale.order.application.port.out.EventInfo;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.OrderRepository;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderLine;
import java.time.Clock;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderHistoryApiTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderRepository repository;

    @MockitoBean
    ProductPort productPort;

    @MockitoBean
    InventoryPort inventoryPort;

    @MockitoBean
    PaymentPort paymentPort;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(clock.instant()).thenReturn(NOW);
    }

    private long place(long userId, OrderLine... lines) {
        Order saved = repository.save(Order.pending(userId, null, List.of(lines), NOW));
        saved.markPaid(NOW);
        repository.save(saved);
        return saved.id();
    }

    private static OrderLine line(long productId, String name, long price, int quantity) {
        return new OrderLine(productId, name, price, quantity);
    }

    @Test
    void listsOnlyMyOrdersNewestFirst() throws Exception {
        long first = place(5, line(1, "A", 1000, 1));
        long second = place(5, line(2, "B", 500, 2));
        long third = place(5, line(3, "C", 300, 1));
        place(6, line(1, "A", 1000, 1));

        mvc.perform(get("/api/orders").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.orders.length()").value(3))
                .andExpect(jsonPath("$.orders[0].orderId").value(third))
                .andExpect(jsonPath("$.orders[1].orderId").value(second))
                .andExpect(jsonPath("$.orders[2].orderId").value(first))
                .andExpect(jsonPath("$.orders[1].status").value("PAID"))
                .andExpect(jsonPath("$.orders[1].amount").value(1000))
                .andExpect(jsonPath("$.orders[1].itemCount").value(1))
                .andExpect(jsonPath("$.orders[1].firstProductName").value("B"));
    }

    @Test
    void paginates() throws Exception {
        long oldest = place(5, line(1, "A", 1000, 1));
        long middle = place(5, line(2, "B", 500, 1));
        place(5, line(3, "C", 300, 1));

        mvc.perform(get("/api/orders").param("page", "1").param("size", "1").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].orderId").value(middle))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/orders").param("page", "2").param("size", "1").header("X-User-Id", "5"))
                .andExpect(jsonPath("$.orders[0].orderId").value(oldest));
    }

    @Test
    void detailHasItemsAndAmount() throws Exception {
        long id = place(5, line(1, "A", 1000, 2), line(2, "B", 500, 1));

        mvc.perform(get("/api/orders/" + id).header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(id))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(2500))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("A"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(2000))
                .andExpect(jsonPath("$.items[1].subtotal").value(500));
    }

    @Test
    void otherUsersOrderReturns404() throws Exception {
        long id = place(5, line(1, "A", 1000, 1));

        mvc.perform(get("/api/orders/" + id).header("X-User-Id", "6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void eventOrderAppearsInHistory() throws Exception {
        when(productPort.getEvent(1)).thenReturn(
                new EventInfo(1, 7, "Sneakers", 10000, Instant.parse("2026-11-01T09:59:00Z"), 100));
        when(inventoryPort.decrease(7, 1)).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.APPROVED);
        mvc.perform(post("/api/orders").header("X-User-Id", "5")
                .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/orders").header("X-User-Id", "5"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.orders[0].firstProductName").value("Sneakers"));
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/orders").header("X-User-Id", "5")).andReturn().getResponse()
                        .getContentAsString(), "$.orders[0].orderId")).longValue();
        mvc.perform(get("/api/orders/" + id).header("X-User-Id", "5"))
                .andExpect(jsonPath("$.eventId").value(1));
    }

    @Test
    void sizeOver100Returns400() throws Exception {
        mvc.perform(get("/api/orders").param("size", "101").header("X-User-Id", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidPagingAndIdReturn400() throws Exception {
        mvc.perform(get("/api/orders").param("page", "-1").header("X-User-Id", "5"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("size", "0").header("X-User-Id", "5"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("page", "x").header("X-User-Id", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/orders/abc").header("X-User-Id", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingUserIdReturns401() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized());
    }
}
