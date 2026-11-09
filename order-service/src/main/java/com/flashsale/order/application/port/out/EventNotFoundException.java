package com.flashsale.order.application.port.out;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(long eventId) {
        super("event not found: " + eventId);
    }
}
