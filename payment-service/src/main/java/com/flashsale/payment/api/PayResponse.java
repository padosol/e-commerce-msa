package com.flashsale.payment.api;

import com.flashsale.payment.domain.PaymentStatus;

public record PayResponse(long paymentId, PaymentStatus status) {
}
