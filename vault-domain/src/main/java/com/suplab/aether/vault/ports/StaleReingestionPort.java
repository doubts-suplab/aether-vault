package com.suplab.aether.vault.ports;

/**
 * Automatically re-ingests {@code STALE} documents in collections that have opted in — the closing
 * half of the freshness loop (the freshness sweep <em>marks</em> stale; this <em>refreshes</em>).
 *
 * <p>For each collection whose {@link com.suplab.aether.vault.domain.FreshnessPolicy} enables
 * {@code autoReingest}, the sweep re-fetches its {@code STALE} documents through their source
 * connector and re-indexes them in place (unchanged sources are skipped by the checksum-aware ingest
 * path). It is <strong>best-effort</strong>: a source that cannot be fetched is logged and left
 * {@code STALE} for the next run — a single bad source never stops the sweep. Requires a source
 * connector to be enabled; with none, this is a no-op.</p>
 */
public interface StaleReingestionPort {

    /**
     * Outcome of one auto-reingestion sweep.
     *
     * @param scopesScanned        auto-reingest collections examined this run
     * @param documentsReingested  stale documents successfully re-fetched + re-indexed (or confirmed unchanged)
     * @param documentsFailed      stale documents whose re-fetch failed (left stale for the next run)
     */
    record ReingestionResult(long scopesScanned, long documentsReingested, long documentsFailed) {}

    /**
     * Runs one auto-reingestion sweep across every collection that opted into {@code autoReingest}.
     *
     * @param maxScopes    maximum auto-reingest collections to process this run
     * @param maxPerScope  maximum stale documents to re-ingest per collection this run
     * @return the sweep result
     */
    ReingestionResult reingestStale(int maxScopes, int maxPerScope);
}
