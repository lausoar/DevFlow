package com.devflow.service;

import com.devflow.entity.Document;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiKnowledgeSyncServiceTest {

    private HttpServer server;
    private AiKnowledgeSyncService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        service = new AiKnowledgeSyncService(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", "secret-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void synchronousIndexPropagatesAiFailureToOutbox() {
        server.createContext("/api/v1/rag/index", exchange -> respond(exchange, 503, "unavailable"));

        assertThatThrownBy(() -> service.indexDocumentSync(document("body")))
                .isInstanceOf(RestClientResponseException.class)
                .hasMessageContaining("503");
    }

    @Test
    void blankIndexUsesScopedDeleteAndPreservesAsyncCompatibility() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/api/v1/rag/index/d1", exchange -> {
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getQuery());
            token.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            respond(exchange, "DELETE".equals(exchange.getRequestMethod()) ? 204 : 405, "");
        });

        service.indexDocumentSync(document(" \n"));

        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(query.get()).contains("project_id=p1", "enterprise_id=e1");
        assertThat(token.get()).isEqualTo("secret-token");
        assertThat(AiKnowledgeSyncService.class.getMethod("indexDocument", Document.class)
                .getAnnotation(Async.class)).isNotNull();
        assertThat(AiKnowledgeSyncService.class.getMethod("deleteDocument", String.class, String.class, String.class)
                .getAnnotation(Async.class)).isNotNull();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) {
                exchange.getResponseBody().write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static Document document(String content) {
        Document document = new Document();
        document.setId("d1");
        document.setTitle("Design");
        document.setContent(content);
        document.setType("DOC");
        document.setProjectId("p1");
        document.setEnterpriseId("e1");
        return document;
    }
}
