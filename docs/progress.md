# Aether Vault — Progress Tracker

> **Scope:** This tracker covers **Aether Vault** (`suplab/aether-vault`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 1 — Ingestion & Retrieval Engine 🔄 (in progress)

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Ingestion & Retrieval Engine | 🔄 In progress | 2 |
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

**Tests — 56 unit tests green (Phase 0):**
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

---

## Phase 1 — Ingestion & Retrieval Engine 🔄 (session 2)

**Commit:** `feat(vault): add default-deny source connectors with freshness-aware ingestion`

### What was done — source connectors

The document ingest path previously accepted only inline `text`. Phase 1 adds a **connector-driven
ingest path** so Vault can pull from an actual source, with freshness built in.

**`vault-domain` — new contracts (still framework-free):**
- `FetchedContent` record — the raw `(sourceUri, title, contentType, rawText)` a connector returns
- `ContentChecksum` — shared SHA-256 fingerprint used by both ingest paths (inline text + sources)
- `SourceFetchException` — domain signal for an unsupported/oversized/unreachable source
- `DocumentSourceConnector` port — one scheme per connector; `supports` / `fetch`; a trust boundary
- `SourceIngestionPort` — `ingestFromSource(scope, uri)` → `SourceIngestionResult` with an
  `Outcome` of `INDEXED | UNCHANGED | FAILED`
- `KnowledgeDocument.reregister(checksum, title, contentType)` — a PENDING copy that keeps the
  document's identity so a changed source re-indexes in place (one `sourceUri` → one document)
- `KnowledgeDocumentStore.findBySourceUri(scope, uri)` — the lookup that makes that possible

**`vault-engine` — connectors + orchestration:**
- `FilesystemSourceConnector` — `file:` sources confined to a configured **allowed root**
  (path-traversal and absolute-escape rejected before any read) with a size cap
- `HttpSourceConnector` — `http(s):` over `java.net.http.HttpClient` with request timeout,
  `Content-Length` and decoded-body size caps, non-2xx → `SourceFetchException`
- `SourceConnectorRegistry` — **default-deny** scheme resolution; unknown schemes are never fetched
- `DefaultSourceIngestionService` — fetch → checksum → skip-if-unchanged → register/re-register →
  index; composes the existing `DocumentIngestionPort`
- `JdbcKnowledgeDocumentStore.findBySourceUri` — scoped source lookup

**`vault-api`:**
- `KnowledgeDocumentController` gains `POST …/documents/from-source` (`201` with
  `outcome`; `400` on a bad/unsupported source; `503` when no connector is enabled) and now uses the
  shared `ContentChecksum` (private duplicate removed)
- `VaultApiConfig` wires the HTTP connector (on by default), the filesystem connector (off unless
  `aether.vault.source.filesystem.enabled=true` with an allowed root), the registry, and the source
  ingest service
- `application.yml` — `aether.vault.source.*` config surface (enable flags, timeouts, size caps)

**`vault-infra` / migrations:**
- `V005__index_documents_by_source_uri.sql` — `(tenant_id, collection_id, source_uri)` index backing
  `findBySourceUri` (added to api, engine test, and infra reference copies)

**Tests — 88 unit tests green (was 56):**
- Domain: `FetchedContentTest` (6), `ContentChecksumTest` (5), `KnowledgeDocumentReregisterTest` (3)
- Engine: `FilesystemSourceConnectorTest` (6, incl. path-traversal + size cap), `HttpSourceConnectorTest`
  (4, real JDK `HttpServer`), `SourceConnectorRegistryTest` (4, default-deny),
  `DefaultSourceIngestionServiceTest` (4, new/unchanged/changed/stale)
- IT: `JdbcKnowledgeDocumentStoreIT` gains a `findBySourceUri` scope-isolation case

### Not yet done (Phase 1 remainder)
- S3 / object-store connector · token-accurate chunking · full Testcontainers pass in CI
