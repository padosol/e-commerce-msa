package com.flashsale.user.adapter.out.persistence;

import com.flashsale.user.application.port.out.TestUserStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcTestUserStore implements TestUserStore {

    private static final String EMAIL_PREFIX = "loadtest-";
    private static final String EMAIL_SUFFIX = "@test.local";

    private final JdbcTemplate jdbc;

    JdbcTestUserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Long> ensure(List<String> emails, String passwordHash, Instant now) {
        LocalDateTime createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.batchUpdate(
            "INSERT IGNORE INTO users (email, password_hash, name, created_at) VALUES (?, ?, ?, ?)",
            emails, 1000, (ps, email) -> {
                ps.setString(1, email);
                ps.setString(2, passwordHash);
                ps.setString(3, email.substring(0, email.indexOf('@')));
                ps.setObject(4, createdAt);
            });
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, email FROM users WHERE email LIKE ?",
            rs -> {
                ids.put(rs.getString(2), rs.getLong(1));
            }, EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        return ids;
    }
}
