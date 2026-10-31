package com.flashsale.gateway;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway")
public record GatewayProperties(URI userUri, URI productUri, URI orderUri) {
}
