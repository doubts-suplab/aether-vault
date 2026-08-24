package com.suplab.aether.vault.engine.rag;

import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RelevantEntity;
import com.suplab.aether.vault.domain.RetrievalQuery;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.engine.graph.HeuristicEntityExtractor;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        @Override
        public int deleteByCollection(KnowledgeScope scope) {
            throw new UnsupportedOperationException();
        }
    }

    private static RetrievedChunk chunk(String content) {
        return new RetrievedChunk(UUID.randomUUID(), "Doc", "uri", 0, content, 0.2);
    }

    /** In-memory graph store: resolves entities by (name,type) and returns fixed neighbours. */
    private static class FakeGraphStore implements KnowledgeGraphStore {
        private final Map<String, KnowledgeEntity> byKey = new HashMap<>();
        private final Map<UUID, List<KnowledgeEntity>> edges = new HashMap<>();

        KnowledgeEntity put(KnowledgeScope scope, String name, EntityType type, int mentions) {
            var entity = new KnowledgeEntity(UUID.randomUUID(), scope.tenantId(), scope.collectionId(),
                    name, type, mentions, java.time.Instant.now());
            byKey.put(name.toLowerCase() + '|' + type, entity);
            return entity;
        }

        void link(UUID from, KnowledgeEntity neighbour) {
            edges.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(neighbour);
        }

        @Override public KnowledgeEntity upsertEntity(KnowledgeEntity entity) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<KnowledgeEntity> findByName(KnowledgeScope scope, String name,
                                                              EntityType type) {
            return Optional.ofNullable(byKey.get(name.toLowerCase() + '|' + type));
        }
        @Override public List<KnowledgeEntity> findEntities(KnowledgeScope scope, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public void relate(KnowledgeScope scope, EntityRelation relation) {
            throw new UnsupportedOperationException();
        }
        @Override public List<KnowledgeEntity> neighbours(KnowledgeScope scope, UUID entityId, int limit) {
            return edges.getOrDefault(entityId, List.of()).stream().limit(limit).toList();
        }
        @Override public int deleteByCollection(KnowledgeScope scope) {
            throw new UnsupportedOperationException();
        }
    }

    private static DefaultRagPipelineService graphAware(DocumentChunkStore store, KnowledgeGraphStore graph) {
        return new DefaultRagPipelineService(store, Optional.empty(),
                new HeuristicEntityExtractor(), graph);
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

    @Test
    void retrieveWithGraph_graphlessPipeline_returnsEmptyGraph() {
        var store = new RecordingChunkStore(List.of(chunk("alpha")));
        var service = new DefaultRagPipelineService(store, Optional.empty());

        var result = service.retrieveWithGraph(
                new RetrievalQuery("tenant-1", "handbook", "Who was Ada Lovelace?", 5));

        assertThat(result.retrieval().chunks()).hasSize(1);          // text retrieval intact
        assertThat(result.graph().entities()).isEmpty();             // no graph wired → empty
    }

    @Test
    void retrieveWithGraph_surfacesMatchedEntitiesAndNeighbours() {
        var scope = KnowledgeScope.of("tenant-1", "handbook");
        var store = new RecordingChunkStore(List.of(chunk("Ada pioneered computing.")));
        var graph = new FakeGraphStore();
        var ada = graph.put(scope, "Ada Lovelace", EntityType.PERSON, 4);
        var engine = graph.put(scope, "Analytical Engine", EntityType.CONCEPT, 7);
        graph.link(ada.id(), engine);
        var service = graphAware(store, graph);

        var result = service.retrieveWithGraph(
                new RetrievalQuery("tenant-1", "handbook", "Tell me about Ada Lovelace", 5));

        // Query entity resolved to a graph node, and its neighbour was expanded.
        assertThat(result.graph().entities()).extracting(RelevantEntity::name)
                .contains("Ada Lovelace", "Analytical Engine");
        var adaView = result.graph().entities().stream()
                .filter(e -> e.name().equals("Ada Lovelace")).findFirst().orElseThrow();
        assertThat(adaView.relevance()).isEqualTo(RelevantEntity.Relevance.MATCHED);
        var engineView = result.graph().entities().stream()
                .filter(e -> e.name().equals("Analytical Engine")).findFirst().orElseThrow();
        assertThat(engineView.relevance()).isEqualTo(RelevantEntity.Relevance.RELATED);
        assertThat(result.graph().summary()).contains("Ada Lovelace");
    }

    @Test
    void retrieveWithGraph_noEntityMatch_returnsEmptyGraph() {
        var store = new RecordingChunkStore(List.of(chunk("some text")));
        var graph = new FakeGraphStore(); // nothing registered
        var service = graphAware(store, graph);

        var result = service.retrieveWithGraph(
                new RetrievalQuery("tenant-1", "handbook", "Tell me about Ada Lovelace", 5));

        assertThat(result.graph().entities()).isEmpty();
    }

    @Test
    void retrieveWithGraph_graphFailure_isBestEffort() {
        var store = new RecordingChunkStore(List.of(chunk("alpha")));
        KnowledgeGraphStore failing = new FakeGraphStore() {
            @Override public Optional<KnowledgeEntity> findByName(KnowledgeScope s, String n, EntityType t) {
                throw new IllegalStateException("graph down");
            }
        };
        var service = new DefaultRagPipelineService(store, Optional.empty(),
                new HeuristicEntityExtractor(), failing);

        var result = service.retrieveWithGraph(
                new RetrievalQuery("tenant-1", "handbook", "Tell me about Ada Lovelace", 5));

        // A graph failure never breaks retrieval; the text half is intact, the graph is empty.
        assertThat(result.retrieval().chunks()).hasSize(1);
        assertThat(result.graph().entities()).isEmpty();
    }
}
