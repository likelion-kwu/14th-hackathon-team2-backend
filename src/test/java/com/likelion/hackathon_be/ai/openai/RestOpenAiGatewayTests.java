package com.likelion.hackathon_be.ai.openai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestOpenAiGatewayTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesOne429ThenParsesStructuredOutput() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                respond(exchange, 429, "{}");
            } else {
                respond(exchange, 200, success("{\\\"matched\\\":true}"));
            }
        });
        RestOpenAiGateway gateway = gateway(Duration.ofSeconds(2));

        JsonNode result = gateway.structuredResponse(
                "test_schema", "test-v1", "instructions", "input", List.of(),
                new ObjectMapper().readTree("{\"type\":\"object\"}"), 50
        );

        assertThat(result.path("matched").booleanValue()).isTrue();
        assertThat(calls).hasValue(2);
    }

    @Test
    void retriesOneServerErrorThenReportsUnavailable() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "{}");
        });
        RestOpenAiGateway gateway = gateway(Duration.ofSeconds(2));

        assertThatThrownBy(() -> gateway.structuredResponse(
                "test_schema", "test-v1", "instructions", "input", List.of(),
                new ObjectMapper().readTree("{\"type\":\"object\"}"), 50
        )).isInstanceOfSatisfying(OpenAiGatewayException.class, exception ->
                assertThat(exception.kind()).isEqualTo(OpenAiGatewayException.Kind.UNAVAILABLE));
        assertThat(calls).hasValue(2);
    }

    @Test
    void retriesOneTimeoutThenReportsUnavailable() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(150);
                respond(exchange, 200, success("{\\\"ok\\\":true}"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                exchange.close();
            }
        });
        RestOpenAiGateway gateway = gateway(Duration.ofMillis(30));

        assertThatThrownBy(() -> gateway.structuredResponse(
                "test_schema", "test-v1", "instructions", "input", List.of(),
                new ObjectMapper().readTree("{\"type\":\"object\"}"), 50
        )).isInstanceOfSatisfying(OpenAiGatewayException.class, exception ->
                assertThat(exception.kind()).isEqualTo(OpenAiGatewayException.Kind.UNAVAILABLE));
        assertThat(calls.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void identifiesRefusalWithoutExposingItAsJson() throws Exception {
        server = server(exchange -> respond(exchange, 200, """
                {"output":[{"content":[{"type":"refusal","refusal":"cannot comply"}]}]}
                """));
        RestOpenAiGateway gateway = gateway(Duration.ofSeconds(2));

        assertThatThrownBy(() -> gateway.structuredResponse(
                "test_schema", "test-v1", "instructions", "input", List.of(),
                new ObjectMapper().readTree("{\"type\":\"object\"}"), 50
        )).isInstanceOfSatisfying(OpenAiGatewayException.class, exception ->
                assertThat(exception.kind()).isEqualTo(OpenAiGatewayException.Kind.REFUSED));
    }

    private RestOpenAiGateway gateway(Duration responseTimeout) {
        OpenAiProperties properties = new OpenAiProperties(
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-model",
                "test-image-model",
                Duration.ofSeconds(1),
                responseTimeout,
                responseTimeout
        );
        return new RestOpenAiGateway(properties, new ObjectMapper());
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v1/responses", exchange -> handler.handle(exchange));
        httpServer.start();
        return httpServer;
    }

    private String success(String escapedJson) {
        return "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\""
                + escapedJson
                + "\"}]}],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("x-request-id", "req_test");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
