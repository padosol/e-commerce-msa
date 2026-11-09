package com.flashsale.order.adapter.out.http;

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
public class HttpClientConfig {

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
    ProductHttpClient productClient(ClientProperties p) {
        return new ProductHttpClient(restClient(p.productUrl(), p));
    }

    @Bean
    InventoryHttpClient inventoryClient(ClientProperties p) {
        return new InventoryHttpClient(restClient(p.inventoryUrl(), p));
    }

    @Bean
    PaymentHttpClient paymentClient(ClientProperties p) {
        return new PaymentHttpClient(restClient(p.paymentUrl(), p));
    }

    private static RestClient restClient(String baseUrl, ClientProperties p) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(p.connectTimeout(), p.readTimeout()))
                .build();
    }
}
