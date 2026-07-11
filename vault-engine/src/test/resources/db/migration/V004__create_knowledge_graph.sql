-- V004 — Create knowledge graph tables (knowledge_entities, entity_relations)
-- Entities are nodes, relations are edges. Both are collection-scoped — the graph never
-- bridges the tenancy boundary.
-- Lock risk: LOW (new tables, no existing data)
-- Rollback: DROP TABLE entity_relations; DROP TABLE knowledge_entities;

CREATE TABLE IF NOT EXISTS knowledge_entities (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     TEXT         NOT NULL,
    collection_id TEXT         NOT NULL,
    name          TEXT         NOT NULL,
    entity_type   TEXT         NOT NULL DEFAULT 'OTHER'
                               CHECK (entity_type IN ('PERSON', 'ORGANISATION', 'LOCATION',
                                                      'CONCEPT', 'PRODUCT', 'EVENT', 'OTHER')),
    mention_count INTEGER      NOT NULL DEFAULT 1
                               CHECK (mention_count >= 1),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- One node per distinct surface form + type within a collection; upsert bumps mention_count.
    UNIQUE (tenant_id, collection_id, name, entity_type)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_entities_scope
    ON knowledge_entities (tenant_id, collection_id, mention_count DESC);

CREATE TABLE IF NOT EXISTS entity_relations (
    source_entity_id UUID             NOT NULL REFERENCES knowledge_entities (id) ON DELETE CASCADE,
    target_entity_id UUID             NOT NULL REFERENCES knowledge_entities (id) ON DELETE CASCADE,
    relation_type    TEXT             NOT NULL,
    confidence       DOUBLE PRECISION NOT NULL DEFAULT 1.0
                                      CHECK (confidence BETWEEN 0 AND 1),
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (source_entity_id, target_entity_id, relation_type),
    CHECK (source_entity_id <> target_entity_id)
);

-- Traverse edges from either endpoint.
CREATE INDEX IF NOT EXISTS idx_entity_relations_source ON entity_relations (source_entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_relations_target ON entity_relations (target_entity_id);
