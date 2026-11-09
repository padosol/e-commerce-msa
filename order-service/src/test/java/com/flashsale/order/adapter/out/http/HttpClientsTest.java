package com.flashsale.order.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flashsale.order.application.port.out.CatalogProduct;
import com.flashsale.order.application.port.out.DownstreamException;
import com.flashsale.order.application.port.out.EventInfo;
import com.flashsale.order.application.port.out.EventNotFoundException;
import com.flashsale.order.application.port.out.PaymentOutcome;
import com.flashsale.order.application.port.out.StockDecreaseResult;
import com.flashsale.order.application.port.out.StockLine;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    void decreaseAllSendsItemsAndParsesResult() {
        try (StubHttpServer stub = new StubHttpServer()
                .respond("/internal/inventory/decrease-all", 200, "{\"result\":\"OUT_OF_STOCK\"}")) {
            InventoryHttpClient client = new InventoryHttpClient(restClient(stub.baseUrl(), READ));

            assertThat(client.decreaseAll(List.of(new StockLine(1, 2), new StockLine(2, 1))))
                    .isEqualTo(StockDecreaseResult.OUT_OF_STOCK);
            assertThat(stub.lastBody("/internal/inventory/decrease-all")).isEqualTo(
                    "{\"items\":[{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]}");
        }
    }

    @Test
    void decreaseAllErrorIsDownstreamException() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/inventory/decrease-all", 400, "{}")) {
            InventoryHttpClient client = new InventoryHttpClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.decreaseAll(List.of(new StockLine(1, 2))))
                    .isInstanceOf(DownstreamException.class);
        }
    }

    @Test
    void increaseAllSendsItems() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/inventory/increase-all", 204, "")) {
            InventoryHttpClient client = new InventoryHttpClient(restClient(stub.baseUrl(), READ));

            client.increaseAll(List.of(new StockLine(1, 2), new StockLine(2, 1)));

            assertThat(stub.hits("/internal/inventory/increase-all")).isEqualTo(1);
            assertThat(stub.lastBody("/internal/inventory/increase-all")).isEqualTo(
                    "{\"items\":[{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]}");
        }
    }

    @Test
    void increaseAllNotFoundIsDownstreamException() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/inventory/increase-all", 404, "{}")) {
            InventoryHttpClient client = new InventoryHttpClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.increaseAll(List.of(new StockLine(1, 2))))
                    .isInstanceOf(DownstreamException.class);
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
    void findListedSendsIdsAndParsesProducts() {
        String json = "[{\"productId\":7,\"name\":\"A\",\"price\":1000},{\"productId\":8,\"name\":\"B\",\"price\":500}]";
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/products", 200, json)) {
            ProductHttpClient client = new ProductHttpClient(restClient(stub.baseUrl(), READ));

            List<CatalogProduct> products = client.findListed(List.of(7L, 8L));

            assertThat(stub.lastQuery("/internal/products")).isEqualTo("ids=7,8");
            assertThat(products).containsExactly(new CatalogProduct(7, "A", 1000), new CatalogProduct(8, "B", 500));
        }
    }

    @Test
    void findListedWithNoIdsMakesNoCall() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/products", 200, "[]")) {
            ProductHttpClient client = new ProductHttpClient(restClient(stub.baseUrl(), READ));

            assertThat(client.findListed(List.of())).isEmpty();
            assertThat(stub.hits("/internal/products")).isZero();
        }
    }

    @Test
    void findListedFailureIsDownstreamError() {
        try (StubHttpServer stub = new StubHttpServer().respond("/internal/products", 500, "{}")) {
            ProductHttpClient client = new ProductHttpClient(restClient(stub.baseUrl(), READ));

            assertThatThrownBy(() -> client.findListed(List.of(7L))).isInstanceOf(DownstreamException.class);
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
