package com.flashsale.product.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.product.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clearCatalog() {
        jdbc.update("DELETE FROM products WHERE listed = TRUE");
    }

    private int register(String name, long price) throws Exception {
        String body = mvc.perform(post("/internal/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.productId");
    }

    private String createEvent(String productName) throws Exception {
        return mvc.perform(post("/internal/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productName\":\"" + productName
                    + "\",\"price\":5000,\"openAt\":\"2026-11-01T10:00:00Z\",\"saleQuantity\":10}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    void listShowsOnlyListedProductsInIdOrder() throws Exception {
        int a = register("A", 1000);
        int b = register("B", 2000);
        createEvent("event-product");

        mvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products.length()").value(2))
            .andExpect(jsonPath("$.products[0].productId").value(a))
            .andExpect(jsonPath("$.products[0].name").value("A"))
            .andExpect(jsonPath("$.products[0].price").value(1000))
            .andExpect(jsonPath("$.products[1].productId").value(b))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void detailReturnsListedProduct() throws Exception {
        int a = register("A", 1000);
        mvc.perform(get("/api/products/" + a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(a))
            .andExpect(jsonPath("$.name").value("A"))
            .andExpect(jsonPath("$.price").value(1000));
    }

    @Test
    void eventProductDetailIs404() throws Exception {
        int productId = JsonPath.read(createEvent("event-product"), "$.productId");
        mvc.perform(get("/api/products/" + productId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void eventLookupStillWorks() throws Exception {
        int eventId = JsonPath.read(createEvent("event-product"), "$.eventId");
        mvc.perform(get("/api/products/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productName").value("event-product"));
    }

    @Test
    void internalLookupReturnsOnlyListed() throws Exception {
        int a = register("A", 1000);
        int eventProductId = JsonPath.read(createEvent("event-product"), "$.productId");
        mvc.perform(get("/internal/products").param("ids", a + "," + eventProductId + ",999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].productId").value(a))
            .andExpect(jsonPath("$[0].name").value("A"))
            .andExpect(jsonPath("$[0].price").value(1000));
    }

    @Test
    void internalLookupRejectsEmptyOrTooManyIds() throws Exception {
        mvc.perform(get("/internal/products"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/internal/products").param("ids", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        String many = String.join(",", java.util.stream.LongStream.rangeClosed(1, 101)
            .mapToObj(String::valueOf).toList());
        mvc.perform(get("/internal/products").param("ids", many))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void sizeOver100Returns400() throws Exception {
        mvc.perform(get("/api/products").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/products").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsNegativePrice() throws Exception {
        mvc.perform(post("/internal/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"price\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/internal/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \",\"price\":1}"))
            .andExpect(status().isBadRequest());
    }
}
