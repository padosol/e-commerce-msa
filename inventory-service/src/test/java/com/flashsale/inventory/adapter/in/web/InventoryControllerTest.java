package com.flashsale.inventory.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.adapter.out.persistence.InventoryJpaRepository;
import com.flashsale.inventory.application.StockStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InventoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InventoryJpaRepository repository;

    @Autowired
    StockStats stats;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        stats.reset();
    }

    private void putQuantity(long productId, int quantity) throws Exception {
        mvc.perform(put("/internal/inventory/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}"))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions postQuantity(String path, int quantity) throws Exception {
        return mvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"quantity\":" + quantity + "}"));
    }

    @Test
    void putThenGetReturnsQuantity() throws Exception {
        putQuantity(1L, 7);

        mvc.perform(get("/internal/inventory/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(1))
            .andExpect(jsonPath("$.quantity").value(7));
    }

    @Test
    void decreaseReturnsResultBody() throws Exception {
        putQuantity(1L, 1);

        postQuantity("/internal/inventory/1/decrease", 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("SUCCESS"));
        postQuantity("/internal/inventory/1/decrease", 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("OUT_OF_STOCK"));
    }

    @Test
    void increaseReturns204() throws Exception {
        putQuantity(1L, 1);

        postQuantity("/internal/inventory/1/increase", 1).andExpect(status().isNoContent());

        mvc.perform(get("/internal/inventory/1"))
            .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void unknownProductReturns404WithCode() throws Exception {
        mvc.perform(get("/internal/inventory/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
    }

    @Test
    void invalidQuantityReturns400() throws Exception {
        putQuantity(1L, 5);

        postQuantity("/internal/inventory/1/decrease", 0).andExpect(status().isBadRequest());
    }

    @Test
    void strategyEndpointReportsNaive() throws Exception {
        mvc.perform(get("/internal/strategy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategy").value("naive"));
    }

    @Test
    void statsEndpointReportsAndResets() throws Exception {
        stats.conflict();
        stats.conflict();
        stats.retry();

        mvc.perform(get("/internal/inventory/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conflicts").value(2))
            .andExpect(jsonPath("$.retries").value(1))
            .andExpect(jsonPath("$.exhausted").value(0));
        mvc.perform(delete("/internal/inventory/stats")).andExpect(status().isNoContent());
        mvc.perform(get("/internal/inventory/stats"))
            .andExpect(jsonPath("$.conflicts").value(0));
    }
}
