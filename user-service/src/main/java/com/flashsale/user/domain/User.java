package com.flashsale.user.domain;

import java.time.Instant;

public class User {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final Instant createdAt;

    private User(Long id, String email, String passwordHash, String name, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = createdAt;
    }

    public static User register(String email, String passwordHash, String name, Instant now) {
        return new User(null, email, passwordHash, name, now);
    }

    public static User restore(long id, String email, String passwordHash, String name, Instant createdAt) {
        return new User(id, email, passwordHash, name, createdAt);
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
