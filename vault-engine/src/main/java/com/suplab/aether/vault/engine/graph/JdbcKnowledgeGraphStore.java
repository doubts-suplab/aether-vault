package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link KnowledgeGraphStore} over the {@code knowledge_entities} and
 * {@code entity_relations} tables.
 *
 * <p>Entities are unique per {@code (tenant_id, collection_id, name, type)}; {@link #upsertEntity}
 * increments {@code mention_count} on conflict. {@link #relate} verifies both endpoints exist in
 * the given scope before inserting an edge, so the graph never bridges collections.</p>
 */
public class JdbcKnowledgeGraphStore implements KnowledgeGraphStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcKnowledgeGraphStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcKnowledgeGraphStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KnowledgeEntity upsertEntity(KnowledgeEntity entity) {
        var sql = """
                INSERT INTO knowledge_entities
                    (id, tenant_id, collection_id, name, entity_type, mention_count, created_at)
                VALUES
                    (:id, :tenantId, :collectionId, :name, :entityType, :mentionCount, :createdAt)
                ON CONFLICT (tenant_id, collection_id, name, entity_type) DO UPDATE SET
                    mention_count = knowledge_entities.mention_count + 1
                RETURNING id, tenant_id, collection_id, name, entity_type, mention_count, created_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", entity.id())
                .addValue("tenantId", entity.tenantId())
                .addValue("collectionId", entity.collectionId())
                .addValue("name", entity.name())
                .addValue("entityType", entity.type().name())
                .addValue("mentionCount", entity.mentionCount())
                .addValue("createdAt", Timestamp.from(entity.createdAt()));
        var stored = jdbc.query(sql, params, this::mapEntity).getFirst();
        log.debug("Upserted entity name={} type={} tenantId={} collectionId={} mentionCount={}",
                stored.name(), stored.type(), stored.tenantId(), stored.collectionId(), stored.mentionCount());
        return stored;
    }

    @Override
    public Optional<KnowledgeEntity> findByName(KnowledgeScope scope, String name, EntityType type) {
        var sql = """
                SELECT id, tenant_id, collection_id, name, entity_type, mention_count, created_at
                FROM knowledge_entities
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                  AND name = :name AND entity_type = :entityType
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId())
                .addValue("name", name)
                .addValue("entityType", type.name());
        return jdbc.query(sql, params, this::mapEntity).stream().findFirst();
    }

    @Override
    public List<KnowledgeEntity> findEntities(KnowledgeScope scope, int limit) {
        var sql = """
                SELECT id, tenant_id, collection_id, name, entity_type, mention_count, created_at
                FROM knowledge_entities
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                ORDER BY mention_count DESC, name ASC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId())
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapEntity);
    }

    @Override
    public void relate(KnowledgeScope scope, EntityRelation relation) {
        // Both endpoints must exist within this scope — reject cross-collection edges.
        var guard = """
                SELECT COUNT(*) FROM knowledge_entities
                WHERE tenant_id = :tenantId AND collection_id = :collectionId AND id IN (:source, :target)
                """;
        var guardParams = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId())
                .addValue("source", relation.sourceEntityId())
                .addValue("target", relation.targetEntityId());
        Long present = jdbc.queryForObject(guard, guardParams, Long.class);
        if (present == null || present < 2) {
            throw new IllegalArgumentException("both entities must exist in the given scope to relate them");
        }

        var sql = """
                INSERT INTO entity_relations
                    (source_entity_id, target_entity_id, relation_type, confidence)
                VALUES
                    (:source, :target, :relationType, :confidence)
                ON CONFLICT (source_entity_id, target_entity_id, relation_type) DO UPDATE SET
                    confidence = EXCLUDED.confidence
                """;
        var params = new MapSqlParameterSource()
                .addValue("source", relation.sourceEntityId())
                .addValue("target", relation.targetEntityId())
                .addValue("relationType", relation.relationType())
                .addValue("confidence", relation.confidence());
        jdbc.update(sql, params);
        log.debug("Related source={} target={} type={} confidence={}",
                relation.sourceEntityId(), relation.targetEntityId(), relation.relationType(),
                relation.confidence());
    }

    @Override
    public List<KnowledgeEntity> neighbours(KnowledgeScope scope, UUID entityId, int limit) {
        var sql = """
                SELECT e.id, e.tenant_id, e.collection_id, e.name, e.entity_type, e.mention_count,
                       e.created_at
                FROM knowledge_entities e
                JOIN (
                    SELECT target_entity_id AS neighbour_id FROM entity_relations
                    WHERE source_entity_id = :entityId
                    UNION
                    SELECT source_entity_id AS neighbour_id FROM entity_relations
                    WHERE target_entity_id = :entityId
                ) n ON n.neighbour_id = e.id
                WHERE e.tenant_id = :tenantId AND e.collection_id = :collectionId
                ORDER BY e.mention_count DESC, e.name ASC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("entityId", entityId)
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId())
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapEntity);
    }

    private KnowledgeEntity mapEntity(ResultSet rs, int row) throws SQLException {
        return new KnowledgeEntity(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("collection_id"),
                rs.getString("name"),
                EntityType.valueOf(rs.getString("entity_type")),
                rs.getInt("mention_count"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
