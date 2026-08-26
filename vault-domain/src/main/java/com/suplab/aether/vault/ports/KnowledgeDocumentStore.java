package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.DocumentStatus;
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
     * Looks up a document by its source URI within a scope.
     *
     * <p>The connector-driven ingest path uses this to give one {@code sourceUri} a single stable
     * document: re-fetching a source finds its existing record so an unchanged source can be skipped
     * and a changed one re-indexed in place rather than duplicated. A source URI is unique within a
     * collection; the most recently updated match is returned defensively.</p>
     *
     * @param scope     the owning tenant + collection
     * @param sourceUri the source URI to look up
     * @return the document if one exists for this source in this scope, otherwise empty
     */
    Optional<KnowledgeDocument> findBySourceUri(KnowledgeScope scope, String sourceUri);

    /**
     * Lists documents in a collection, most recently updated first.
     *
     * @param scope the owning tenant + collection
     * @param limit maximum number of documents to return
     * @return documents in the collection (may be empty)
     */
    List<KnowledgeDocument> findByCollection(KnowledgeScope scope, int limit);

    /**
     * Lists documents in a collection with a given status, oldest-indexed first. Used by the
     * auto-reingestion sweep to find the {@code STALE} documents a collection has opted to refresh.
     *
     * @param scope  the owning tenant + collection
     * @param status the lifecycle status to filter by
     * @param limit  maximum number of documents to return
     * @return matching documents (may be empty)
     */
    List<KnowledgeDocument> findByStatus(KnowledgeScope scope, DocumentStatus status, int limit);

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

    /**
     * Deletes every document record in a collection — the document half of a right-to-erasure
     * request. Scoped by tenant + collection so there is no cross-collection deletion path. Chunk and
     * graph cleanup are the caller's responsibility.
     *
     * @param scope the owning tenant + collection
     * @return the number of document records deleted
     */
    int deleteByCollection(KnowledgeScope scope);
}
