package com.flashsale.order.client;

public class DownstreamException extends RuntimeException {

    public DownstreamException(String message) {
        super(message);
    }

    public DownstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
