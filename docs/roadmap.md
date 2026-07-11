# Aether Vault — Development Roadmap

> **Scope:** This roadmap covers Aether Vault only.
> For the ecosystem roadmap see [suplab/aether](https://github.com/suplab/aether).

---

## Phase 0 — Scaffold ✅

**Goal:** Standalone platform bootstrapped. Independent Maven multi-module, Spring Boot 3.3.5, all golden rules enforced, ecosystem relationship established.

| Deliverable | Status |
|---|---|
| Independent parent POM (`aether-vault-parent`) | ✅ |
| 4 Maven modules: vault-domain, vault-engine, vault-api, vault-infra | ✅ |
| Domain model: KnowledgeDocument, DocumentChunk, KnowledgeScope, RetrievalQuery, RagContext, RetrievedChunk, KnowledgeEntity, EntityRelation | ✅ |
| Port interfaces: KnowledgeDocumentStore, DocumentChunkStore, KnowledgeGraphStore, DocumentIngestionPort, RagPipelinePort, KnowledgeFreshnessPort | ✅ |
| `PGVectorDocumentChunkStore` adapter (cosine search, document-joined projections) | ✅ |
| `KnowledgeEmbeddingService` (Ollama all-MiniLM-L6-v2, 384-dim, graceful fallback) | ✅ |
| `TextChunker` + `DefaultDocumentIngestionService` (idempotent chunk/embed/index) | ✅ |
| `DefaultRagPipelineService` (bounded context, topK clamp, collection-scoped) | ✅ |
| `JdbcKnowledgeGraphStore` (entity upsert/mention, relations, neighbours) | ✅ |
| `DocumentFreshnessService` (set-based stale sweep) | ✅ |
| REST: Document, RAG, KnowledgeGraph controllers + freshness scheduler | ✅ |
| Flyway migrations V001–V004 | ✅ |
| Docker Compose + Kubernetes manifests | ✅ |
| GitHub Actions CI + quality-gate + docker-build | ✅ |
| CLAUDE.md + .claude/memory/ + .claude/agents/ | ✅ |
| Docs: README, index.html, architecture.md, roadmap.md, progress.md | ✅ |

---

## Phase 1 — Ingestion & Retrieval Engine

**Goal:** Document indexing and RAG fully operational end-to-end under integration tests.

| Deliverable | Status |
|---|---|
| Semantic `findSimilar` retrieval wired through the RAG endpoint | ⏳ |
| Source connectors (filesystem, HTTP, S3) feeding the ingest pipeline | ⏳ |
| Token-accurate chunking (model tokenizer instead of char estimate) | ⏳ |
| Testcontainers coverage green in CI (chunk store, document store, graph) | ⏳ |

---

## Phase 2 — Knowledge Graph Extraction

**Goal:** Automatic entity + relation extraction during ingestion.

| Deliverable | Status |
|---|---|
| NER-based entity extraction on ingest | ⏳ |
| Relation extraction between co-occurring entities | ⏳ |
| Graph-augmented retrieval (entity-aware RAG) | ⏳ |
| Entity resolution / de-duplication | ⏳ |
| Graph-store evaluation — recursive CTE → Apache AGE → native graph DB, gated on traversal depth/latency once extraction produces a dense graph (see ADR-0008) | ⏳ |

---

## Phase 3 — Freshness & Governance

**Goal:** Full lifecycle governance and change-driven re-indexing.

| Deliverable | Status |
|---|---|
| Checksum-driven re-index (not just interval-based staleness) | ⏳ |
| Automatic re-ingestion of STALE documents | ⏳ |
| Per-collection freshness policy | ⏳ |
| GDPR erasure across documents, chunks, and graph | ⏳ |

---

## Phase 4 — Kubernetes + Helm

**Goal:** Production-ready deployment.

| Deliverable | Status |
|---|---|
| Multi-stage Dockerfile (Temurin 21 JRE, non-root uid 1000) | ✅ (scaffolded) |
| Helm chart `vault-infra/helm/aether-vault/` | ⏳ |
| HPA (min 2, max 8 replicas) | ✅ (manifest) |
| Docker build + Helm release workflows | ⏳ |
