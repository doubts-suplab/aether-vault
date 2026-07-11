# Aether Vault — Progress Tracker

> **Scope:** This tracker covers **Aether Vault** (`suplab/aether-vault`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 0 — Scaffold ✅ (complete)

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Ingestion & Retrieval Engine | ⏳ Planned | — |
| 2 | Knowledge Graph Extraction | ⏳ Planned | — |
| 3 | Freshness & Governance | ⏳ Planned | — |
| 4 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 0 — Scaffold ✅

**Commit:** `feat(vault): scaffold Aether Vault — organisational knowledge platform`

### What was done

**Maven project:**
- `pom.xml` — independent parent POM (`aether-vault-parent`), Spring Boot 3.3.5 BOM, Java 21, `--enable-preview`, `-parameters` flags, JaCoCo config (pluginManagement, mirroring the ecosystem)
- 4 modules: `vault-domain`, `vault-engine`, `vault-api`, `vault-infra`

**`vault-domain` — pure domain (no Spring):**
- `KnowledgeDocument` record: lifecycle (`PENDING → INDEXED → STALE/FAILED`), checksum-based `isStale`, `markIndexed/markStale/markFailed`
- `DocumentChunk` record: ordered, scoped chunk; `create(document, ordinal, content, tokenCount)`
- `KnowledgeScope` record: `(tenantId, collectionId)` ownership + isolation key
- `DocumentStatus` + `EntityType` enums
- `RetrievalQuery` + `RetrievedChunk` + `RagContext` records: RAG request, read projection, bounded context (`MAX_CONTEXT_CHARS=4000`, `assemble()`)
- `KnowledgeEntity` (mention count) + `EntityRelation` (typed, confidence-weighted) records
- Ports: `KnowledgeDocumentStore`, `DocumentChunkStore`, `KnowledgeGraphStore`, `DocumentIngestionPort`, `RagPipelinePort`, `KnowledgeFreshnessPort`

**`vault-engine` — adapters + services:**
- `PGVectorDocumentChunkStore`: cosine similarity (`<=> :query::vector`), document-joined `RetrievedChunk` projection, explicit column lists, `NamedParameterJdbcTemplate`, `ON CONFLICT` upsert
- `KnowledgeEmbeddingService`: Ollama REST client, 384-dim, zero-vector fallback on error
- `TextChunker`: deterministic, overlapping, character-based chunking (pure utility)
- `DefaultDocumentIngestionService`: idempotent chunk → embed → store, mark indexed/failed
- `DefaultRagPipelineService`: embed → collection-scoped vector search → bounded context, `MAX_TOP_K=20`
- `JdbcKnowledgeGraphStore`: entity upsert-with-mention, in-scope relation guard, undirected neighbour traversal
- `DocumentFreshnessService`: set-based `INDEXED → STALE` sweep past the re-index interval

**`vault-api` — Spring Boot application:**
- `AetherVaultApplication`: port 8084, `scanBasePackages = "com.suplab.aether.vault"`
- `KnowledgeDocumentController`: POST ingest, GET list, GET count, GET one, DELETE — all tenant+collection scoped; SHA-256 checksum
- `RagController`: `POST /api/v1/rag/query`
- `KnowledgeGraphController`: entity upsert/list, relation create, neighbour traversal
- `KnowledgeFreshnessScheduler` + `KnowledgeFreshnessConfig`: `@Scheduled` sweep, Micrometer metrics, opt-out flag
- `VaultApiConfig`: wires all engine beans via constructor injection; embedding `@ConditionalOnProperty`
- `application.yml`: port 8084, Flyway enabled, Ollama + ingestion + freshness config; `Dockerfile` (multi-stage, non-root)

**`vault-infra` — infrastructure:**
- Flyway migrations V001–V004 (knowledge_documents, document_chunks, pgvector embeddings, knowledge graph)
- `docker/docker-compose.yml`: postgres-vault (port 5435) + aether-vault (port 8084)
- `k8s/`: namespace, deployment (probes, non-root, read-only fs), service + HPA + ConfigMap + Secret template

**Tests — 56 unit tests green:**
- `KnowledgeDocumentTest` (14), `DocumentChunkTest` (6), `KnowledgeGraphDomainTest` (8), `RagContextAndQueryTest` (7), `KnowledgeScopeTest` (5)
- `TextChunkerTest` (7), `DefaultRagPipelineServiceTest` (4), `DefaultDocumentIngestionServiceTest` (3)
- `KnowledgeFreshnessSchedulerTest` (2): counter accumulation vs gauge-latest
- Testcontainers ITs (CI, `pgvector/pgvector:pg16`): `PGVectorDocumentChunkStoreIT` (5), `JdbcKnowledgeDocumentStoreIT` (5), `JdbcKnowledgeGraphStoreIT` (5)

**`.claude/` setup:**
- Specialist agent definitions + memory files seeded with Vault context
- `CLAUDE.md` project brief, `aether.manifest.yaml`

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`, `docker-build.yml`
