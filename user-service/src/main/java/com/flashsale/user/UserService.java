package com.flashsale.user;

import com.flashsale.user.auth.JwtIssuer;
import com.flashsale.user.domain.User;
import com.flashsale.user.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException() {
            super("email already registered");
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("invalid credentials");
        }
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtIssuer jwt;

    public UserService(UserRepository users, PasswordEncoder encoder, JwtIssuer jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public long signup(String email, String password, String name) {
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        try {
            return users.saveAndFlush(new User(email, encoder.encode(password), name)).getId();
        } catch (DataIntegrityViolationException e) {
            throw new EmailTakenException();
        }
    }

    public String login(String email, String password) {
        User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return jwt.issue(user.getId());
    }
}
