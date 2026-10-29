package com.flashsale.product;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(long eventId) {
        super("event not found: " + eventId);
    }
}
