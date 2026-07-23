package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeScope;

import java.util.UUID;

/**
 * Port for ingesting a document straight from a source URI, rather than from inline text.
 *
 * <p>This is the connector-driven ingest path: resolve a {@link DocumentSourceConnector} for the
 * URI, fetch the content, checksum it, and — only if the source is new or has changed — register
 * (or re-register) the {@link com.suplab.aether.vault.domain.KnowledgeDocument} and run it through
 * the {@link DocumentIngestionPort}. An unchanged source is a no-op: it is not re-embedded.</p>
 *
 * <p>Freshness is checksum-driven here: re-pointing at the same {@code sourceUri} reuses the
 * existing document's identity, so a source has one stable document across re-ingestions.</p>
 */
public interface SourceIngestionPort {

    /**
     * What the source ingest did.
     *
     * <ul>
     *   <li>{@code INDEXED} — the source was new or changed and was (re-)indexed.</li>
     *   <li>{@code UNCHANGED} — the source's checksum matched an already-{@code INDEXED} document;
     *       nothing was re-embedded.</li>
     *   <li>{@code FAILED} — the fetch succeeded but ingestion produced no usable chunks.</li>
     * </ul>
     */
    enum Outcome { INDEXED, UNCHANGED, FAILED }

    /**
     * Outcome of ingesting one source.
     *
     * @param documentId the document's stable ID (reused across re-ingestions of the same source)
     * @param status     the document's status after the operation
     * @param chunkCount the document's current chunk count
     * @param outcome    what the operation did
     */
    record SourceIngestionResult(UUID documentId, DocumentStatus status, int chunkCount, Outcome outcome) {}

    /**
     * Fetches the content at {@code sourceUri} and indexes it into the given collection, skipping
     * re-embedding when the source is unchanged since it was last indexed.
     *
     * @param scope     the owning tenant + collection
     * @param sourceUri the source to fetch and index
     * @return the ingestion result
     * @throws com.suplab.aether.vault.domain.SourceFetchException if no connector supports the URI
     *                                                             or the fetch fails
     */
    SourceIngestionResult ingestFromSource(KnowledgeScope scope, String sourceUri);
}
