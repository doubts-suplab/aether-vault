package com.suplab.aether.vault.domain;

/**
 * The outcome of erasing a knowledge collection — Vault's contribution to the ecosystem's
 * right-to-erasure (GDPR Art. 17).
 *
 * <p>Erasure removes everything Vault derived from a collection's sources: the documents, their
 * embedded chunks, and the knowledge-graph entities (and, by cascade, their relations). The counts
 * report what was actually removed so the deletion is auditable. A collection with nothing stored
 * returns zero counts — the operation is idempotent.</p>
 *
 * @param tenantId       owning tenant (isolation boundary)
 * @param collectionId   the erased collection
 * @param documentsErased number of document records removed
 * @param chunksErased    number of embedded chunks removed
 * @param entitiesErased  number of knowledge-graph entities removed (relations cascade)
 */
public record KnowledgeErasureResult(
        String tenantId,
        String collectionId,
        int documentsErased,
        int chunksErased,
        int entitiesErased
) {
    public KnowledgeErasureResult {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
        if (documentsErased < 0 || chunksErased < 0 || entitiesErased < 0)
            throw new IllegalArgumentException("erasure counts must be >= 0");
    }

    /** @return {@code true} if the collection held nothing to erase. */
    public boolean isEmpty() {
        return documentsErased == 0 && chunksErased == 0 && entitiesErased == 0;
    }
}
