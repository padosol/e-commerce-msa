package com.flashsale.order.application.port.out;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

public class DownstreamException extends RuntimeException {

    private final boolean timeout;

    public DownstreamException(String message) {
        super(message);
        this.timeout = false;
    }

    public DownstreamException(String message, Throwable cause) {
        super(message, cause);
        this.timeout = causedByTimeout(cause);
    }

    /** True when the downstream call failed by connect or read timeout (as opposed to an error response). */
    public boolean isTimeout() {
        return timeout;
    }

    private static boolean causedByTimeout(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
