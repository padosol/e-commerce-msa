package com.flashsale.order.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.EventInfo;
import com.flashsale.order.application.port.out.EventNotFoundException;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpClientsTest {

    private static final Duration CONNECT = Duration.ofSeconds(2);
    private static final Duration READ = Duration.ofSeconds(5);

    private static RestClient restClient(String baseUrl, Duration read) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(HttpClientConfig.requestFactory(CONNECT, read))
                .build();
    }

    @Test
    void decreaseParsesResult() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/inventory/7/decrease", 200, "{\"result\":\"OUT_OF_STOCK\"}")) {
            InventoryHttpClient client = new InventoryHttpClient(restClient(stub.baseUrl(), READ));

            assertThat(client.decrease(7, 1)).isEqualTo(StockDecreaseResult.OUT_OF_STOCK);
            assertThat(stub.lastBody("/internal/inventory/7/decrease")).isEqualTo("{\"quantity\":1}");
        }
    }

    @Test
    void payParsesOutcome() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/payments", 200, "{\"paymentId\":1,\"status\":\"DECLINED\"}")) {
            PaymentHttpClient client = new PaymentHttpClient(restClient(stub.baseUrl(), READ));

            assertThat(client.pay(10, 20, 1000)).isEqualTo(PaymentOutcome.DECLINED);
        }
    }

    @Test
    void getEventParsesInstant() {
        String json = "{\"eventId\":3,\"productId\":4,\"productName\":\"Sneakers\",\"price\":59000,"
                + "\"openAt\":\"2026-11-01T10:00:00Z\",\"saleQuantity\":100}";
        try (StubHttpServer stub = new StubHttpServer().respond("/api/products/events/3", 200, json)) {
            ProductHttpClient client = new ProductHttpClient(restClient(stub.baseUrl(), READ));

            EventInfo info = client.getEvent(3);

            assertThat(info.openAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
            assertThat(info.productName()).isEqualTo("Sneakers");
            assertThat(info).isEqualTo(
                    new EventInfo(3, 4, "Sneakers", 59000, Instant.parse("2026-11-01T10:00:00Z"), 100));
        }
    }

    @Test
    void productNotFoundMapsToEventNotFound() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/api/products/events/9", 404, "{\"code\":\"EVENT_NOT_FOUND\",\"message\":\"x\"}")) {
            ProductHttpClient client = new ProductHttpClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.getEvent(9)).isInstanceOf(EventNotFoundException.class);
        }
    }

    @Test
    void serverErrorMapsToDownstreamException() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/payments", 500, "{}")) {
            PaymentHttpClient client = new PaymentHttpClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOf(DownstreamException.class);
        }
    }

    @Test
    void readTimeoutIsMarkedTimeout() {
        Duration read = Duration.ofMillis(300);
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/payments", 200, "{\"paymentId\":1,\"status\":\"APPROVED\"}", 1000)
                .respond("/internal/inventory/7/decrease", 200, "{\"result\":\"SUCCESS\"}", 1000)
                .respond("/api/products/events/1", 200, "{}", 1000)) {
            PaymentHttpClient payment = new PaymentHttpClient(restClient(stub.baseUrl(), read));
            InventoryHttpClient inventory = new InventoryHttpClient(restClient(stub.baseUrl(), read));
            ProductHttpClient product = new ProductHttpClient(restClient(stub.baseUrl(), read));

            assertThatThrownBy(() -> payment.pay(1, 2, 3)).isInstanceOfSatisfying(DownstreamException.class,
                    e -> assertThat(e.isTimeout()).isTrue());
            assertThatThrownBy(() -> inventory.decrease(7, 1)).isInstanceOfSatisfying(DownstreamException.class,
                    e -> assertThat(e.isTimeout()).isTrue());
            assertThatThrownBy(() -> product.getEvent(1)).isInstanceOfSatisfying(DownstreamException.class,
                    e -> assertThat(e.isTimeout()).isTrue());
        }
    }

    @Test
    void serverErrorIsNotTimeout() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/payments", 500, "{}")) {
            PaymentHttpClient client = new PaymentHttpClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOfSatisfying(DownstreamException.class,
                    e -> assertThat(e.isTimeout()).isFalse());
        }
    }

    @Test
    void decreaseParsesConflict() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/inventory/7/decrease", 200, "{\"result\":\"CONFLICT\"}")) {
            InventoryHttpClient client = new InventoryHttpClient(restClient(stub.baseUrl(), READ));

            assertThat(client.decrease(7, 1)).isEqualTo(StockDecreaseResult.CONFLICT);
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

            PaymentHttpClient client =
                    new PaymentHttpClient(restClient("http://127.0.0.1:" + server.getLocalPort(), READ));

            assertThatThrownBy(() -> client.pay(1, 2, 3)).isInstanceOfSatisfying(DownstreamException.class,
                    e -> assertThat(e.isTimeout()).isFalse());

            Thread.sleep(500); // give any hidden retry time to show up
            assertThat(accepted.get()).isEqualTo(1);
        }
    }
}
