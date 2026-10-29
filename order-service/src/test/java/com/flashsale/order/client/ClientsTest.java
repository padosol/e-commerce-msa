package com.flashsale.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ClientsTest {

    private static final Duration CONNECT = Duration.ofSeconds(2);
    private static final Duration READ = Duration.ofSeconds(5);

    private static RestClient restClient(String baseUrl, Duration read) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientConfig.requestFactory(CONNECT, read))
                .build();
    }

    @Test
    void decreaseParsesResult() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/inventory/7/decrease", 200, "{\"result\":\"OUT_OF_STOCK\"}")) {
            InventoryClient client = new InventoryClient(restClient(stub.baseUrl(), READ));

            assertThat(client.decrease(7, 1)).isEqualTo(StockDecreaseResult.OUT_OF_STOCK);
            assertThat(stub.lastBody("/internal/inventory/7/decrease")).isEqualTo("{\"quantity\":1}");
        }
    }

    @Test
    void payParsesOutcome() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/payments", 200, "{\"paymentId\":1,\"status\":\"DECLINED\"}")) {
            PaymentClient client = new PaymentClient(restClient(stub.baseUrl(), READ));

            assertThat(client.pay(10, 20, 1000)).isEqualTo(PaymentOutcome.DECLINED);
        }
    }

    @Test
    void getEventParsesInstant() {
        String json = "{\"eventId\":3,\"productId\":4,\"productName\":\"Sneakers\",\"price\":59000,"
                + "\"openAt\":\"2026-11-01T10:00:00Z\",\"saleQuantity\":100}";
        try (StubHttpServer stub = new StubHttpServer().respond("/api/products/events/3", 200, json)) {
            ProductClient client = new ProductClient(restClient(stub.baseUrl(), READ));

            EventInfo info = client.getEvent(3);

            assertThat(info.openAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
            assertThat(info).isEqualTo(new EventInfo(3, 4, 59000, Instant.parse("2026-11-01T10:00:00Z"), 100));
        }
    }

    @Test
    void productNotFoundMapsToEventNotFound() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/api/products/events/9", 404, "{\"code\":\"EVENT_NOT_FOUND\",\"message\":\"x\"}")) {
            ProductClient client = new ProductClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.getEvent(9)).isInstanceOf(EventNotFoundException.class);
        }
    }

    @Test
    void serverErrorMapsToDownstreamException() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/payments", 500, "{}")) {
            PaymentClient client = new PaymentClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOf(DownstreamException.class);
        }
    }

    @Test
    void readTimeoutMapsToDownstreamException() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/payments", 200, "{\"paymentId\":1,\"status\":\"APPROVED\"}", 1000)) {
            PaymentClient client = new PaymentClient(restClient(stub.baseUrl(), Duration.ofMillis(300)));

            assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOf(DownstreamException.class);
        }
    }

    @Test
    void droppedConnectionIsNotRetried() throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) {
                        accepted.incrementAndGet();
                        InputStream in = s.getInputStream();
                        in.read(new byte[8192]); // consume the request, then close without a response
                    } catch (Exception e) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            PaymentClient client = new PaymentClient(restClient("http://127.0.0.1:" + server.getLocalPort(), READ));

            assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOf(DownstreamException.class);

            Thread.sleep(500); // give any hidden retry time to show up
            assertThat(accepted.get()).isEqualTo(1);
        }
    }
}
