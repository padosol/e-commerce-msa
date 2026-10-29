package com.flashsale.user.auth;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

@Component
public class JwtIssuer {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtIssuer(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(Long.toString(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(properties.ttl())))
            .signWith(key)
            .compact();
    }
}
