package com.flashsale.payment.adapter.in.web;

import com.flashsale.payment.application.PaymentResult;
import com.flashsale.payment.application.PaymentService;
import com.flashsale.payment.application.port.out.PaymentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
public class PaymentController {

    private final PaymentService service;
    private final PaymentRepository repository;

    public PaymentController(PaymentService service, PaymentRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping
    public PayResponse pay(@Valid @RequestBody PayRequest request) {
        PaymentResult result = service.pay(request.orderId(), request.userId(), request.amount());
        return new PayResponse(result.paymentId(), result.status());
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
