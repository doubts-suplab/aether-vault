package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeDocument;

import java.util.UUID;

/**
 * Port interface for document indexing — the ingest pipeline that turns raw source text into
 * searchable, embedded chunks.
 *
 * <p>Ingestion is idempotent per document: re-ingesting replaces the document's existing chunks
 * so re-indexing a changed source never leaves stale chunks behind. Implementations chunk the
 * text, embed each chunk (via the embedding service), persist the chunks, and transition the
 * document to {@link DocumentStatus#INDEXED} (or {@link DocumentStatus#FAILED}).</p>
 */
public interface DocumentIngestionPort {

    /**
     * Outcome of ingesting one document.
     *
     * @param documentId the ingested document's ID
     * @param chunkCount the number of chunks produced
     * @param status     the document's status after ingestion
     */
    record IngestionResult(UUID documentId, int chunkCount, DocumentStatus status) {}

    /**
     * Indexes a document: chunk {@code rawText}, embed each chunk, replace the document's chunks,
     * and mark the document indexed.
     *
     * @param document the document metadata (already scoped and checksummed)
     * @param rawText  the full source text to chunk and embed
     * @return the ingestion result (document ID, chunk count, resulting status)
     */
    IngestionResult ingest(KnowledgeDocument document, String rawText);
}
