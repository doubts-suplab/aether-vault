package com.suplab.aether.vault.engine.ingestion;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.engine.embedding.KnowledgeEmbeddingService;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.DocumentIngestionPort;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.TokenCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link DocumentIngestionPort} — the document-indexing pipeline.
 *
 * <p>Ingestion is idempotent per document:</p>
 * <ol>
 *   <li>Existing chunks for the document are deleted first, so re-indexing a changed source never
 *       leaves stale chunks behind.</li>
 *   <li>The raw text is split by {@link TextChunker} into ordered, overlapping chunks.</li>
 *   <li>Each chunk is embedded via {@link KnowledgeEmbeddingService} and persisted through
 *       {@link DocumentChunkStore}. When embedding is disabled the chunk is stored with a
 *       zero vector — it is retained but not semantically retrievable, consistent with the rest
 *       of the ecosystem.</li>
 *   <li>The document is transitioned to {@code INDEXED} (or {@code FAILED} when the source
 *       produced no usable chunks) and saved.</li>
 * </ol>
 *
 * <p>The token count stored per chunk comes from a pluggable {@link TokenCounter} — a real
 * tokenizer, not a {@code chars / 4} guess — so downstream context budgeting is accurate.</p>
 */
public class DefaultDocumentIngestionService implements DocumentIngestionPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentIngestionService.class);
    private static final int EMBEDDING_DIM = 384;

    private final KnowledgeDocumentStore documentStore;
    private final DocumentChunkStore chunkStore;
    private final Optional<KnowledgeEmbeddingService> embeddingService;
    private final TextChunker chunker;
    private final TokenCounter tokenCounter;

    public DefaultDocumentIngestionService(KnowledgeDocumentStore documentStore,
                                           DocumentChunkStore chunkStore,
                                           Optional<KnowledgeEmbeddingService> embeddingService,
                                           TextChunker chunker,
                                           TokenCounter tokenCounter) {
        this.documentStore = documentStore;
        this.chunkStore = chunkStore;
        this.embeddingService = embeddingService;
        this.chunker = chunker;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public IngestionResult ingest(KnowledgeDocument document, String rawText) {
        var scope = document.scope();
        // Replace any prior chunks first — re-ingestion must not accumulate stale chunks.
        chunkStore.deleteByDocument(scope, document.id());

        List<String> pieces = chunker.chunk(rawText);
        if (pieces.isEmpty()) {
            var failed = document.markFailed();
            documentStore.save(failed);
            log.warn("Ingestion produced no chunks for documentId={} tenantId={} collectionId={} — marked FAILED",
                    document.id(), document.tenantId(), document.collectionId());
            return new IngestionResult(failed.id(), 0, failed.status());
        }

        for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
            var content = pieces.get(ordinal);
            int tokenCount = Math.max(1, tokenCounter.countTokens(content));
            var chunk = DocumentChunk.create(document, ordinal, content, tokenCount);
            var embedding = embeddingService.map(svc -> svc.embed(content)).orElseGet(() -> new float[EMBEDDING_DIM]);
            chunkStore.save(chunk, embedding);
        }

        var indexed = document.markIndexed(pieces.size());
        documentStore.save(indexed);
        log.info("Indexed documentId={} tenantId={} collectionId={} chunks={} embeddingEnabled={}",
                indexed.id(), indexed.tenantId(), indexed.collectionId(), pieces.size(),
                embeddingService.isPresent());
        return new IngestionResult(indexed.id(), pieces.size(), indexed.status());
    }
}
