package com.flashsale.user.adapter.out.token;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import com.flashsale.user.application.port.out.TokenIssuer;
import org.springframework.stereotype.Component;

@Component
public class JwtIssuer implements TokenIssuer {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtIssuer(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
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
