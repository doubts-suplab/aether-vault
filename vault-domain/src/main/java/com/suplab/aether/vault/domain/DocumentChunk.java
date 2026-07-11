package com.suplab.aether.vault.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A contiguous slice of a {@link KnowledgeDocument}, the unit of embedding and vector search.
 *
 * <p>Documents are split into ordered chunks so that retrieval returns focused, prompt-sized
 * passages rather than whole files. Each chunk carries its {@code ordinal} (0-based position
 * within the document) and a copy of the owning {@code tenantId} + {@code collectionId} so that
 * every chunk query stays inside the multi-tenancy boundary without a join back to the document
 * table.</p>
 *
 * @param id           stable identifier
 * @param documentId   the document this chunk belongs to
 * @param tenantId     owning tenant (denormalised from the document for scoped queries)
 * @param collectionId owning collection (denormalised from the document for scoped queries)
 * @param ordinal      0-based position of this chunk within the document
 * @param content      the chunk text
 * @param tokenCount   approximate token count of the content (0 if unknown)
 * @param createdAt    when the chunk was produced
 */
public record DocumentChunk(
        UUID id,
        UUID documentId,
        String tenantId,
        String collectionId,
        int ordinal,
        String content,
        int tokenCount,
        Instant createdAt
) {
    public DocumentChunk {
        if (id == null) id = UUID.randomUUID();
        if (documentId == null) throw new IllegalArgumentException("documentId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be >= 0");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content required");
        if (tokenCount < 0) throw new IllegalArgumentException("tokenCount must be >= 0");
        if (createdAt == null) createdAt = Instant.now();
    }

    /**
     * Factory for a chunk belonging to a given document. Inherits the document's scope and
     * assigns a random ID.
     *
     * @param document   the owning document
     * @param ordinal    0-based position within the document
     * @param content    the chunk text
     * @param tokenCount approximate token count
     */
    public static DocumentChunk create(KnowledgeDocument document, int ordinal, String content, int tokenCount) {
        return new DocumentChunk(UUID.randomUUID(), document.id(), document.tenantId(), document.collectionId(),
                ordinal, content, tokenCount, Instant.now());
    }

    /**
     * Returns the owning scope ({@code tenantId} + {@code collectionId}) of this chunk.
     */
    public KnowledgeScope scope() {
        return new KnowledgeScope(tenantId, collectionId);
    }
}
