package com.flashsale.order.client;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(long eventId) {
        super("event not found: " + eventId);
    }
}
