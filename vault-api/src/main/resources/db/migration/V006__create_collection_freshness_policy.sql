-- V006 — Per-collection knowledge-freshness policy
-- Overrides the global re-index interval per collection, and opts a collection into automatic
-- re-ingestion of its stale documents. Collection-scoped — one policy per (tenant, collection).
-- A collection with no row uses the global default interval (aether.vault.freshness.reindex-interval-days).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE collection_freshness_policy;

CREATE TABLE IF NOT EXISTS collection_freshness_policy (
    tenant_id             TEXT        NOT NULL,
    collection_id         TEXT        NOT NULL,
    reindex_interval_days INTEGER     NOT NULL DEFAULT 30
                                      CHECK (reindex_interval_days >= 1),
    auto_reingest         BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, collection_id)
);

-- The stale-reingestion sweep lists collections opted into auto-reingestion.
CREATE INDEX IF NOT EXISTS idx_collection_freshness_auto_reingest
    ON collection_freshness_policy (auto_reingest)
    WHERE auto_reingest = TRUE;
