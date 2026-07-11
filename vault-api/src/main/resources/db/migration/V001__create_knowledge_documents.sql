-- V001 — Create knowledge_documents table
-- Tenant- and collection-scoped source documents (the unit of ingestion in Aether Vault).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE knowledge_documents;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     TEXT         NOT NULL,
    collection_id TEXT         NOT NULL,
    source_uri    TEXT         NOT NULL,
    title         TEXT         NOT NULL,
    content_type  TEXT         NOT NULL DEFAULT 'text/plain',
    checksum      TEXT         NOT NULL,
    status        TEXT         NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'INDEXED', 'STALE', 'FAILED')),
    chunk_count   INTEGER      NOT NULL DEFAULT 0
                               CHECK (chunk_count >= 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    indexed_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Primary collection-scoped access path.
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_scope
    ON knowledge_documents (tenant_id, collection_id, updated_at DESC);

-- Freshness sweep scans INDEXED documents by age.
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_freshness
    ON knowledge_documents (indexed_at)
    WHERE status = 'INDEXED';
