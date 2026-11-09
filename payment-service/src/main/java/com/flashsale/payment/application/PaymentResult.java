package com.flashsale.payment.application;

import com.flashsale.payment.domain.PaymentStatus;

public record PaymentResult(long paymentId, PaymentStatus status) {
}
