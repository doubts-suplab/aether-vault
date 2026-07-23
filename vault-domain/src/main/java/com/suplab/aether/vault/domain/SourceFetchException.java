package com.suplab.aether.vault.domain;

/**
 * Raised when a source cannot be fetched into a {@link FetchedContent} — an unsupported URI scheme,
 * a source outside the allowed boundary, a transport failure, or content that exceeds the connector's
 * size limit.
 *
 * <p>It is a domain-level signal, distinct from a low-level {@code IOException}, so the ingest path
 * can map a fetch failure to a clean API response without leaking connector internals.</p>
 */
public class SourceFetchException extends RuntimeException {

    public SourceFetchException(String message) {
        super(message);
    }

    public SourceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
