package com.suplab.aether.vault.engine.ingestion;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.engine.tokenizer.HeuristicTokenCounter;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.GraphExtractionPort;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDocumentIngestionServiceTest {

    private static final KnowledgeScope SCOPE = KnowledgeScope.of("tenant-1", "handbook");

    /** Captures saved chunks and delete calls. */
    private static final class CapturingChunkStore implements DocumentChunkStore {
        final List<DocumentChunk> saved = new ArrayList<>();
        int deleteCalls = 0;

        @Override
        public void save(DocumentChunk chunk, float[] embedding) {
            saved.add(chunk);
        }

        @Override
        public List<RetrievedChunk> findSimilar(KnowledgeScope scope, float[] queryEmbedding, int topK) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByDocument(KnowledgeScope scope, UUID documentId) {
            deleteCalls++;
            return 0;
        }

        @Override
        public long countByCollection(KnowledgeScope scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByCollection(KnowledgeScope scope) {
            throw new UnsupportedOperationException();
        }
    }

    /** Captures the last saved document. */
    private static final class CapturingDocumentStore implements KnowledgeDocumentStore {
        KnowledgeDocument lastSaved;

        @Override
        public void save(KnowledgeDocument document) {
            this.lastSaved = document;
        }

        @Override
        public Optional<KnowledgeDocument> findById(KnowledgeScope scope, UUID documentId) {
            return Optional.empty();
        }

        @Override
        public Optional<KnowledgeDocument> findBySourceUri(KnowledgeScope scope, String sourceUri) {
            return Optional.empty();
        }

        @Override
        public List<KnowledgeDocument> findByCollection(KnowledgeScope scope, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeDocument> findByStatus(KnowledgeScope scope, DocumentStatus status, int limit) {
            return List.of();
        }

        @Override
        public long countByCollection(KnowledgeScope scope) {
            return 0;
        }

        @Override
        public void delete(KnowledgeScope scope, UUID documentId) {
        }

        @Override
        public int deleteByCollection(KnowledgeScope scope) {
            return 0;
        }
    }

    @Test
    void ingest_chunksEmbedsAndMarksIndexed() {
        var chunkStore = new CapturingChunkStore();
        var docStore = new CapturingDocumentStore();
        var service = new DefaultDocumentIngestionService(docStore, chunkStore, Optional.empty(),
                new TextChunker(10, 0), new HeuristicTokenCounter(), Optional.empty());
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        var result = service.ingest(doc, "abcdefghijklmnopqrst"); // 20 chars → 2 chunks

        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(chunkStore.saved).hasSize(2);
        assertThat(chunkStore.saved).extracting(DocumentChunk::ordinal).containsExactly(0, 1);
        assertThat(docStore.lastSaved.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(docStore.lastSaved.chunkCount()).isEqualTo(2);
    }

    @Test
    void ingest_deletesPriorChunksFirst() {
        var chunkStore = new CapturingChunkStore();
        var service = new DefaultDocumentIngestionService(new CapturingDocumentStore(), chunkStore,
                Optional.empty(), new TextChunker(1000, 0), new HeuristicTokenCounter(), Optional.empty());
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        service.ingest(doc, "content");

        assertThat(chunkStore.deleteCalls).isEqualTo(1);
    }

    @Test
    void ingest_invokesGraphExtractionWithIndexedDocumentAndChunks() {
        var chunkStore = new CapturingChunkStore();
        var docStore = new CapturingDocumentStore();
        var extraction = new CapturingGraphExtraction();
        var service = new DefaultDocumentIngestionService(docStore, chunkStore, Optional.empty(),
                new TextChunker(10, 0), new HeuristicTokenCounter(), Optional.of(extraction));
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        service.ingest(doc, "abcdefghijklmnopqrst"); // 2 chunks

        assertThat(extraction.calls).isEqualTo(1);
        assertThat(extraction.lastDocument.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(extraction.lastChunks).hasSize(2);
    }

    @Test
    void ingest_swallowsGraphExtractionFailure() {
        var service = new DefaultDocumentIngestionService(new CapturingDocumentStore(),
                new CapturingChunkStore(), Optional.empty(), new TextChunker(1000, 0),
                new HeuristicTokenCounter(), Optional.of((document, chunks) -> {
                    throw new IllegalStateException("boom");
                }));
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        var result = service.ingest(doc, "content"); // must still succeed

        assertThat(result.status()).isEqualTo(DocumentStatus.INDEXED);
    }

    @Test
    void ingest_skipsGraphExtractionForFailedDocument() {
        var extraction = new CapturingGraphExtraction();
        var service = new DefaultDocumentIngestionService(new CapturingDocumentStore(),
                new CapturingChunkStore(), Optional.empty(), new TextChunker(10, 0),
                new HeuristicTokenCounter(), Optional.of(extraction));
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        service.ingest(doc, "    "); // no chunks → FAILED

        assertThat(extraction.calls).isZero();
    }

    /** Captures graph-extraction invocations. */
    private static final class CapturingGraphExtraction implements GraphExtractionPort {
        int calls = 0;
        KnowledgeDocument lastDocument;
        List<String> lastChunks;

        @Override
        public ExtractionSummary extract(KnowledgeDocument document, List<String> chunks) {
            calls++;
            lastDocument = document;
            lastChunks = chunks;
            return new ExtractionSummary(chunks.size(), 0);
        }
    }

    @Test
    void ingest_marksFailedWhenNoChunksProduced() {
        var chunkStore = new CapturingChunkStore();
        var docStore = new CapturingDocumentStore();
        var service = new DefaultDocumentIngestionService(docStore, chunkStore, Optional.empty(),
                new TextChunker(10, 0), new HeuristicTokenCounter(), Optional.empty());
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        var result = service.ingest(doc, "    "); // blank → no chunks

        assertThat(result.chunkCount()).isZero();
        assertThat(result.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(chunkStore.saved).isEmpty();
        assertThat(docStore.lastSaved.status()).isEqualTo(DocumentStatus.FAILED);
    }
}
