package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.SourceFetchException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpSourceConnectorTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void route(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    @Test
    void fetchesABodyOverHttp() {
        route("/policy.txt", 200, "text/plain; charset=utf-8",
                "expense limit is 500".getBytes(StandardCharsets.UTF_8));
        var connector = new HttpSourceConnector(Duration.ofSeconds(5), 1_000_000);

        var fetched = connector.fetch(base + "/policy.txt");

        assertThat(fetched.rawText()).isEqualTo("expense limit is 500");
        assertThat(fetched.title()).isEqualTo("policy.txt");
        assertThat(fetched.contentType()).isEqualTo("text/plain");
        assertThat(connector.supports(base + "/policy.txt")).isTrue();
        assertThat(connector.supports("file:x")).isFalse();
    }

    @Test
    void rejectsNon2xx() {
        route("/missing", 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
        var connector = new HttpSourceConnector(Duration.ofSeconds(5), 1_000_000);

        assertThatThrownBy(() -> connector.fetch(base + "/missing"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void rejectsBodyOverTheSizeCap() {
        route("/big", 200, "text/plain", "0123456789".getBytes(StandardCharsets.UTF_8));
        var connector = new HttpSourceConnector(Duration.ofSeconds(5), 4);

        assertThatThrownBy(() -> connector.fetch(base + "/big"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("max size");
    }

    @Test
    void rejectsUnsupportedScheme() {
        var connector = new HttpSourceConnector(Duration.ofSeconds(5), 1_000_000);

        assertThatThrownBy(() -> connector.fetch("file:/etc/hosts"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("Unsupported");
    }
}
