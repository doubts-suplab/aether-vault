package com.suplab.aether.vault.domain;

/**
 * The raw content a {@link com.suplab.aether.vault.ports.DocumentSourceConnector} pulls from a
 * source, before it is registered and indexed.
 *
 * <p>A connector's only job is to turn a {@code sourceUri} into one of these — resolving the title
 * and content type from the source and reading its text. Everything downstream (checksum,
 * registration, chunking, embedding) is the ingestion pipeline's concern, not the connector's.</p>
 *
 * @param sourceUri   the URI the content was fetched from (echoed back for provenance)
 * @param title       a human-readable title derived from the source (e.g. file name, page title)
 * @param contentType MIME type of the content ({@code text/plain}, {@code text/markdown}, …)
 * @param rawText     the full extracted text
 */
public record FetchedContent(String sourceUri, String title, String contentType, String rawText) {

    public FetchedContent {
        if (sourceUri == null || sourceUri.isBlank()) throw new IllegalArgumentException("sourceUri required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (contentType == null || contentType.isBlank()) contentType = "text/plain";
        if (rawText == null) throw new IllegalArgumentException("rawText required");
    }
}
