package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for document-metadata persistence.
 *
 * <p>Stores the {@link KnowledgeDocument} record — not its chunks (those live behind
 * {@link DocumentChunkStore}). Every read and write is scoped by {@link KnowledgeScope} so there
 * is no cross-tenant or cross-collection access path. Implementations live in
 * {@code vault-engine}.</p>
 */
public interface KnowledgeDocumentStore {

    /**
     * Persists a document. Uses UPSERT semantics — calling with an existing ID updates the record
     * (status, chunk count, timestamps).
     *
     * @param document the document to persist
     */
    void save(KnowledgeDocument document);

    /**
     * Looks up a single document by ID within a scope.
     *
     * @param scope      the owning tenant + collection
     * @param documentId the document's ID
     * @return the document if present in this scope, otherwise empty
     */
    Optional<KnowledgeDocument> findById(KnowledgeScope scope, UUID documentId);

    /**
     * Lists documents in a collection, most recently updated first.
     *
     * @param scope the owning tenant + collection
     * @param limit maximum number of documents to return
     * @return documents in the collection (may be empty)
     */
    List<KnowledgeDocument> findByCollection(KnowledgeScope scope, int limit);

    /**
     * Returns the number of documents registered in a collection.
     *
     * @param scope the owning tenant + collection
     * @return non-negative document count
     */
    long countByCollection(KnowledgeScope scope);

    /**
     * Deletes a document. Scope is required to prevent cross-collection deletion. Chunk cleanup is
     * the caller's responsibility (see {@link DocumentChunkStore#deleteByDocument}).
     *
     * @param scope      the owning tenant + collection
     * @param documentId the document to delete
     */
    void delete(KnowledgeScope scope, UUID documentId);
}
