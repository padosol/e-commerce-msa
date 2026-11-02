package com.flashsale.gateway;

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

/** Shared JWT and HTTP helpers for the gateway tests. */
final class GatewayTestSupport {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private GatewayTestSupport() {
    }

    static SecretKey key(String s) {
        return Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }

    static String token(String sub, SecretKey key, Instant expiresAt) {
        return Jwts.builder().subject(sub)
            .issuedAt(Date.from(Instant.now().minusSeconds(60)))
            .expiration(Date.from(expiresAt))
            .signWith(key).compact();
    }

    static HttpResponse<String> send(int port, String method, String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, method.equals("POST")
                ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody());
        if (method.equals("POST")) {
            b.header("Content-Type", "application/json");
        }
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
