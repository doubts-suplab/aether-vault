package com.suplab.aether.vault.engine.freshness;

import com.suplab.aether.vault.domain.FreshnessPolicy;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.FreshnessPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link FreshnessPolicyStore} backed by the
 * {@code collection_freshness_policy} table.
 *
 * <p>Uses {@code NamedParameterJdbcTemplate} with explicit column lists and UPSERT on
 * {@code (tenant_id, collection_id)}. Every read and write is scoped by tenant + collection.</p>
 */
public class JdbcFreshnessPolicyStore implements FreshnessPolicyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcFreshnessPolicyStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFreshnessPolicyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FreshnessPolicy policy) {
        var sql = """
                INSERT INTO collection_freshness_policy
                    (tenant_id, collection_id, reindex_interval_days, auto_reingest, updated_at)
                VALUES
                    (:tenantId, :collectionId, :reindexIntervalDays, :autoReingest, :updatedAt)
                ON CONFLICT (tenant_id, collection_id) DO UPDATE SET
                    reindex_interval_days = EXCLUDED.reindex_interval_days,
                    auto_reingest = EXCLUDED.auto_reingest,
                    updated_at = EXCLUDED.updated_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", policy.tenantId())
                .addValue("collectionId", policy.collectionId())
                .addValue("reindexIntervalDays", policy.reindexIntervalDays())
                .addValue("autoReingest", policy.autoReingest())
                .addValue("updatedAt", Timestamp.from(policy.updatedAt()));
        jdbc.update(sql, params);
        log.debug("Saved freshness policy tenantId={} collectionId={} intervalDays={} autoReingest={}",
                policy.tenantId(), policy.collectionId(), policy.reindexIntervalDays(), policy.autoReingest());
    }

    @Override
    public Optional<FreshnessPolicy> find(KnowledgeScope scope) {
        var sql = """
                SELECT tenant_id, collection_id, reindex_interval_days, auto_reingest, updated_at
                FROM collection_freshness_policy
                WHERE tenant_id = :tenantId AND collection_id = :collectionId
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("collectionId", scope.collectionId());
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<KnowledgeScope> findAutoReingestScopes(int limit) {
        var sql = """
                SELECT tenant_id, collection_id
                FROM collection_freshness_policy
                WHERE auto_reingest = TRUE
                ORDER BY updated_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource().addValue("limit", limit);
        return jdbc.query(sql, params, (rs, row) ->
                new KnowledgeScope(rs.getString("tenant_id"), rs.getString("collection_id")));
    }

    private FreshnessPolicy mapRow(ResultSet rs, int row) throws SQLException {
        return new FreshnessPolicy(
                rs.getString("tenant_id"),
                rs.getString("collection_id"),
                rs.getInt("reindex_interval_days"),
                rs.getBoolean("auto_reingest"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
