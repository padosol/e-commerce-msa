package com.flashsale.order.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientProperties.class)
public class ClientConfig {

    /** HTTP/1.1 JDK client. No retries anywhere: a resent POST could double-decrease stock. */
    public static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    @Bean
    ProductClient productClient(ClientProperties p) {
        return new ProductClient(restClient(p.productUrl(), p));
    }

    @Bean
    InventoryClient inventoryClient(ClientProperties p) {
        return new InventoryClient(restClient(p.inventoryUrl(), p));
    }

    @Bean
    PaymentClient paymentClient(ClientProperties p) {
        return new PaymentClient(restClient(p.paymentUrl(), p));
    }

    private static RestClient restClient(String baseUrl, ClientProperties p) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(p.connectTimeout(), p.readTimeout()))
                .build();
    }
}
