package com.flashsale.user.application;

import com.flashsale.user.application.port.out.TestUserStore;
import com.flashsale.user.application.port.out.TokenIssuer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class TestUserService {

    public record TestUser(long userId, String accessToken) {
    }

    static final String PASSWORD = "loadtest";
    private static final String EMAIL_PREFIX = "loadtest-";
    private static final String EMAIL_SUFFIX = "@test.local";

    private final TestUserStore store;
    private final PasswordEncoder encoder;
    private final TokenIssuer tokens;

    public TestUserService(TestUserStore store, PasswordEncoder encoder, TokenIssuer tokens) {
        this.store = store;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    public List<TestUser> ensure(int count) {
        String hash = encoder.encode(PASSWORD);
        List<String> emails = new ArrayList<>(count);
        for (int n = 1; n <= count; n++) {
            emails.add(EMAIL_PREFIX + n + EMAIL_SUFFIX);
        }
        Map<String, Long> ids = store.ensure(emails, hash, Instant.now());
        List<TestUser> result = new ArrayList<>(count);
        for (String email : emails) {
            long id = ids.get(email);
            result.add(new TestUser(id, tokens.issue(id)));
        }
        return result;
    }
}
