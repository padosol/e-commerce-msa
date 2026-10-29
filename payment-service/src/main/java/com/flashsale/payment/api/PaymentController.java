package com.flashsale.payment.api;

import com.flashsale.payment.PaymentResult;
import com.flashsale.payment.PaymentService;
import com.flashsale.payment.domain.PaymentRepository;
import com.flashsale.payment.simulation.PaymentSimulator;
import com.flashsale.payment.simulation.SimulationConfig;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
public class PaymentController {

    private final PaymentService service;
    private final PaymentSimulator simulator;
    private final PaymentRepository repository;

    public PaymentController(PaymentService service, PaymentSimulator simulator, PaymentRepository repository) {
        this.service = service;
        this.simulator = simulator;
        this.repository = repository;
    }

    @PostMapping
    public PayResponse pay(@Valid @RequestBody PayRequest request) {
        PaymentResult result = service.pay(request.orderId(), request.userId(), request.amount());
        return new PayResponse(result.paymentId(), result.status());
    }

    @GetMapping("/config")
    public SimulationConfig getConfig() {
        return simulator.current();
    }

    @PutMapping("/config")
    public SimulationConfig putConfig(@Valid @RequestBody ConfigRequest request) {
        simulator.update(request.toConfig());
        return simulator.current();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        repository.deleteAllInBatch();
        return ResponseEntity.noContent().build();
    }
}
