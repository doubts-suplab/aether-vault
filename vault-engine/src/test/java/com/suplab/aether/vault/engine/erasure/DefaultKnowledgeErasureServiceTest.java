package com.suplab.aether.vault.engine.erasure;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultKnowledgeErasureServiceTest {

    private static final KnowledgeScope SCOPE = new KnowledgeScope("acme", "handbook");

    private static final class FakeDocStore implements KnowledgeDocumentStore {
        int docs;
        FakeDocStore(int docs) { this.docs = docs; }
        @Override public void save(KnowledgeDocument d) { }
        @Override public Optional<KnowledgeDocument> findById(KnowledgeScope s, UUID id) { return Optional.empty(); }
        @Override public Optional<KnowledgeDocument> findBySourceUri(KnowledgeScope s, String u) { return Optional.empty(); }
        @Override public List<KnowledgeDocument> findByCollection(KnowledgeScope s, int l) { return List.of(); }
        @Override public long countByCollection(KnowledgeScope s) { return docs; }
        @Override public void delete(KnowledgeScope s, UUID id) { }
        @Override public java.util.List<com.suplab.aether.vault.domain.KnowledgeDocument> findByStatus(KnowledgeScope s, com.suplab.aether.vault.domain.DocumentStatus st, int l) { return java.util.List.of(); }
        @Override public int deleteByCollection(KnowledgeScope s) {
            int d = s.equals(SCOPE) ? docs : 0;
            if (s.equals(SCOPE)) docs = 0;
            return d;
        }
    }

    private static final class FakeChunkStore implements DocumentChunkStore {
        int chunks;
        FakeChunkStore(int chunks) { this.chunks = chunks; }
        @Override public void save(DocumentChunk c, float[] e) { }
        @Override public List<RetrievedChunk> findSimilar(KnowledgeScope s, float[] q, int k) { return List.of(); }
        @Override public int deleteByDocument(KnowledgeScope s, UUID id) { return 0; }
        @Override public long countByCollection(KnowledgeScope s) { return chunks; }
        @Override public int deleteByCollection(KnowledgeScope s) {
            int c = s.equals(SCOPE) ? chunks : 0;
            if (s.equals(SCOPE)) chunks = 0;
            return c;
        }
    }

    private static final class FakeGraphStore implements KnowledgeGraphStore {
        int entities;
        FakeGraphStore(int entities) { this.entities = entities; }
        @Override public KnowledgeEntity upsertEntity(KnowledgeEntity e) { return e; }
        @Override public Optional<KnowledgeEntity> findByName(KnowledgeScope s, String n, EntityType t) { return Optional.empty(); }
        @Override public List<KnowledgeEntity> findEntities(KnowledgeScope s, int l) { return List.of(); }
        @Override public void relate(KnowledgeScope s, EntityRelation r) { }
        @Override public List<KnowledgeEntity> neighbours(KnowledgeScope s, UUID id, int l) { return List.of(); }
        @Override public int deleteByCollection(KnowledgeScope s) {
            int e = s.equals(SCOPE) ? entities : 0;
            if (s.equals(SCOPE)) entities = 0;
            return e;
        }
    }

    @Test
    void erasesDocumentsChunksAndGraphAndReportsCounts() {
        var docs = new FakeDocStore(3);
        var chunks = new FakeChunkStore(12);
        var graph = new FakeGraphStore(7);
        var service = new DefaultKnowledgeErasureService(docs, chunks, graph);

        var result = service.eraseCollection(SCOPE);

        assertThat(result.documentsErased()).isEqualTo(3);
        assertThat(result.chunksErased()).isEqualTo(12);
        assertThat(result.entitiesErased()).isEqualTo(7);
        assertThat(result.isEmpty()).isFalse();
        // everything is gone — a second erase is a no-op
        var again = service.eraseCollection(SCOPE);
        assertThat(again.isEmpty()).isTrue();
    }

    @Test
    void erasingAnEmptyCollectionIsANoOp() {
        var service = new DefaultKnowledgeErasureService(
                new FakeDocStore(0), new FakeChunkStore(0), new FakeGraphStore(0));

        var result = service.eraseCollection(SCOPE);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.documentsErased()).isZero();
    }

    @Test
    void erasureIsScopedToTheGivenCollection() {
        var docs = new FakeDocStore(3);
        var service = new DefaultKnowledgeErasureService(docs, new FakeChunkStore(0), new FakeGraphStore(0));

        var result = service.eraseCollection(new KnowledgeScope("acme", "other-collection"));

        // a different collection erases nothing here
        assertThat(result.documentsErased()).isZero();
        assertThat(docs.docs).isEqualTo(3);
    }
}
