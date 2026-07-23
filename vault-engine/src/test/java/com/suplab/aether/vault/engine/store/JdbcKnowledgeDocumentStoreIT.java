package com.suplab.aether.vault.engine.store;

import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcKnowledgeDocumentStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_vault_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcKnowledgeDocumentStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcKnowledgeDocumentStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static KnowledgeScope uniqueScope() {
        return KnowledgeScope.of("tenant-" + UUID.randomUUID(), "collection-" + UUID.randomUUID());
    }

    @Test
    void save_andFindById_roundTrip() {
        var scope = uniqueScope();
        var doc = KnowledgeDocument.create(scope, "s3://a", "Doc A", "text/markdown", "sum-1");
        store.save(doc);

        var found = store.findById(scope, doc.id());

        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Doc A");
        assertThat(found.get().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(found.get().indexedAt()).isNull();
    }

    @Test
    void save_upsertPersistsIndexedTransition() {
        var scope = uniqueScope();
        var doc = KnowledgeDocument.create(scope, "s3://a", "Doc A", "text/plain", "sum-1");
        store.save(doc);
        store.save(doc.markIndexed(9));

        var found = store.findById(scope, doc.id()).orElseThrow();

        assertThat(found.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(found.chunkCount()).isEqualTo(9);
        assertThat(found.indexedAt()).isNotNull();
    }

    @Test
    void findById_isolatesPerCollection() {
        var scope = uniqueScope();
        var otherScope = KnowledgeScope.of(scope.tenantId(), scope.collectionId() + "-b");
        var doc = KnowledgeDocument.create(scope, "uri", "t", "text/plain", "sum");
        store.save(doc);

        assertThat(store.findById(otherScope, doc.id())).isEmpty();
    }

    @Test
    void countAndList_scopeToCollection() {
        var scope = uniqueScope();
        store.save(KnowledgeDocument.create(scope, "uri1", "t1", "text/plain", "s1"));
        store.save(KnowledgeDocument.create(scope, "uri2", "t2", "text/plain", "s2"));

        assertThat(store.countByCollection(scope)).isEqualTo(2);
        assertThat(store.findByCollection(scope, 10)).hasSize(2);
    }

    @Test
    void findBySourceUri_findsWithinScopeAndIsolatesAcrossCollections() {
        var scope = uniqueScope();
        var otherScope = KnowledgeScope.of(scope.tenantId(), scope.collectionId() + "-b");
        var doc = KnowledgeDocument.create(scope, "file:policy.md", "Policy", "text/markdown", "sum-1");
        store.save(doc);

        assertThat(store.findBySourceUri(scope, "file:policy.md")).isPresent();
        assertThat(store.findBySourceUri(scope, "file:policy.md").orElseThrow().id()).isEqualTo(doc.id());
        // same source URI, different collection → not visible
        assertThat(store.findBySourceUri(otherScope, "file:policy.md")).isEmpty();
        // unknown source URI → empty
        assertThat(store.findBySourceUri(scope, "file:missing.md")).isEmpty();
    }

    @Test
    void delete_removesDocumentWithinScope() {
        var scope = uniqueScope();
        var doc = KnowledgeDocument.create(scope, "uri", "t", "text/plain", "sum");
        store.save(doc);

        store.delete(scope, doc.id());

        assertThat(store.findById(scope, doc.id())).isEmpty();
    }
}
