package com.flashsale.order.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients")
public record ClientProperties(
        String productUrl,
        String inventoryUrl,
        String paymentUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
