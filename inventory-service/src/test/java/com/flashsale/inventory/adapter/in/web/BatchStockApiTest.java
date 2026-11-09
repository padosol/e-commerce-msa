package com.flashsale.inventory.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.inventory.TestcontainersConfiguration;
import com.flashsale.inventory.adapter.out.persistence.InventoryJpaEntity;
import com.flashsale.inventory.adapter.out.persistence.InventoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BatchStockApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InventoryJpaRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private ResultActions send(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void duplicateProductIdsReturn400() throws Exception {
        send("/internal/inventory/decrease-all",
            "{\"items\":[{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":1}]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void emptyItemsReturn400() throws Exception {
        send("/internal/inventory/decrease-all", "{\"items\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidItemReturn400() throws Exception {
        send("/internal/inventory/increase-all", "{\"items\":[{\"productId\":1,\"quantity\":0}]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        send("/internal/inventory/increase-all", "{\"items\":[{\"quantity\":1}]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void decreaseAllReturnsResult() throws Exception {
        repository.save(new InventoryJpaEntity(1L, 5));

        send("/internal/inventory/decrease-all", "{\"items\":[{\"productId\":1,\"quantity\":2}]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("SUCCESS"));
        send("/internal/inventory/decrease-all", "{\"items\":[{\"productId\":1,\"quantity\":9}]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("OUT_OF_STOCK"));
    }

    @Test
    void increaseAllReturns204AndUnknownProductReturns404() throws Exception {
        repository.save(new InventoryJpaEntity(1L, 5));

        send("/internal/inventory/increase-all", "{\"items\":[{\"productId\":1,\"quantity\":2}]}")
            .andExpect(status().isNoContent());
        send("/internal/inventory/increase-all", "{\"items\":[{\"productId\":999,\"quantity\":2}]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_NOT_FOUND"));
    }
}
