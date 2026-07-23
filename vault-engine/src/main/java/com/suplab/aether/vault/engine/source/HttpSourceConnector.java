package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.FetchedContent;
import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link DocumentSourceConnector} for {@code http:} and {@code https:} sources.
 *
 * <p>Fetches over a shared {@link HttpClient} with a connect and request timeout, follows normal
 * (non-insecure) redirects, and caps the response body: a {@code Content-Length} over the limit is
 * rejected up front, and the decoded body is re-checked so a chunked response cannot exceed the cap.
 * A non-2xx status is a {@link SourceFetchException}. The title is derived from the last path
 * segment of the URL. The connector performs retrieval only — it does not execute scripts or parse
 * HTML into a DOM; the raw text is handed to the ingest pipeline as-is.</p>
 */
public class HttpSourceConnector implements DocumentSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(HttpSourceConnector.class);

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final long maxBytes;

    /**
     * @param requestTimeout per-request timeout
     * @param maxBytes       maximum response body size in bytes
     */
    public HttpSourceConnector(Duration requestTimeout, long maxBytes) {
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero())
            throw new IllegalArgumentException("requestTimeout must be positive");
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be > 0");
        this.requestTimeout = requestTimeout;
        this.maxBytes = maxBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String scheme() {
        return "http(s)";
    }

    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && (sourceUri.startsWith("http://") || sourceUri.startsWith("https://"));
    }

    @Override
    public FetchedContent fetch(String sourceUri) {
        if (!supports(sourceUri)) {
            throw new SourceFetchException("Unsupported URI for HTTP connector: " + sourceUri);
        }
        URI uri;
        try {
            uri = URI.create(sourceUri);
        } catch (IllegalArgumentException e) {
            throw new SourceFetchException("Malformed URL: " + sourceUri, e);
        }

        var request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "text/plain, text/markdown, text/html, application/json, */*")
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new SourceFetchException("Source returned HTTP " + response.statusCode() + ": " + sourceUri);
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declared > maxBytes) {
                throw new SourceFetchException(
                        "Source exceeds max size (Content-Length " + declared + " > " + maxBytes + "): " + sourceUri);
            }
            byte[] body = response.body();
            if (body.length > maxBytes) {
                throw new SourceFetchException(
                        "Source exceeds max size (" + body.length + " > " + maxBytes + " bytes): " + sourceUri);
            }
            var text = new String(body, StandardCharsets.UTF_8);
            var contentType = response.headers().firstValue("Content-Type")
                    .map(HttpSourceConnector::normaliseContentType).orElse("text/plain");
            log.info("Fetched HTTP source uri={} status={} bytes={}", sourceUri, response.statusCode(), body.length);
            return new FetchedContent(sourceUri, titleFrom(uri), contentType, text);
        } catch (IOException e) {
            throw new SourceFetchException("Failed to fetch HTTP source: " + sourceUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException("Interrupted fetching HTTP source: " + sourceUri, e);
        }
    }

    private static String normaliseContentType(String header) {
        int semi = header.indexOf(';');
        var type = (semi >= 0 ? header.substring(0, semi) : header).trim().toLowerCase();
        return type.isBlank() ? "text/plain" : type;
    }

    private static String titleFrom(URI uri) {
        var path = uri.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            return uri.getHost() != null ? uri.getHost() : uri.toString();
        }
        var segment = path.substring(path.lastIndexOf('/') + 1);
        return segment.isBlank() ? uri.getHost() : segment;
    }
}
