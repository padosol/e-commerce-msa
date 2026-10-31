package com.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

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

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void resetStubs() {
        USER.reset();
        PRODUCT.reset();
        ORDER.reset();
    }

    private SecretKey key(String s) {
        return Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }

    private String token(String sub, SecretKey key, Instant expiresAt) {
        return Jwts.builder().subject(sub)
            .issuedAt(Date.from(Instant.now().minusSeconds(60)))
            .expiration(Date.from(expiresAt))
            .signWith(key).compact();
    }

    private HttpResponse<String> send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, method.equals("POST")
                ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody());
        if (method.equals("POST")) {
            b.header("Content-Type", "application/json");
        }
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ordersWithoutTokenReturn401AndSkipDownstream() throws Exception {
        HttpResponse<String> response = send("POST", "/api/orders");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(ORDER.hits()).isZero();
    }

    @Test
    void ordersWithInvalidSignatureReturn401() throws Exception {
        String t = token("42", key("another-secret-another-secret-another-secret"), Instant.now().plusSeconds(600));

        HttpResponse<String> response = send("POST", "/api/orders", "Authorization", "Bearer " + t);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(ORDER.hits()).isZero();
    }

    @Test
    void ordersWithExpiredTokenReturn401() throws Exception {
        String t = token("42", key(secret), Instant.now().minusSeconds(30));

        HttpResponse<String> response = send("POST", "/api/orders", "Authorization", "Bearer " + t);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(ORDER.hits()).isZero();
    }

    @Test
    void ordersWithValidTokenForwardUserId() throws Exception {
        String t = token("42", key(secret), Instant.now().plusSeconds(600));

        HttpResponse<String> response = send("POST", "/api/orders", "Authorization", "Bearer " + t);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("userId").asString()).isEqualTo("42");
        assertThat(body.get("path").asString()).isEqualTo("/api/orders");
        assertThat(ORDER.hits()).isEqualTo(1);
    }

    @Test
    void clientSuppliedUserIdIsReplaced() throws Exception {
        String t = token("42", key(secret), Instant.now().plusSeconds(600));

        HttpResponse<String> response = send("POST", "/api/orders",
            "Authorization", "Bearer " + t, "X-User-Id", "999");

        assertThat(JSON.readTree(response.body()).get("userId").asString()).isEqualTo("42");
    }

    @Test
    void publicRoutesStripClientUserId() throws Exception {
        HttpResponse<String> response = send("GET", "/api/products/events/1", "X-User-Id", "999");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("userId").isNull()).isTrue();
        assertThat(body.get("path").asString()).isEqualTo("/api/products/events/1");
        assertThat(PRODUCT.hits()).isEqualTo(1);
    }

    @Test
    void internalPathsAreNotRouted() throws Exception {
        HttpResponse<String> response = send("GET", "/internal/inventory/1");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(USER.hits() + PRODUCT.hits() + ORDER.hits()).isZero();
    }

    @Test
    void slowDownstreamWithinReadTimeoutSucceeds() throws Exception {
        ORDER.setDelayMillis(2000);
        String t = token("42", key(secret), Instant.now().plusSeconds(600));

        HttpResponse<String> response = send("POST", "/api/orders", "Authorization", "Bearer " + t);

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
