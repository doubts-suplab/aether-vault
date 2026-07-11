-- V003 — Add pgvector embedding column to document_chunks
-- Lock risk: MEDIUM (ALTER TABLE on existing table; AccessExclusiveLock during ADD COLUMN)
-- The column is nullable initially so the migration completes without a table scan backfill.
-- Rollback: ALTER TABLE document_chunks DROP COLUMN IF EXISTS embedding;
--           DROP INDEX IF EXISTS idx_document_chunks_embedding;

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS embedding vector(384);

-- IVFFlat index for approximate cosine similarity search.
-- lists=100 is sized for ~1M vectors per the pgvector recommendation.
-- Revisit when total embeddings approach that threshold.
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
