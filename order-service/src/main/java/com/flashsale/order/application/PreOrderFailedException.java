package com.flashsale.order.application;

import com.flashsale.order.domain.FailReason;

public class PreOrderFailedException extends RuntimeException {

    private final FailReason reason;

    public PreOrderFailedException(FailReason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public FailReason reason() {
        return reason;
    }
}
