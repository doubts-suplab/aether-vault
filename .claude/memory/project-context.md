# Project Context — Aether Vault

## Service Identity
- **Name:** Aether Vault (`suplab/aether-vault`)
- **Purpose:** Organisational knowledge platform — document indexing, vector search, RAG pipeline, knowledge graph, knowledge freshness
- **Port:** 8084
- **Database:** `aether_vault` (PostgreSQL 16 + pgvector, separate from Core, Grid, and Memory)
- **Ecosystem layer:** Platform (above Grid runtime and Core cognition; alongside Memory; below domain products)

## Capability Ownership
- **Owns (exclusively):** Knowledge — Document Indexing, Vector Search, Knowledge Graph, RAG Pipeline, Knowledge Freshness
- Personal memory stays in Aether Core; shared team memory stays in Aether Memory; Vault owns documents and knowledge derived from them

## Maven Modules
| Module | Artifact ID | Purpose |
|---|---|---|
| `vault-domain` | `vault-domain` | Domain types + port interfaces (no Spring) |
| `vault-engine` | `vault-engine` | pgvector chunk store, embedding, ingestion, RAG, graph, freshness |
| `vault-api` | `vault-api` | Spring Boot app, REST controllers, Flyway |
| `vault-infra` | `vault-infra` | Docker Compose, k8s, standalone migrations |

## Key Packages
- `com.suplab.aether.vault.domain` — KnowledgeDocument, DocumentChunk, KnowledgeScope, DocumentStatus, EntityType, RetrievalQuery, RetrievedChunk, RagContext, KnowledgeEntity, EntityRelation
- `com.suplab.aether.vault.ports` — KnowledgeDocumentStore, DocumentChunkStore, KnowledgeGraphStore, DocumentIngestionPort, RagPipelinePort, KnowledgeFreshnessPort
- `com.suplab.aether.vault.engine.store` — PGVectorDocumentChunkStore, JdbcKnowledgeDocumentStore
- `com.suplab.aether.vault.engine.embedding` — KnowledgeEmbeddingService (Ollama)
- `com.suplab.aether.vault.engine.ingestion` — TextChunker, DefaultDocumentIngestionService
- `com.suplab.aether.vault.engine.rag` — DefaultRagPipelineService
- `com.suplab.aether.vault.engine.graph` — JdbcKnowledgeGraphStore
- `com.suplab.aether.vault.engine.freshness` — DocumentFreshnessService
- `com.suplab.aether.vault.api` — AetherVaultApplication, controllers, config, freshness scheduler

## Environments
- **Local:** Docker Compose at `vault-infra/docker/docker-compose.yml` (postgres-vault on 5435, app on 8084)
- **CI:** GitHub Actions, pgvector service container
- **Production:** Kubernetes (manifests in `vault-infra/k8s/`; Helm chart — planned Phase 4)

## Current Status
- Phase 0 (scaffold) complete — domain, engine, API, infra, docs, CI all in place; 56 unit tests green
- Next: Phase 1 — Ingestion & Retrieval Engine
