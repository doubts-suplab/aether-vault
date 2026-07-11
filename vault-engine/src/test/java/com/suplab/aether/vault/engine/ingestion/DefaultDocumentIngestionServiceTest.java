package com.suplab.aether.vault.engine.ingestion;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.ports.DocumentChunkStore;
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
        public List<KnowledgeDocument> findByCollection(KnowledgeScope scope, int limit) {
            return List.of();
        }

        @Override
        public long countByCollection(KnowledgeScope scope) {
            return 0;
        }

        @Override
        public void delete(KnowledgeScope scope, UUID documentId) {
        }
    }

    @Test
    void ingest_chunksEmbedsAndMarksIndexed() {
        var chunkStore = new CapturingChunkStore();
        var docStore = new CapturingDocumentStore();
        var service = new DefaultDocumentIngestionService(docStore, chunkStore, Optional.empty(),
                new TextChunker(10, 0));
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
                Optional.empty(), new TextChunker(1000, 0));
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        service.ingest(doc, "content");

        assertThat(chunkStore.deleteCalls).isEqualTo(1);
    }

    @Test
    void ingest_marksFailedWhenNoChunksProduced() {
        var chunkStore = new CapturingChunkStore();
        var docStore = new CapturingDocumentStore();
        var service = new DefaultDocumentIngestionService(docStore, chunkStore, Optional.empty(),
                new TextChunker(10, 0));
        var doc = KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");

        var result = service.ingest(doc, "    "); // blank → no chunks

        assertThat(result.chunkCount()).isZero();
        assertThat(result.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(chunkStore.saved).isEmpty();
        assertThat(docStore.lastSaved.status()).isEqualTo(DocumentStatus.FAILED);
    }
}
