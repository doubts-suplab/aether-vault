package com.suplab.aether.vault.engine.store;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievedChunk;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PGVectorDocumentChunkStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_vault_test")
            .withUsername("aether")
            .withPassword("aether");

    private NamedParameterJdbcTemplate jdbc;
    private PGVectorDocumentChunkStore chunkStore;
    private JdbcKnowledgeDocumentStore documentStore;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        chunkStore = new PGVectorDocumentChunkStore(jdbc);
        documentStore = new JdbcKnowledgeDocumentStore(jdbc);
    }

    private KnowledgeDocument persistedDocument(KnowledgeScope scope) {
        var doc = KnowledgeDocument.create(scope, "uri", "The Title", "text/plain", "sum");
        documentStore.save(doc);
        return doc;
    }

    private static KnowledgeScope uniqueScope() {
        return KnowledgeScope.of("tenant-" + UUID.randomUUID(), "collection-" + UUID.randomUUID());
    }

    @Test
    void save_andFindSimilar_roundTripWithDocumentJoin() {
        var scope = uniqueScope();
        var doc = persistedDocument(scope);
        chunkStore.save(DocumentChunk.create(doc, 0, "how the team deploys", 5), new float[384]);

        List<RetrievedChunk> found = chunkStore.findSimilar(scope, new float[384], 5);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("how the team deploys");
        assertThat(found.getFirst().documentTitle()).isEqualTo("The Title");
        assertThat(found.getFirst().documentId()).isEqualTo(doc.id());
    }

    @Test
    void findSimilar_isolatesPerCollection() {
        var scopeA = uniqueScope();
        var scopeB = KnowledgeScope.of(scopeA.tenantId(), scopeA.collectionId() + "-b");
        var docA = persistedDocument(scopeA);
        var docB = persistedDocument(scopeB);
        chunkStore.save(DocumentChunk.create(docA, 0, "collection A chunk", 3), new float[384]);
        chunkStore.save(DocumentChunk.create(docB, 0, "collection B chunk", 3), new float[384]);

        var resultA = chunkStore.findSimilar(scopeA, new float[384], 10);

        assertThat(resultA).extracting(RetrievedChunk::content).containsExactly("collection A chunk");
    }

    @Test
    void countByCollection_countsOnlyThatCollection() {
        var scope = uniqueScope();
        var doc = persistedDocument(scope);
        assertThat(chunkStore.countByCollection(scope)).isZero();

        chunkStore.save(DocumentChunk.create(doc, 0, "c0", 1), new float[384]);
        chunkStore.save(DocumentChunk.create(doc, 1, "c1", 1), new float[384]);

        assertThat(chunkStore.countByCollection(scope)).isEqualTo(2);
    }

    @Test
    void deleteByDocument_removesOnlyThatDocumentsChunks() {
        var scope = uniqueScope();
        var doc1 = persistedDocument(scope);
        var doc2 = persistedDocument(scope);
        chunkStore.save(DocumentChunk.create(doc1, 0, "doc1 chunk", 1), new float[384]);
        chunkStore.save(DocumentChunk.create(doc2, 0, "doc2 chunk", 1), new float[384]);

        int deleted = chunkStore.deleteByDocument(scope, doc1.id());

        assertThat(deleted).isEqualTo(1);
        assertThat(chunkStore.countByCollection(scope)).isEqualTo(1);
    }

    @Test
    void save_upsertUpdatesExistingChunk() {
        var scope = uniqueScope();
        var doc = persistedDocument(scope);
        var chunk = DocumentChunk.create(doc, 0, "original", 1);
        chunkStore.save(chunk, new float[384]);

        var updated = new DocumentChunk(chunk.id(), doc.id(), scope.tenantId(), scope.collectionId(),
                0, "updated", 2, chunk.createdAt());
        chunkStore.save(updated, new float[384]);

        var found = chunkStore.findSimilar(scope, new float[384], 10);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("updated");
    }
}
