package com.flashsale.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Downstream stub: echoes the received path and X-User-Id as JSON and counts hits. */
class EchoServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile long delayMillis;

    EchoServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        hits.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        String json = "{\"path\":\"" + exchange.getRequestURI().getPath() + "\",\"userId\":"
            + (userId == null ? "null" : "\"" + userId + "\"") + "}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int hits() {
        return hits.get();
    }

    void reset() {
        hits.set(0);
        delayMillis = 0;
    }

    void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
