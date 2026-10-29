package com.flashsale.order.client;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal real HTTP server on a random port with per-path canned responses. */
class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Map<String, String> lastBodies = new ConcurrentHashMap<>();

    StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.start();
    }

    StubHttpServer respond(String path, int status, String json) {
        return respond(path, status, json, 0);
    }

    StubHttpServer respond(String path, int status, String json, long delayMs) {
        server.createContext(path, exchange -> {
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            lastBodies.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // client gave up (read timeout)
            } finally {
                exchange.close();
            }
        });
        return this;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int hits(String path) {
        AtomicInteger c = hits.get(path);
        return c == null ? 0 : c.get();
    }

    String lastBody(String path) {
        return lastBodies.get(path);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
