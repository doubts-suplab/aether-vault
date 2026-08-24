package com.suplab.aether.vault.engine.freshness;

import com.suplab.aether.vault.domain.FreshnessPolicy;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.engine.store.JdbcKnowledgeDocumentStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcFreshnessPolicyStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_vault_test")
            .withUsername("aether")
            .withPassword("aether");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcFreshnessPolicyStore policyStore;
    private JdbcKnowledgeDocumentStore documentStore;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        policyStore = new JdbcFreshnessPolicyStore(jdbc);
        documentStore = new JdbcKnowledgeDocumentStore(jdbc);
    }

    private static KnowledgeScope uniqueScope() {
        return KnowledgeScope.of("tenant-" + UUID.randomUUID(), "collection-" + UUID.randomUUID());
    }

    @Test
    void save_upsert_andFind_roundTrip() {
        var scope = uniqueScope();
        policyStore.save(FreshnessPolicy.of(scope, 7, true));

        var found = policyStore.find(scope).orElseThrow();
        assertThat(found.reindexIntervalDays()).isEqualTo(7);
        assertThat(found.autoReingest()).isTrue();

        // upsert on (tenant, collection) — a second save overwrites, not duplicates
        policyStore.save(FreshnessPolicy.of(scope, 14, false));
        var updated = policyStore.find(scope).orElseThrow();
        assertThat(updated.reindexIntervalDays()).isEqualTo(14);
        assertThat(updated.autoReingest()).isFalse();
    }

    @Test
    void find_isEmptyForUnknownCollection() {
        assertThat(policyStore.find(uniqueScope())).isEmpty();
    }

    @Test
    void findAutoReingestScopes_returnsOnlyOptedInCollections() {
        var optedIn = uniqueScope();
        var optedOut = uniqueScope();
        policyStore.save(FreshnessPolicy.of(optedIn, 5, true));
        policyStore.save(FreshnessPolicy.of(optedOut, 5, false));

        var scopes = policyStore.findAutoReingestScopes(100);

        assertThat(scopes).contains(optedIn).doesNotContain(optedOut);
    }

    @Test
    void freshnessSweep_honoursPerCollectionInterval() {
        // Two collections, both with a document indexed 10 days ago. Global default is 30 days, so
        // neither would be stale by default — but the fast collection overrides to a 7-day interval.
        var fast = uniqueScope();
        var slow = uniqueScope();
        policyStore.save(FreshnessPolicy.of(fast, 7, false));

        indexedDaysAgo(fast, 10);
        indexedDaysAgo(slow, 10);

        var sweep = new DocumentFreshnessService(jdbc, 30);
        sweep.sweep();

        // fast collection's doc breached its 7-day override → STALE; slow collection uses the 30-day
        // default → still INDEXED.
        assertThat(onlyDoc(fast).status().name()).isEqualTo("STALE");
        assertThat(onlyDoc(slow).status().name()).isEqualTo("INDEXED");
    }

    private void indexedDaysAgo(KnowledgeScope scope, int days) {
        var doc = KnowledgeDocument.create(scope, "uri-" + UUID.randomUUID(), "t", "text/plain", "sum")
                .markIndexed(3);
        documentStore.save(doc);
        // Backdate indexed_at directly — markIndexed stamps "now".
        jdbc.update("UPDATE knowledge_documents SET indexed_at = :ts WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("ts", Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)))
                        .addValue("id", doc.id()));
    }

    private KnowledgeDocument onlyDoc(KnowledgeScope scope) {
        return documentStore.findByCollection(scope, 1).get(0);
    }
}
