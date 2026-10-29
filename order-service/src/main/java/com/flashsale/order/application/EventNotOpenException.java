package com.flashsale.order.application;

public class EventNotOpenException extends RuntimeException {

    public EventNotOpenException(long eventId) {
        super("event not open yet: " + eventId);
    }
}
