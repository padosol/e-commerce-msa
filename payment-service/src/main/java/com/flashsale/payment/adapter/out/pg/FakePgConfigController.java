package com.flashsale.payment.adapter.out.pg;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Config API of the fake PG. It goes away together with the fake gateway. */
@RestController
@RequestMapping("/internal/payments/config")
@ConditionalOnProperty(name = "payment.gateway", havingValue = "fake", matchIfMissing = true)
public class FakePgConfigController {

    private final FakePaymentGateway gateway;

    public FakePgConfigController(FakePaymentGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    public FakePgConfig get() {
        return gateway.current();
    }

    @PutMapping
    public FakePgConfig put(@Valid @RequestBody FakePgConfigRequest request) {
        gateway.update(request.toConfig());
        return gateway.current();
    }
}
