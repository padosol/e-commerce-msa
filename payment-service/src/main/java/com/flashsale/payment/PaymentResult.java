package com.flashsale.payment;

import com.flashsale.payment.domain.PaymentStatus;

public record PaymentResult(long paymentId, PaymentStatus status) {
}
