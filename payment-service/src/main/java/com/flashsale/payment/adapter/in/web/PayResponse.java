package com.flashsale.payment.adapter.in.web;

import com.flashsale.payment.domain.PaymentStatus;

public record PayResponse(long paymentId, PaymentStatus status) {
}
