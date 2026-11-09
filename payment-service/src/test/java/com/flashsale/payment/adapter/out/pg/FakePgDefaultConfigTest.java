package com.flashsale.payment.adapter.out.pg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.flashsale.payment.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
// Other tests change the fake PG config in the shared context; start from a fresh one to see the defaults.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class FakePgDefaultConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    void defaultFailureRateIsTenPercent() throws Exception {
        mvc.perform(get("/internal/payments/config"))
            .andExpect(jsonPath("$.delayMs").value(200))
            .andExpect(jsonPath("$.jitterMs").value(50))
            .andExpect(jsonPath("$.failureRate").value(0.1));
    }
}
