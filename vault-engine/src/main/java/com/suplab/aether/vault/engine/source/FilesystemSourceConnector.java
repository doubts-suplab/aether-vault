package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.FetchedContent;
import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link DocumentSourceConnector} for {@code file:} sources, confined to a configured allowed root.
 *
 * <p>Every fetched path must resolve to a location <em>inside</em> {@code allowedRoot}: a
 * {@code file:} URI pointing outside it — or escaping via {@code ..} — is rejected with a
 * {@link SourceFetchException} before any read. This makes the connector a hard boundary: Vault can
 * only ingest from the directory tree an operator explicitly opened to it, never arbitrary host
 * paths. A size cap guards against pulling an unexpectedly huge file into memory.</p>
 */
public class FilesystemSourceConnector implements DocumentSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(FilesystemSourceConnector.class);
    private static final String PREFIX = "file:";

    private final Path allowedRoot;
    private final long maxBytes;

    /**
     * @param allowedRoot the only directory tree this connector may read from
     * @param maxBytes    maximum file size in bytes; larger files are rejected
     */
    public FilesystemSourceConnector(Path allowedRoot, long maxBytes) {
        if (allowedRoot == null) throw new IllegalArgumentException("allowedRoot required");
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be > 0");
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Override
    public String scheme() {
        return "file";
    }

    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && sourceUri.startsWith(PREFIX);
    }

    @Override
    public FetchedContent fetch(String sourceUri) {
        if (!supports(sourceUri)) {
            throw new SourceFetchException("Unsupported URI for filesystem connector: " + sourceUri);
        }
        var resolved = resolveWithinRoot(sourceUri);

        try {
            if (!Files.isRegularFile(resolved)) {
                throw new SourceFetchException("Not a readable file: " + sourceUri);
            }
            long size = Files.size(resolved);
            if (size > maxBytes) {
                throw new SourceFetchException(
                        "Source exceeds max size (" + size + " > " + maxBytes + " bytes): " + sourceUri);
            }
            var text = Files.readString(resolved, StandardCharsets.UTF_8);
            var fileName = resolved.getFileName().toString();
            log.info("Fetched file source uri={} bytes={} allowedRoot={}", sourceUri, size, allowedRoot);
            return new FetchedContent(sourceUri, fileName, contentTypeFor(fileName), text);
        } catch (IOException e) {
            throw new SourceFetchException("Failed to read file source: " + sourceUri, e);
        }
    }

    /**
     * Resolves the path portion of a {@code file:} URI against the allowed root and verifies the
     * result stays inside it — rejecting absolute paths outside the root and {@code ..} escapes.
     */
    private Path resolveWithinRoot(String sourceUri) {
        var pathPart = sourceUri.substring(PREFIX.length());
        // Normalise file://host/path and file:///path forms down to the path.
        while (pathPart.startsWith("//")) pathPart = pathPart.substring(1);
        if (pathPart.isBlank()) throw new SourceFetchException("Empty file path: " + sourceUri);

        var candidate = allowedRoot.resolve(pathPart).toAbsolutePath().normalize();
        if (!candidate.startsWith(allowedRoot)) {
            log.warn("Rejected file source outside allowed root uri={} resolved={} allowedRoot={}",
                    sourceUri, candidate, allowedRoot);
            throw new SourceFetchException("Source resolves outside the allowed root: " + sourceUri);
        }
        return candidate;
    }

    private static String contentTypeFor(String fileName) {
        var lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".json")) return "application/json";
        return "text/plain";
    }
}
