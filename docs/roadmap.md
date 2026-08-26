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
| Semantic `findSimilar` retrieval wired through the RAG endpoint | ✅ |
| Source connectors (filesystem, HTTP) feeding the ingest pipeline, default-deny + freshness-aware | ✅ |
| S3 / object-store source connector (AWS SDK v2, injected client, endpoint override) | ✅ |
| Token-accurate chunking — pluggable `TokenCounter`, real BPE tokenizer (jtokkit) replaces `chars/4` | ✅ |
| Testcontainers coverage wired into CI via `maven-failsafe-plugin` (chunk store, document store, graph ITs run at `verify`) | ✅ |

---

## Phase 2 — Knowledge Graph Extraction 🔄 (core complete)

**Goal:** Automatic entity + relation extraction during ingestion.

| Deliverable | Status |
|---|---|
| NER-based entity extraction on ingest (pluggable `EntityExtractor`; dependency-free heuristic default) | ✅ |
| Relation extraction between co-occurring entities (`co_occurs_with`, canonical, bounded, idempotent) | ✅ |
| Extraction wired into the ingest pipeline as best-effort augmentation (`GraphExtractionPort`) | ✅ |
| Graph-augmented retrieval (entity-aware RAG) — `retrieveWithGraph` / `includeGraph`, bounded `GraphContext` (matched + neighbour entities + summary), best-effort | ✅ |
| Model-/LLM-based entity extractor behind the `EntityExtractor` port — `OllamaEntityExtractor` (`extractor=llm`), heuristic fallback | ✅ |
| Entity resolution / de-duplication — `NormalizingEntityResolver` via `ResolvingEntityExtractor` (applied at ingest + query) | ✅ |
| Graph-store evaluation — relational vs. dedicated graph DB; **decision recorded in [ADR-001](decisions/ADR-001-graph-store-relational-vs-dedicated.md)** (stay relational, revisit at multi-hop scale) | ✅ |

---

## Phase 3 — Freshness & Governance 🔄 (core complete)

**Goal:** Full lifecycle governance and change-driven re-indexing.

| Deliverable | Status |
|---|---|
| Checksum-driven re-index (not just interval-based staleness) — the connector-driven ingest path (`DefaultSourceIngestionService`) already SHA-256s fetched content: an unchanged source is `UNCHANGED` (not re-embedded), a changed one re-indexed in place under the same document id | ✅ |
| Per-collection freshness policy — `FreshnessPolicy` (per-collection re-index interval + auto-reingest opt-in, V006 `collection_freshness_policy`) + `FreshnessPolicyStore`; the freshness sweep honours each collection's interval override via a correlated `COALESCE` (global default as fallback); `GET/PUT /api/v1/tenants/{tenantId}/collections/{collectionId}/freshness-policy` | ✅ |
| GDPR erasure across documents, chunks, and graph — `KnowledgeErasurePort`/`DefaultKnowledgeErasureService` + `DELETE /api/v1/tenants/{tenantId}/collections/{collectionId}`; erases chunks → documents → graph entities (relations cascade), tenant+collection-scoped, idempotent, reports counts | ✅ |
| Automatic re-ingestion of STALE documents (scheduled re-fetch via source connector for auto-reingest collections) — `StaleReingestionPort`/`DefaultStaleReingestionService` reads the `autoReingest` scopes (`FreshnessPolicyStore.findAutoReingestScopes`), lists each scope's `STALE` documents (`KnowledgeDocumentStore.findByStatus`) and re-fetches them through their source connector (`SourceIngestionPort`); driven by `StaleReingestionScheduler` (`aether.vault.freshness.reingest-cron`, default 04:30 daily after the freshness sweep), best-effort per document (a failed re-fetch is counted, never stops the sweep), a no-op when no source connector is enabled; metered `aether.vault.reingest.{documents,failures}` | ✅ |

---

## Phase 4 — Kubernetes + Helm 🔄 (core complete)

**Goal:** Production-ready deployment.

| Deliverable | Status |
|---|---|
| Multi-stage Dockerfile (Temurin 21 JRE, non-root uid 1000) | ✅ (scaffolded) |
| Helm chart `vault-infra/helm/aether-vault/` — namespace, service-account (token disabled), configmap (ollama/embedding/freshness/reingest/source/graph config), ClusterIP service (8084), deployment (non-root uid 1000, read-only rootfs, dropped caps, topology spread by zone, startup/liveness/readiness probes, config-checksum rollout), Route (OpenShift), ServiceMonitor, NOTES; secrets never in-chart (pre-existing `existingSecret`) | ✅ |
| HPA (min 2, max 8 replicas, CPU 70%) | ✅ |
| Value sets — vanilla / AWS EKS (ALB + IRSA) / OpenShift (Route + SCC) | ✅ |
| Docker build + Helm release workflows (`helm-release.yml` — lints all value sets + `helm template` dry-run, packages + pushes the chart to GHCR as an OCI artifact on main) | ✅ |

---

## Ecosystem review — future backlog

> Repo-specific items from the [ecosystem improvement backlog](https://github.com/doubts-suplab/aether/blob/main/docs/roadmaps/ecosystem-improvements.md). Planned, not started.
> Feasibility: **S** small · **M** moderate · **L** large. License unchanged (AGPL-3.0).

| Item | Feasibility |
|---|---|
| More enterprise connectors (SharePoint, Confluence, Google Drive, …) | M each / L aggregate |
| Higher-quality entity/relation extraction (LLM-assisted / hybrid) *(Phase 2 follow-up)* | M–L |
| Hybrid search (vector + keyword) | M |
| Multi-modal support | L |
| Re-indexing pipelines | M |
| RAG-quality evaluation (retrieval precision/recall, context usefulness) | M |
| Entity-aware RAG + entity resolution/de-dup *(Phase 2 follow-ups)* | M–L |
