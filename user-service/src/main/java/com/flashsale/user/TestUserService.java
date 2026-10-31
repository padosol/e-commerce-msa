package com.flashsale.user;

import com.flashsale.user.auth.JwtIssuer;
import com.flashsale.user.api.TestUserResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class TestUserService {

    static final String PASSWORD = "loadtest";
    private static final String EMAIL_PREFIX = "loadtest-";
    private static final String EMAIL_SUFFIX = "@test.local";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtIssuer jwt;

    public TestUserService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtIssuer jwt) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public List<TestUserResponse> ensure(int count) {
        String hash = encoder.encode(PASSWORD);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<String> emails = new ArrayList<>(count);
        for (int n = 1; n <= count; n++) {
            emails.add(EMAIL_PREFIX + n + EMAIL_SUFFIX);
        }
        jdbc.batchUpdate(
            "INSERT IGNORE INTO users (email, password_hash, name, created_at) VALUES (?, ?, ?, ?)",
            emails, 1000, (ps, email) -> {
                ps.setString(1, email);
                ps.setString(2, hash);
                ps.setString(3, email.substring(0, email.indexOf('@')));
                ps.setObject(4, now);
            });
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, email FROM users WHERE email LIKE ?",
            rs -> {
                ids.put(rs.getString(2), rs.getLong(1));
            }, EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        List<TestUserResponse> result = new ArrayList<>(count);
        for (String email : emails) {
            long id = ids.get(email);
            result.add(new TestUserResponse(id, jwt.issue(id)));
        }
        return result;
    }
}
