package com.flashsale.order.adapter.out.http;

import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.EventInfo;
import com.flashsale.order.application.port.out.EventNotFoundException;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.application.port.out.CatalogProduct;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class ProductHttpClient implements ProductPort {

    private final RestClient restClient;

    public ProductHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public EventInfo getEvent(long eventId) {
        try {
            EventInfo info = restClient.get()
                    .uri("/api/products/events/{eventId}", eventId)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                            throw new EventNotFoundException(eventId);
                        }
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new DownstreamException("product-service returned " + res.getStatusCode().value());
                        }
                        return res.bodyTo(EventInfo.class);
                    });
            if (info == null) {
                throw new DownstreamException("product-service returned empty body");
            }
            return info;
        } catch (EventNotFoundException | DownstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DownstreamException("product-service call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CatalogProduct> findListed(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        String ids = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            List<CatalogProduct> products = restClient.get()
                    .uri(uri -> uri.path("/internal/products").queryParam("ids", ids).build())
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new DownstreamException("product-service returned " + res.getStatusCode().value());
                        }
                        return res.bodyTo(new ParameterizedTypeReference<List<CatalogProduct>>() { });
                    });
            if (products == null) {
                throw new DownstreamException("product-service returned empty body");
            }
            return products;
        } catch (DownstreamException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DownstreamException("product-service call failed: " + e.getMessage(), e);
        }
    }
}
