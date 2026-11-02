package com.flashsale.gateway;

import static com.flashsale.gateway.GatewayTestSupport.key;
import static com.flashsale.gateway.GatewayTestSupport.send;
import static com.flashsale.gateway.GatewayTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.http.clients.read-timeout=1s")
class GatewayTimeoutTest {

    private static final EchoServer USER = new EchoServer();
    private static final EchoServer PRODUCT = new EchoServer();
    private static final EchoServer ORDER = new EchoServer();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void uris(DynamicPropertyRegistry registry) {
        registry.add("gateway.user-uri", USER::uri);
        registry.add("gateway.product-uri", PRODUCT::uri);
        registry.add("gateway.order-uri", ORDER::uri);
    }

    @AfterAll
    static void stopStubs() {
        USER.close();
        PRODUCT.close();
        ORDER.close();
    }

    @LocalServerPort
    int port;

    @Value("${jwt.secret}")
    String secret;

    @BeforeEach
    void resetStubs() {
        ORDER.reset();
    }

    @Test
    void downstreamReadTimeoutReturns504() throws Exception {
        ORDER.setDelayMillis(3000);
        String t = token("42", key(secret), Instant.now().plusSeconds(600));

        HttpResponse<String> r = send(port, "POST", "/api/orders", "Authorization", "Bearer " + t);

        assertThat(r.statusCode()).isEqualTo(504);
        assertThat(JSON.readTree(r.body()).get("code").asString()).isEqualTo("GATEWAY_TIMEOUT");
    }
}
