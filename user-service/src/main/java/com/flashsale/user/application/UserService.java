package com.flashsale.user.application;

import com.flashsale.user.application.port.out.TokenIssuer;
import com.flashsale.user.application.port.out.UserRepository;
import com.flashsale.user.domain.EmailTakenException;
import com.flashsale.user.domain.InvalidCredentialsException;
import com.flashsale.user.domain.User;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenIssuer tokens;

    public UserService(UserRepository users, PasswordEncoder encoder, TokenIssuer tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    public long signup(String email, String password, String name) {
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        return users.save(User.register(email, encoder.encode(password), name, Instant.now())).id();
    }

    public String login(String email, String password) {
        User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!encoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.id());
    }
}
