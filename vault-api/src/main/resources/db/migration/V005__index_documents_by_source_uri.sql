-- V005 — Index knowledge_documents by source URI within a collection
-- Supports the connector-driven ingest path: findBySourceUri gives one source URI a single
-- stable document (skip-if-unchanged, re-index-if-changed) without a full collection scan.
-- Lock risk: LOW (CREATE INDEX IF NOT EXISTS on a young table)
-- Rollback: DROP INDEX IF EXISTS idx_knowledge_documents_source_uri;

CREATE INDEX IF NOT EXISTS idx_knowledge_documents_source_uri
    ON knowledge_documents (tenant_id, collection_id, source_uri);
