package com.flashsale.user.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtIssuerTest {

    private static final String SECRET = "local-dev-secret-change-me-0123456789abcdef";

    @Test
    void issuedTokenCarriesSubjectAndExpiry() {
        JwtIssuer issuer = new JwtIssuer(new JwtProperties(SECRET, Duration.ofHours(2)));
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(issuer.issue(42)).getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
            .isEqualTo(Duration.ofHours(2).toMillis());
    }
}
