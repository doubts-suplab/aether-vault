-- V002 — Create document_chunks table
-- Ordered slices of a knowledge_document; the unit of embedding and vector search.
-- Deleting a document cascades to its chunks (re-indexing replaces them wholesale).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE document_chunks;

CREATE TABLE IF NOT EXISTS document_chunks (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID         NOT NULL REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    tenant_id     TEXT         NOT NULL,
    collection_id TEXT         NOT NULL,
    ordinal       INTEGER      NOT NULL
                               CHECK (ordinal >= 0),
    content       TEXT         NOT NULL,
    token_count   INTEGER      NOT NULL DEFAULT 0
                               CHECK (token_count >= 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, ordinal)
);

-- Collection-scoped retrieval path (vector search filters on this before ordering by distance).
CREATE INDEX IF NOT EXISTS idx_document_chunks_scope
    ON document_chunks (tenant_id, collection_id);

-- Fast chunk cleanup on re-index / document delete.
CREATE INDEX IF NOT EXISTS idx_document_chunks_document
    ON document_chunks (document_id);
