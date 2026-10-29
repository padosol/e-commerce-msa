package com.flashsale.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.payment.TestcontainersConfiguration;
import com.flashsale.payment.domain.PaymentRepository;
import com.flashsale.payment.domain.PaymentStatus;
import com.flashsale.payment.simulation.PaymentSimulator;
import com.flashsale.payment.simulation.SimulationConfig;
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
class PaymentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PaymentRepository repository;

    @Autowired
    PaymentSimulator simulator;

    @BeforeEach
    void reset() {
        simulator.update(new SimulationConfig(0, 0, 0.0));
        repository.deleteAll();
    }

    private ResultActions pay() throws Exception {
        return mvc.perform(post("/internal/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":1,\"userId\":2,\"amount\":3000}"));
    }

    private ResultActions putConfig(String body) throws Exception {
        return mvc.perform(put("/internal/payments/config")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void payApprovesAndPersists() throws Exception {
        pay().andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(repository.findAll()).singleElement()
            .satisfies(p -> {
                assertThat(p.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                assertThat(p.getOrderId()).isEqualTo(1);
                assertThat(p.getAmount()).isEqualTo(3000);
            });
    }

    @Test
    void payDeclinesWhenFailureRateIsOne() throws Exception {
        putConfig("{\"delayMs\":0,\"jitterMs\":0,\"failureRate\":1.0}").andExpect(status().isOk());

        pay().andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(repository.findAll()).singleElement()
            .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.DECLINED));
    }

    @Test
    void payWaitsConfiguredDelay() throws Exception {
        putConfig("{\"delayMs\":300,\"jitterMs\":0,\"failureRate\":0.0}").andExpect(status().isOk());

        long start = System.nanoTime();
        pay().andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    @Test
    void invalidConfigReturns400() throws Exception {
        putConfig("{\"delayMs\":0,\"jitterMs\":0,\"failureRate\":1.5}").andExpect(status().isBadRequest());
        putConfig("{\"delayMs\":-1,\"jitterMs\":0,\"failureRate\":0.5}").andExpect(status().isBadRequest());
        putConfig("{\"delayMs\":0,\"jitterMs\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void getConfigReturnsCurrent() throws Exception {
        putConfig("{\"delayMs\":5,\"jitterMs\":1,\"failureRate\":0.5}").andExpect(status().isOk());

        mvc.perform(get("/internal/payments/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.delayMs").value(5))
            .andExpect(jsonPath("$.jitterMs").value(1))
            .andExpect(jsonPath("$.failureRate").value(0.5));
    }

    @Test
    void deleteClearsPayments() throws Exception {
        pay().andExpect(status().isOk());
        pay().andExpect(status().isOk());
        assertThat(repository.count()).isEqualTo(2);

        mvc.perform(delete("/internal/payments")).andExpect(status().isNoContent());

        assertThat(repository.count()).isZero();
    }
}
