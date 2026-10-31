package com.flashsale.order.client;

import java.util.Map;
import org.springframework.web.client.RestClient;

public class PaymentClient {

    private record PayResponse(long paymentId, PaymentOutcome status) {
    }

    private final RestClient restClient;

    public PaymentClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public PaymentOutcome pay(long orderId, long userId, long amount) {
        try {
            PayResponse body = restClient.post()
                    .uri("/internal/payments")
                    .body(Map.of("orderId", orderId, "userId", userId, "amount", amount))
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new DownstreamException("payment-service returned " + res.getStatusCode().value());
                        }
                        return res.bodyTo(PayResponse.class);
                    });
            if (body == null || body.status() == null) {
                throw new DownstreamException("payment-service returned empty status");
            }
            return body.status();
        } catch (DownstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DownstreamException("payment call failed: " + e.getMessage(), e);
        }
    }
}
