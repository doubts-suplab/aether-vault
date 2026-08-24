package com.suplab.aether.vault.engine.store;

import com.suplab.aether.vault.domain.DocumentChunk;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * pgvector-backed implementation of {@link DocumentChunkStore}.
 *
 * <p>Chunk embeddings are stored in a {@code vector(384)} column using the pgvector extension.
 * The {@code <=>} operator provides cosine distance ordering for semantic similarity search.
 * Embeddings are serialised as {@code [x,y,z,...]} strings and cast to {@code ::vector} in the
 * SQL, which pgvector parses at query time. Every query is scoped by {@code tenant_id} and
 * {@code collection_id} — there is no cross-collection retrieval path.</p>
 */
public class PGVectorDocumentChunkStore implements DocumentChunkStore {

    private static final Logger log = LoggerFactory.getLogger(PGVectorDocumentChunkStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public PGVectorDocumentChunkStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(DocumentChunk chunk, float[] embedding) {
        var sql = """
                INSERT INTO document_chunks
                    (id, document_id, tenant_id, collection_id, ordinal, content, token_count,
                     embedding, created_at)
                VALUES
                    (:id, :documentId, :tenantId, :collectionId, :ordinal, :content, :tokenCount,
                     :embedding::vector, :createdAt)
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    token_count = EXCLUDED.token_count,
                    embedding = EXCLUDED.embedding
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", chunk.id())
                .addValue("documentId", chunk.documentId())
                .addValue("tenantId", chunk.tenantId())
                .addValue("collectionId", chunk.collectionId())
                .addValue("ordinal", chunk.ordinal())
                .addValue("content", chunk.content())
                .addValue("tokenCount", chunk.tokenCount())
                .addValue("embedding", toVectorString(embedding))
                .addValue("createdAt", Timestamp.from(chunk.createdAt()));
        jdbc.update(sql, params);
        log.debug("Saved chunk id={} documentId={} ordinal={} tenantId={} collectionId={}",
                chunk.id(), chunk.documentId(), chunk.ordinal(), chunk.tenantId(), chunk.collectionId());
    }

    @Override
    public List<RetrievedChunk> findSimilar(KnowledgeScope scope, float[] queryEmbedding, int topK) {
        var sql = """
                SELECT c.document_id, d.title, d.source_uri, c.ordinal, c.content,
                       (c.embedding <=> :query::vector) AS distance
                FROM document_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                WHERE c.tenant_id = :tenantId AND c.collection_id = :collectionId
                ORDER BY c.embedding <=> :query::vector
                LIMIT :topK
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId())
                .addValue("query", toVectorString(queryEmbedding))
                .addValue("topK", topK);
        return jdbc.query(sql, params, this::mapRetrieved);
    }

    @Override
    public int deleteByDocument(KnowledgeScope scope, UUID documentId) {
        var sql = """
                DELETE FROM document_chunks
                WHERE document_id = :documentId AND tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("documentId", documentId)
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        int deleted = jdbc.update(sql, params);
        log.debug("Deleted {} chunk(s) for documentId={} tenantId={} collectionId={}",
                deleted, documentId, scope.tenantId(), scope.collectionId());
        return deleted;
    }

    @Override
    public long countByCollection(KnowledgeScope scope) {
        var sql = """
                SELECT COUNT(*) FROM document_chunks
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public int deleteByCollection(KnowledgeScope scope) {
        var sql = """
                DELETE FROM document_chunks
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        int deleted = jdbc.update(sql, params);
        log.info("Erased {} chunk(s) tenantId={} collectionId={}",
                deleted, scope.tenantId(), scope.collectionId());
        return deleted;
    }

    private RetrievedChunk mapRetrieved(ResultSet rs, int row) throws SQLException {
        return new RetrievedChunk(
                UUID.fromString(rs.getString("document_id")),
                rs.getString("title"),
                rs.getString("source_uri"),
                rs.getInt("ordinal"),
                rs.getString("content"),
                rs.getDouble("distance")
        );
    }

    /**
     * Converts a float array to the {@code [x,y,z,...]} string format expected by pgvector's
     * {@code ::vector} cast operator.
     */
    static String toVectorString(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
