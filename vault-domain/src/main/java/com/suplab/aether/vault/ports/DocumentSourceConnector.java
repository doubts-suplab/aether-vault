package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.FetchedContent;
import com.suplab.aether.vault.domain.SourceFetchException;

/**
 * Port for pulling document content from an external source into the ingest pipeline.
 *
 * <p>Each connector handles one URI scheme (a file path, an HTTP URL, an object-store key). A
 * connector resolves a {@code sourceUri} to a {@link FetchedContent} — reading the text and deriving
 * a title and content type — and does nothing else: checksum, registration, chunking, and embedding
 * all happen downstream. Connectors are selected by scheme through a registry, which is
 * <em>default-deny</em>: a URI no connector {@link #supports supports} is never fetched.</p>
 *
 * <p>Implementations live in {@code vault-engine} and must enforce their own safety limits (allowed
 * roots for the filesystem, size caps and timeouts for the network) — a connector is a trust
 * boundary between Vault and the outside world.</p>
 */
public interface DocumentSourceConnector {

    /**
     * The URI scheme this connector handles, lowercase and without the {@code ://} (e.g.
     * {@code "file"}, {@code "https"}). Used for logging and diagnostics.
     *
     * @return the scheme name
     */
    String scheme();

    /**
     * Whether this connector can fetch the given URI. Selection is by scheme; a connector must
     * return {@code false} for anything it does not handle so the registry can fall through.
     *
     * @param sourceUri the URI to test
     * @return {@code true} if {@link #fetch} can be called with this URI
     */
    boolean supports(String sourceUri);

    /**
     * Fetches the content at {@code sourceUri}.
     *
     * @param sourceUri the URI to fetch (must be one this connector {@link #supports})
     * @return the fetched content
     * @throws SourceFetchException if the URI is unsupported, outside the allowed boundary, too
     *                              large, or cannot be read
     */
    FetchedContent fetch(String sourceUri);
}
