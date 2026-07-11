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
 * {@code indexed_at} is older than the configured re-index interval is transitioned to
 * {@code STALE}. No per-row round trips, so a sweep over a large corpus stays cheap. Freshness
 * never deletes — sub-interval documents keep their chunks searchable until a re-indexing job
 * refreshes them.</p>
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
        var sql = """
                UPDATE knowledge_documents
                SET status = 'STALE', updated_at = NOW()
                WHERE status = 'INDEXED'
                  AND indexed_at IS NOT NULL
                  AND indexed_at < NOW() - make_interval(days => :reindexIntervalDays)
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
