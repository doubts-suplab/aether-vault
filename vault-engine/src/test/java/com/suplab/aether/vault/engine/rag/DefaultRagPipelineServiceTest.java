package com.suplab.aether.vault.engine.rag;

import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievalQuery;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRagPipelineServiceTest {

    /** In-memory chunk store that records the requested topK and returns a fixed corpus. */
    private static final class RecordingChunkStore implements DocumentChunkStore {
        private final List<RetrievedChunk> corpus;
        int lastRequestedTopK = -1;
        KnowledgeScope lastScope;

        RecordingChunkStore(List<RetrievedChunk> corpus) {
            this.corpus = corpus;
        }

        @Override
        public void save(com.suplab.aether.vault.domain.DocumentChunk chunk, float[] embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RetrievedChunk> findSimilar(KnowledgeScope scope, float[] queryEmbedding, int topK) {
            this.lastRequestedTopK = topK;
            this.lastScope = scope;
            return corpus.stream().limit(topK).toList();
        }

        @Override
        public int deleteByDocument(KnowledgeScope scope, UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByCollection(KnowledgeScope scope) {
            throw new UnsupportedOperationException();
        }
    }

    private static RetrievedChunk chunk(String content) {
        return new RetrievedChunk(UUID.randomUUID(), "Doc", "uri", 0, content, 0.2);
    }

    @Test
    void retrieve_returnsAssembledContextFromChunks() {
        var store = new RecordingChunkStore(List.of(chunk("alpha"), chunk("beta")));
        var service = new DefaultRagPipelineService(store, Optional.empty());

        var context = service.retrieve(new RetrievalQuery("tenant-1", "handbook", "question", 5));

        assertThat(context.chunks()).hasSize(2);
        assertThat(context.assembledContext()).contains("alpha").contains("beta");
    }

    @Test
    void retrieve_scopesToQueryTenantAndCollection() {
        var store = new RecordingChunkStore(List.of());
        var service = new DefaultRagPipelineService(store, Optional.empty());

        service.retrieve(new RetrievalQuery("tenant-x", "collection-y", "q", 5));

        assertThat(store.lastScope).isEqualTo(KnowledgeScope.of("tenant-x", "collection-y"));
    }

    @Test
    void retrieve_clampsTopKToMaximum() {
        var store = new RecordingChunkStore(List.of());
        var service = new DefaultRagPipelineService(store, Optional.empty());

        service.retrieve(new RetrievalQuery("tenant-1", "handbook", "q",
                DefaultRagPipelineService.MAX_TOP_K + 100));

        assertThat(store.lastRequestedTopK).isEqualTo(DefaultRagPipelineService.MAX_TOP_K);
    }

    @Test
    void retrieve_returnsEmptyContextWhenNoChunks() {
        var store = new RecordingChunkStore(List.of());
        var service = new DefaultRagPipelineService(store, Optional.empty());

        var context = service.retrieve(new RetrievalQuery("tenant-1", "handbook", "q", 5));

        assertThat(context.chunks()).isEmpty();
        assertThat(context.assembledContext()).isEmpty();
    }
}
