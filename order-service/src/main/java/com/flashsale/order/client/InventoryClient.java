package com.flashsale.order.client;

import java.util.Map;
import org.springframework.web.client.RestClient;

public class InventoryClient {

    private record DecreaseResponse(StockDecreaseResult result) {
    }

    private final RestClient restClient;

    public InventoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public StockDecreaseResult decrease(long productId, int quantity) {
        try {
            DecreaseResponse body = restClient.post()
                    .uri("/internal/inventory/{productId}/decrease", productId)
                    .body(Map.of("quantity", quantity))
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new DownstreamException("inventory-service returned " + res.getStatusCode().value());
                        }
                        return res.bodyTo(DecreaseResponse.class);
                    });
            if (body == null || body.result() == null) {
                throw new DownstreamException("inventory-service returned empty result");
            }
            return body.result();
        } catch (DownstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DownstreamException("inventory decrease failed: " + e.getMessage(), e);
        }
    }

    public void increase(long productId, int quantity) {
        try {
            restClient.post()
                    .uri("/internal/inventory/{productId}/increase", productId)
                    .body(Map.of("quantity", quantity))
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new DownstreamException("inventory-service returned " + res.getStatusCode().value());
                        }
                        return null;
                    });
        } catch (DownstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DownstreamException("inventory increase failed: " + e.getMessage(), e);
        }
    }
}
