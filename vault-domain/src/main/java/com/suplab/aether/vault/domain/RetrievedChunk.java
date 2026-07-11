package com.suplab.aether.vault.domain;

import java.util.UUID;

/**
 * A single chunk returned by a vector-similarity retrieval, annotated with its source document
 * and relevance.
 *
 * <p>This is a read projection assembled from a {@link DocumentChunk} joined to its
 * {@link KnowledgeDocument}, so a RAG caller gets the passage together with enough provenance
 * ({@code documentTitle}, {@code sourceUri}) to cite it. The {@code score} is the pgvector cosine
 * <em>distance</em> — smaller means more relevant — so results ordered by ascending score are
 * ordered nearest-first.</p>
 *
 * @param documentId    the source document's id
 * @param documentTitle the source document's title (for citation)
 * @param sourceUri     the source document's origin (for citation)
 * @param ordinal       the chunk's position within its document
 * @param content       the chunk text
 * @param score         cosine distance to the query embedding (lower is more relevant)
 */
public record RetrievedChunk(
        UUID documentId,
        String documentTitle,
        String sourceUri,
        int ordinal,
        String content,
        double score
) {
    public RetrievedChunk {
        if (documentId == null) throw new IllegalArgumentException("documentId required");
        if (documentTitle == null) documentTitle = "";
        if (sourceUri == null) sourceUri = "";
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be >= 0");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content required");
    }
}
