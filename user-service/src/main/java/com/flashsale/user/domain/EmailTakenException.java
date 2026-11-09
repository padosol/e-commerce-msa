package com.flashsale.user.domain;

public class EmailTakenException extends RuntimeException {
    public EmailTakenException() {
        super("email already registered");
    }
}
