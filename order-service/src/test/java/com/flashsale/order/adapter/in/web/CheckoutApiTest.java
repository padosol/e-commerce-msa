package com.flashsale.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.order.TestcontainersConfiguration;
import com.flashsale.order.application.port.out.CatalogProduct;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.application.port.out.StockLine;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutApiTest {

    static final CatalogProduct A = new CatalogProduct(1, "A", 1000);
    static final CatalogProduct B = new CatalogProduct(2, "B", 500);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ProductPort productPort;

    @MockitoBean
    InventoryPort inventoryPort;

    @MockitoBean
    PaymentPort paymentPort;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (1, 1, 2), (1, 2, 1)");
        doAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return List.of(A, B).stream().filter(p -> ids.contains(p.productId())).toList();
        }).when(productPort).findListed(any());
        when(inventoryPort.decreaseAll(anyList())).thenReturn(StockDecreaseResult.SUCCESS);
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.APPROVED);
    }

    private int cartRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE user_id = 1", Integer.class);
    }

    @Test
    void paidReturns201AndAppearsInHistory() throws Exception {
        MvcResult res = mvc.perform(post("/api/cart/checkout").header("X-User-Id", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn();
        String orderId = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.orderId")
                .toString();

        mvc.perform(get("/api/orders/{id}", orderId).header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items.length()").value(0));
        verify(paymentPort).pay(Long.parseLong(orderId), 1, 2500);
        verify(inventoryPort).decreaseAll(List.of(new StockLine(1, 2), new StockLine(2, 1)));
    }

    @Test
    void declinedReturns402AndKeepsCart() throws Exception {
        when(paymentPort.pay(anyLong(), anyLong(), anyLong())).thenReturn(PaymentOutcome.DECLINED);

        mvc.perform(post("/api/cart/checkout").header("X-User-Id", 1))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("PAYMENT_DECLINED"));

        assertThat(cartRows()).isEqualTo(2);
        verify(inventoryPort).increaseAll(List.of(new StockLine(1, 2), new StockLine(2, 1)));
    }

    @Test
    void emptyCartReturns400() throws Exception {
        jdbc.update("DELETE FROM cart_items");

        mvc.perform(post("/api/cart/checkout").header("X-User-Id", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }
}
