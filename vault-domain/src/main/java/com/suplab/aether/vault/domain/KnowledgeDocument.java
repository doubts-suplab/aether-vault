package com.suplab.aether.vault.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A source document registered in a knowledge collection within a tenant.
 *
 * <p>A {@code KnowledgeDocument} is the unit of ingestion: it is chunked, each chunk is embedded
 * (384-dim) and stored for semantic retrieval, and the whole is tracked through an indexing
 * {@link DocumentStatus} lifecycle. The {@code checksum} of the source content is the basis for
 * <em>knowledge freshness</em> — when a re-ingested source produces a different checksum the
 * document is {@link #isStale(String) stale} and must be re-indexed.</p>
 *
 * <p>All fields are immutable; state transitions ({@link #markIndexed(int)},
 * {@link #markStale()}, {@link #markFailed()}) return new instances.</p>
 *
 * @param id           stable identifier
 * @param tenantId     owning tenant (isolation boundary)
 * @param collectionId owning knowledge collection within the tenant
 * @param sourceUri    where the document came from (file path, URL, connector ref)
 * @param title        human-readable title
 * @param contentType  MIME type of the source ({@code text/plain}, {@code text/markdown}, …)
 * @param checksum     content fingerprint used for change detection / freshness
 * @param status       indexing lifecycle state
 * @param chunkCount   number of chunks produced by the last successful indexing (0 until indexed)
 * @param createdAt    when the document was first registered
 * @param indexedAt    when the document was last successfully indexed ({@code null} until then)
 * @param updatedAt    when the document record last changed
 */
public record KnowledgeDocument(
        UUID id,
        String tenantId,
        String collectionId,
        String sourceUri,
        String title,
        String contentType,
        String checksum,
        DocumentStatus status,
        int chunkCount,
        Instant createdAt,
        Instant indexedAt,
        Instant updatedAt
) {
    public KnowledgeDocument {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
        if (sourceUri == null || sourceUri.isBlank()) throw new IllegalArgumentException("sourceUri required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (contentType == null || contentType.isBlank()) contentType = "text/plain";
        if (checksum == null || checksum.isBlank()) throw new IllegalArgumentException("checksum required");
        if (status == null) status = DocumentStatus.PENDING;
        if (chunkCount < 0) throw new IllegalArgumentException("chunkCount must be >= 0");
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    /**
     * Factory for a newly registered document: random ID, {@code PENDING} status, no chunks yet.
     */
    public static KnowledgeDocument create(KnowledgeScope scope, String sourceUri, String title,
                                           String contentType, String checksum) {
        var now = Instant.now();
        return new KnowledgeDocument(UUID.randomUUID(), scope.tenantId(), scope.collectionId(), sourceUri,
                title, contentType, checksum, DocumentStatus.PENDING, 0, now, null, now);
    }

    /**
     * Returns the owning scope ({@code tenantId} + {@code collectionId}) of this document.
     */
    public KnowledgeScope scope() {
        return new KnowledgeScope(tenantId, collectionId);
    }

    /**
     * Returns an indexed copy: status {@code INDEXED}, {@code chunkCount} set to the number of
     * chunks produced, {@code indexedAt} and {@code updatedAt} refreshed to now.
     *
     * @param producedChunks number of chunks the ingestion produced (must be >= 0)
     */
    public KnowledgeDocument markIndexed(int producedChunks) {
        if (producedChunks < 0) throw new IllegalArgumentException("producedChunks must be >= 0");
        var now = Instant.now();
        return new KnowledgeDocument(id, tenantId, collectionId, sourceUri, title, contentType, checksum,
                DocumentStatus.INDEXED, producedChunks, createdAt, now, now);
    }

    /**
     * Returns a {@code PENDING} copy re-registered from a freshly fetched source: same identity
     * ({@code id}, {@code scope}, {@code sourceUri}, {@code createdAt}) but a new {@code checksum},
     * refreshed {@code title}/{@code contentType}, {@code chunkCount} reset to 0, and {@code updatedAt}
     * set to now.
     *
     * <p>This is how a source connector re-indexes a changed source without minting a new document —
     * one {@code sourceUri} keeps one stable document across re-ingestions. The prior chunks are
     * replaced by the ingest pipeline, not here.</p>
     *
     * @param newChecksum    the checksum of the newly fetched content (must be present)
     * @param newTitle       the title from the newly fetched content
     * @param newContentType the content type from the newly fetched content
     * @return a PENDING copy ready to be re-indexed
     */
    public KnowledgeDocument reregister(String newChecksum, String newTitle, String newContentType) {
        if (newChecksum == null || newChecksum.isBlank())
            throw new IllegalArgumentException("newChecksum required");
        return new KnowledgeDocument(id, tenantId, collectionId, sourceUri, newTitle, newContentType,
                newChecksum, DocumentStatus.PENDING, 0, createdAt, indexedAt, Instant.now());
    }

    /**
     * Returns a copy marked {@code STALE} — the source has drifted or the re-index interval has
     * elapsed. Existing chunks stay searchable until re-indexing replaces them.
     */
    public KnowledgeDocument markStale() {
        return new KnowledgeDocument(id, tenantId, collectionId, sourceUri, title, contentType, checksum,
                DocumentStatus.STALE, chunkCount, createdAt, indexedAt, Instant.now());
    }

    /**
     * Returns a copy marked {@code FAILED} — ingestion could not produce usable chunks.
     */
    public KnowledgeDocument markFailed() {
        return new KnowledgeDocument(id, tenantId, collectionId, sourceUri, title, contentType, checksum,
                DocumentStatus.FAILED, chunkCount, createdAt, indexedAt, Instant.now());
    }

    /**
     * Returns whether a freshly computed source checksum differs from this document's — i.e. the
     * source content has changed since it was indexed and the document should be re-ingested.
     *
     * @param currentChecksum the checksum computed from the source's current content
     * @return {@code true} if the source has changed
     */
    public boolean isStale(String currentChecksum) {
        return currentChecksum != null && !currentChecksum.equals(checksum);
    }
}
