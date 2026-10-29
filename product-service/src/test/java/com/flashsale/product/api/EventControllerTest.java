package com.flashsale.product.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.product.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
class EventControllerTest {

    @Autowired
    MockMvc mvc;

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/internal/events").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void createThenGetEvent() throws Exception {
        String created = create("""
            {"productName":"flash","price":10000,"openAt":"2026-11-01T10:00:00Z","saleQuantity":100}""")
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int eventId = JsonPath.read(created, "$.eventId");
        int productId = JsonPath.read(created, "$.productId");

        mvc.perform(get("/api/products/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(eventId))
            .andExpect(jsonPath("$.productId").value(productId))
            .andExpect(jsonPath("$.productName").value("flash"))
            .andExpect(jsonPath("$.price").value(10000))
            .andExpect(jsonPath("$.openAt").value("2026-11-01T10:00:00Z"))
            .andExpect(jsonPath("$.saleQuantity").value(100));
    }

    @Test
    void unknownEventReturns404() throws Exception {
        mvc.perform(get("/api/products/events/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void invalidCreateReturns400() throws Exception {
        create("""
            {"productName":"flash","price":10000,"openAt":"2026-11-01T10:00:00Z","saleQuantity":0}""")
            .andExpect(status().isBadRequest());
        create("""
            {"productName":"flash","price":10000,"openAt":"2026-11-01T10:00:00Z"}""")
            .andExpect(status().isBadRequest());
    }
}
