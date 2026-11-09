package com.flashsale.order.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.order.TestcontainersConfiguration;
import com.flashsale.order.application.port.out.CatalogProduct;
import com.flashsale.order.application.port.out.InventoryPort;
import com.flashsale.order.application.port.out.PaymentPort;
import com.flashsale.order.application.port.out.ProductPort;
import java.util.Collection;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CartApiTest {

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
        jdbc.update("DELETE FROM cart_items");
        listing(A, B);
    }

    /** The product service answers with whichever of the given products were asked for. */
    private void listing(CatalogProduct... sold) {
        doAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return List.of(sold).stream().filter(p -> ids.contains(p.productId())).toList();
        }).when(productPort).findListed(any());
    }

    private void addItem(long userId, long productId, int quantity) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", productId).header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void putThenGetShowsPricesAndTotal() throws Exception {
        addItem(1, 1, 2);
        addItem(1, 2, 1);

        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].name").value("A"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(2000))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[1].subtotal").value(500))
                .andExpect(jsonPath("$.total").value(2500));
    }

    @Test
    void linesAreListedByProductIdWhateverTheAddOrder() throws Exception {
        listing(A, B);
        addItem(1, 2, 1);
        addItem(1, 1, 1);

        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[1].productId").value(2));
    }

    @Test
    void the101stProductReturns400AndLeavesCartUnchanged() throws Exception {
        for (long id = 1; id <= 100; id++) {
            jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (1, ?, 1)", id);
        }
        listing(new CatalogProduct(101, "Z", 100));

        mvc.perform(put("/api/cart/items/101").header("X-User-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("cart can hold at most 100 products"));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE user_id = 1", Integer.class))
                .isEqualTo(100);
    }

    @Test
    void putReturnsTheCart() throws Exception {
        mvc.perform(put("/api/cart/items/1").header("X-User-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(3000));
    }

    @Test
    void unlistedProductReturns400() throws Exception {
        listing();

        mvc.perform(put("/api/cart/items/1").header("X-User-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));

        listing(A, B);
        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void quantityOutOfRangeReturns400WithoutCallingProductService() throws Exception {
        for (int q : new int[] {0, 100}) {
            mvc.perform(put("/api/cart/items/1").header("X-User-Id", 1)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + q + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verify(productPort, never()).findListed(any());
    }

    @Test
    void emptyCartDoesNotCallProductService() throws Exception {
        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        verify(productPort, never()).findListed(any());
    }

    @Test
    void unavailableLineShownButExcludedFromTotal() throws Exception {
        addItem(1, 1, 2);
        addItem(1, 2, 1);
        listing(A);

        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items[1].productId").value(2))
                .andExpect(jsonPath("$.items[1].available").value(false))
                .andExpect(jsonPath("$.items[1].name").doesNotExist())
                .andExpect(jsonPath("$.items[1].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.items[1].subtotal").doesNotExist())
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.total").value(2000));
    }

    @Test
    void deleteRemovesLine() throws Exception {
        addItem(1, 1, 2);
        addItem(1, 2, 1);

        mvc.perform(delete("/api/cart/items/1").header("X-User-Id", 1)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/cart/items/99").header("X-User-Id", 1)).andExpect(status().isNoContent());

        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(2))
                .andExpect(jsonPath("$.total").value(500));
    }

    @Test
    void cartsAreSeparatedByUser() throws Exception {
        addItem(1, 1, 2);
        addItem(2, 2, 4);

        mvc.perform(get("/api/cart").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.total").value(2000));
        mvc.perform(get("/api/cart").header("X-User-Id", 2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(2))
                .andExpect(jsonPath("$.total").value(2000));
    }

    @Test
    void missingUserIdReturns401() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/cart/items/1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/cart/items/1")).andExpect(status().isUnauthorized());
    }
}
