package com.suplab.aether.vault.engine.freshness;

import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Set-based JDBC implementation of {@link KnowledgeFreshnessPort}.
 *
 * <p>The sweep runs as a single {@code UPDATE}: every {@code INDEXED} document whose
 * {@code indexed_at} is older than its re-index interval is transitioned to {@code STALE}. The
 * interval is per-collection — a collection's {@code collection_freshness_policy} override is used
 * when present, otherwise the configured global default (via a correlated {@code COALESCE}), so a
 * fast-moving collection can be re-indexed weekly while a stable archive ages for a year. No per-row
 * round trips, so a sweep over a large corpus stays cheap. Freshness never deletes — sub-interval
 * documents keep their chunks searchable until a re-indexing job refreshes them.</p>
 */
public class DocumentFreshnessService implements KnowledgeFreshnessPort {

    private static final Logger log = LoggerFactory.getLogger(DocumentFreshnessService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final int reindexIntervalDays;

    /**
     * @param reindexIntervalDays age (in days since last index) beyond which an {@code INDEXED}
     *                            document is considered stale and flagged for re-indexing
     */
    public DocumentFreshnessService(NamedParameterJdbcTemplate jdbc, int reindexIntervalDays) {
        this.jdbc = jdbc;
        this.reindexIntervalDays = reindexIntervalDays;
    }

    @Override
    public FreshnessResult sweep() {
        long scanned = countIndexed();
        long markedStale = markStale();
        long remainingIndexed = countIndexed();
        log.info("Knowledge freshness sweep complete: scanned={} markedStale={} totalIndexed={}",
                scanned, markedStale, remainingIndexed);
        return new FreshnessResult(scanned, markedStale, remainingIndexed);
    }

    private long markStale() {
        // Per-collection interval: use the collection's policy override when present, else the global
        // default. The correlated subquery keeps the sweep a single set-based UPDATE.
        var sql = """
                UPDATE knowledge_documents d
                SET status = 'STALE', updated_at = NOW()
                WHERE d.status = 'INDEXED'
                  AND d.indexed_at IS NOT NULL
                  AND d.indexed_at < NOW() - make_interval(days => COALESCE(
                        (SELECT p.reindex_interval_days FROM collection_freshness_policy p
                         WHERE p.tenant_id = d.tenant_id AND p.collection_id = d.collection_id),
                        :reindexIntervalDays))
                """;
        var params = new MapSqlParameterSource("reindexIntervalDays", reindexIntervalDays);
        return jdbc.update(sql, params);
    }

    private long countIndexed() {
        var sql = "SELECT COUNT(*) FROM knowledge_documents WHERE status = 'INDEXED'";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count != null ? count : 0L;
    }
}
