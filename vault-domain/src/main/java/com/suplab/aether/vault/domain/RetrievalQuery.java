package com.suplab.aether.vault.domain;

/**
 * A request to retrieve the most relevant document chunks for a natural-language query, scoped
 * to a single collection.
 *
 * <p>This is the input to the RAG pipeline. Retrieval never crosses the {@code tenantId} +
 * {@code collectionId} boundary — a query only ever sees knowledge in its own collection.</p>
 *
 * @param tenantId     the tenant issuing the query (isolation boundary)
 * @param collectionId the knowledge collection to search within
 * @param queryText    the natural-language text to embed and match semantically
 * @param topK         maximum number of chunks to retrieve (clamped by the pipeline)
 */
public record RetrievalQuery(
        String tenantId,
        String collectionId,
        String queryText,
        int topK
) {
    public RetrievalQuery {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
        if (queryText == null || queryText.isBlank()) throw new IllegalArgumentException("queryText required");
        if (topK <= 0) topK = 5;
    }

    /**
     * Returns the scope ({@code tenantId} + {@code collectionId}) this query is confined to.
     */
    public KnowledgeScope scope() {
        return new KnowledgeScope(tenantId, collectionId);
    }
}
