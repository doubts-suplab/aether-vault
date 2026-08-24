package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievedChunk;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for chunk persistence and vector search.
 *
 * <p>Chunks carry the 384-dimension embeddings that power semantic retrieval. The
 * {@link #findSimilar} method is the vector-search primitive underpinning the RAG pipeline; it
 * returns {@link RetrievedChunk} read projections (chunk joined to its document for citation)
 * ordered nearest-first. Every operation is scoped by {@link KnowledgeScope}.</p>
 */
public interface DocumentChunkStore {

    /**
     * Persists a chunk alongside its embedding. UPSERT on the chunk ID.
     *
     * @param chunk     the chunk to persist
     * @param embedding the 384-dimension embedding vector for semantic search
     */
    void save(DocumentChunk chunk, float[] embedding);

    /**
     * Returns the {@code topK} chunks in a collection most semantically similar to the query
     * embedding, ordered by cosine distance ascending (nearest first), each joined to its source
     * document for citation.
     *
     * @param scope          the owning tenant + collection (isolation boundary)
     * @param queryEmbedding the 384-dimension query vector
     * @param topK           maximum number of chunks to return
     * @return ordered list of retrieved chunks (nearest first); never {@code null}
     */
    List<RetrievedChunk> findSimilar(KnowledgeScope scope, float[] queryEmbedding, int topK);

    /**
     * Deletes all chunks belonging to a document. Used before re-indexing so a re-ingested
     * document does not accumulate stale chunks. Scope enforces isolation.
     *
     * @param scope      the owning tenant + collection
     * @param documentId the document whose chunks to delete
     * @return the number of chunks deleted
     */
    int deleteByDocument(KnowledgeScope scope, UUID documentId);

    /**
     * Returns the total number of chunks stored for a collection.
     *
     * @param scope the owning tenant + collection
     * @return non-negative chunk count
     */
    long countByCollection(KnowledgeScope scope);

    /**
     * Deletes every chunk in a collection — the chunk half of a right-to-erasure request. Scoped by
     * tenant + collection so there is no cross-collection deletion path.
     *
     * @param scope the owning tenant + collection
     * @return the number of chunks deleted
     */
    int deleteByCollection(KnowledgeScope scope);
}
