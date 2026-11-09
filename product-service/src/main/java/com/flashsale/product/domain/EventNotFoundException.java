package com.flashsale.product.domain;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(long eventId) {
        super("event not found: " + eventId);
    }
}
