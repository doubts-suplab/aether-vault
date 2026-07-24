package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityType;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end extraction against a real Postgres: heuristic extractor + JDBC graph store, so the
 * upsert-on-conflict and relate idempotency actually exercise the database.
 */
@Testcontainers
class DefaultKnowledgeGraphExtractionServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_vault_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcKnowledgeGraphStore store;
    private DefaultKnowledgeGraphExtractionService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcKnowledgeGraphStore(new NamedParameterJdbcTemplate(dataSource));
        service = new DefaultKnowledgeGraphExtractionService(new HeuristicEntityExtractor(), store);
    }

    private static KnowledgeScope uniqueScope() {
        return KnowledgeScope.of("tenant-" + UUID.randomUUID(), "collection-" + UUID.randomUUID());
    }

    @Test
    void extractsEntitiesAndCoOccurrenceEdgesIntoTheGraph() {
        var scope = uniqueScope();
        var doc = KnowledgeDocument.create(scope, "uri", "title", "text/plain", "sum");

        var summary = service.extract(doc, List.of("Ada Lovelace worked with Charles Babbage."));

        assertThat(summary.entitiesFound()).isEqualTo(2);
        assertThat(summary.relationsCreated()).isEqualTo(1);

        var ada = store.findByName(scope, "Ada Lovelace", EntityType.PERSON).orElseThrow();
        var neighbours = store.neighbours(scope, ada.id(), 10);
        assertThat(neighbours).extracting(e -> e.name()).contains("Charles Babbage");
    }

    @Test
    void reExtractionIsIdempotent_bumpsMentionsWithoutDuplicatingEdges() {
        var scope = uniqueScope();
        var doc = KnowledgeDocument.create(scope, "uri", "title", "text/plain", "sum");
        var chunks = List.of("Acme Corp partnered with NASA.");

        service.extract(doc, chunks);
        var second = service.extract(doc, chunks); // same input again

        assertThat(second.entitiesFound()).isEqualTo(2);
        assertThat(second.relationsCreated()).isEqualTo(1);
        // mention counts grew, but the collection still has exactly the two entities
        assertThat(store.findEntities(scope, 50)).hasSize(2);
        var acme = store.findByName(scope, "Acme Corp", EntityType.ORGANISATION).orElseThrow();
        assertThat(acme.mentionCount()).isEqualTo(2);
        assertThat(store.neighbours(scope, acme.id(), 10)).hasSize(1);
    }
}
