package com.suplab.aether.vault.ports;

/**
 * Port interface for knowledge freshness — keeping indexed documents current.
 *
 * <p>Indexed knowledge goes stale: a source is edited, or simply ages past the point where its
 * embedding can be trusted. The freshness sweep flags {@code INDEXED} documents whose last index
 * is older than the configured re-index interval as {@code STALE}, so a downstream re-indexing
 * job (or an operator) knows what to refresh. Their existing chunks stay searchable until they
 * are re-ingested — freshness marks, it never deletes.</p>
 */
public interface KnowledgeFreshnessPort {

    /**
     * Outcome of one freshness sweep across all collections.
     *
     * @param scannedCount     indexed documents examined this run
     * @param markedStaleCount documents transitioned to {@code STALE} this run
     * @param totalIndexed     documents still {@code INDEXED} after the run
     */
    record FreshnessResult(long scannedCount, long markedStaleCount, long totalIndexed) {}

    /**
     * Runs one freshness sweep: marks {@code INDEXED} documents whose {@code indexed_at} is older
     * than the re-index interval as {@code STALE}.
     *
     * @return the sweep result
     */
    FreshnessResult sweep();
}
