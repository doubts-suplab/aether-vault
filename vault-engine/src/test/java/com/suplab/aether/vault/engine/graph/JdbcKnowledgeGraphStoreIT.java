package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.KnowledgeEntity;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcKnowledgeGraphStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_vault_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcKnowledgeGraphStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcKnowledgeGraphStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static KnowledgeScope uniqueScope() {
        return KnowledgeScope.of("tenant-" + UUID.randomUUID(), "collection-" + UUID.randomUUID());
    }

    @Test
    void upsertEntity_bumpsMentionCountOnConflict() {
        var scope = uniqueScope();
        store.upsertEntity(KnowledgeEntity.create(scope, "pgvector", EntityType.CONCEPT));
        var second = store.upsertEntity(KnowledgeEntity.create(scope, "pgvector", EntityType.CONCEPT));

        assertThat(second.mentionCount()).isEqualTo(2);
        assertThat(store.findByName(scope, "pgvector", EntityType.CONCEPT)).isPresent();
    }

    @Test
    void findEntities_ordersByMentionCountDescending() {
        var scope = uniqueScope();
        store.upsertEntity(KnowledgeEntity.create(scope, "rare", EntityType.OTHER));
        store.upsertEntity(KnowledgeEntity.create(scope, "common", EntityType.OTHER));
        store.upsertEntity(KnowledgeEntity.create(scope, "common", EntityType.OTHER));

        var entities = store.findEntities(scope, 10);

        assertThat(entities).extracting(KnowledgeEntity::name).containsExactly("common", "rare");
    }

    @Test
    void relate_andNeighbours_traverseEdges() {
        var scope = uniqueScope();
        var a = store.upsertEntity(KnowledgeEntity.create(scope, "Service A", EntityType.PRODUCT));
        var b = store.upsertEntity(KnowledgeEntity.create(scope, "Service B", EntityType.PRODUCT));
        store.relate(scope, EntityRelation.of(a.id(), b.id(), "depends_on"));

        assertThat(store.neighbours(scope, a.id(), 10)).extracting(KnowledgeEntity::name)
                .containsExactly("Service B");
        // Undirected traversal: B also sees A as a neighbour.
        assertThat(store.neighbours(scope, b.id(), 10)).extracting(KnowledgeEntity::name)
                .containsExactly("Service A");
    }

    @Test
    void relate_rejectsEndpointsOutsideScope() {
        var scope = uniqueScope();
        var a = store.upsertEntity(KnowledgeEntity.create(scope, "In Scope", EntityType.OTHER));
        var strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> store.relate(scope, EntityRelation.of(a.id(), strangerId, "rel")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both entities must exist");
    }

    @Test
    void findEntities_isolatesPerCollection() {
        var scope = uniqueScope();
        var otherScope = KnowledgeScope.of(scope.tenantId(), scope.collectionId() + "-b");
        store.upsertEntity(KnowledgeEntity.create(scope, "Only Here", EntityType.OTHER));

        assertThat(store.findEntities(otherScope, 10)).isEmpty();
    }
}
