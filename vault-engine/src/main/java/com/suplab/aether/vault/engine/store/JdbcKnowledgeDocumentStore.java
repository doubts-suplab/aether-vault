package com.suplab.aether.vault.engine.store;

import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
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
 * JDBC implementation of {@link KnowledgeDocumentStore} backed by the {@code knowledge_documents}
 * table.
 *
 * <p>Uses {@code NamedParameterJdbcTemplate} with explicit column lists and UPSERT on the
 * document ID. Every read and write is scoped by {@code tenant_id} and {@code collection_id}.</p>
 */
public class JdbcKnowledgeDocumentStore implements KnowledgeDocumentStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcKnowledgeDocumentStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcKnowledgeDocumentStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(KnowledgeDocument document) {
        var sql = """
                INSERT INTO knowledge_documents
                    (id, tenant_id, collection_id, source_uri, title, content_type, checksum,
                     status, chunk_count, created_at, indexed_at, updated_at)
                VALUES
                    (:id, :tenantId, :collectionId, :sourceUri, :title, :contentType, :checksum,
                     :status, :chunkCount, :createdAt, :indexedAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET
                    source_uri = EXCLUDED.source_uri,
                    title = EXCLUDED.title,
                    content_type = EXCLUDED.content_type,
                    checksum = EXCLUDED.checksum,
                    status = EXCLUDED.status,
                    chunk_count = EXCLUDED.chunk_count,
                    indexed_at = EXCLUDED.indexed_at,
                    updated_at = EXCLUDED.updated_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", document.id())
                .addValue("tenantId", document.tenantId())
                .addValue("collectionId", document.collectionId())
                .addValue("sourceUri", document.sourceUri())
                .addValue("title", document.title())
                .addValue("contentType", document.contentType())
                .addValue("checksum", document.checksum())
                .addValue("status", document.status().name())
                .addValue("chunkCount", document.chunkCount())
                .addValue("createdAt", Timestamp.from(document.createdAt()))
                .addValue("indexedAt", document.indexedAt() != null ? Timestamp.from(document.indexedAt()) : null)
                .addValue("updatedAt", Timestamp.from(document.updatedAt()));
        jdbc.update(sql, params);
        log.debug("Saved document id={} tenantId={} collectionId={} status={} chunkCount={}",
                document.id(), document.tenantId(), document.collectionId(), document.status(),
                document.chunkCount());
    }

    @Override
    public Optional<KnowledgeDocument> findById(KnowledgeScope scope, UUID documentId) {
        var sql = """
                SELECT id, tenant_id, collection_id, source_uri, title, content_type, checksum,
                       status, chunk_count, created_at, indexed_at, updated_at
                FROM knowledge_documents
                WHERE id = :id AND tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<KnowledgeDocument> findByCollection(KnowledgeScope scope, int limit) {
        var sql = """
                SELECT id, tenant_id, collection_id, source_uri, title, content_type, checksum,
                       status, chunk_count, created_at, indexed_at, updated_at
                FROM knowledge_documents
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                ORDER BY updated_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId())
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public long countByCollection(KnowledgeScope scope) {
        var sql = """
                SELECT COUNT(*) FROM knowledge_documents
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public void delete(KnowledgeScope scope, UUID documentId) {
        var sql = """
                DELETE FROM knowledge_documents
                WHERE id = :id AND tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        int deleted = jdbc.update(sql, params);
        log.debug("Deleted {} document record(s) id={} tenantId={} collectionId={}",
                deleted, documentId, scope.tenantId(), scope.collectionId());
    }

    private KnowledgeDocument mapRow(ResultSet rs, int row) throws SQLException {
        Timestamp indexedAt = rs.getTimestamp("indexed_at");
        return new KnowledgeDocument(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("collection_id"),
                rs.getString("source_uri"),
                rs.getString("title"),
                rs.getString("content_type"),
                rs.getString("checksum"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getInt("chunk_count"),
                rs.getTimestamp("created_at").toInstant(),
                indexedAt != null ? indexedAt.toInstant() : null,
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
