# Æ Aether Vault

> Organisational knowledge — the platform layer that ingests documents, serves semantic search and RAG, and builds a knowledge graph, all scoped per tenant and collection.

**Aether Vault** is the knowledge platform of the [Aether ecosystem](https://github.com/suplab/aether). Where [Aether Core](https://github.com/suplab/aether-core) owns *personal* memory and [Aether Memory](https://github.com/suplab/aether-memory) owns *shared team* memory, Aether Vault owns **knowledge derived from documents**: enterprise content ingested, chunked, embedded, and made retrievable for Retrieval-Augmented Generation, with a knowledge graph over the entities it contains.

**Ecosystem position:** Aether Vault is a **platform layer** — it sits above the runtime (Grid) and cognitive (Core) layers and is consumed by higher-level products. It runs standalone; Core, Grid, and Memory are not required to be present.

---

## Quick Start

```bash
cd vault-infra/docker && docker compose up -d
cd ../.. && mvn spring-boot:run -pl vault-api
# Vault API: http://localhost:8084
# Health:    http://localhost:8084/actuator/health
```

## Modules

| Module | Purpose |
|---|---|
| `vault-domain` | Domain types: KnowledgeDocument, DocumentChunk, KnowledgeScope, RetrievalQuery, RagContext, KnowledgeEntity, EntityRelation, port interfaces |
| `vault-engine` | pgvector chunk store, Ollama embedding, document ingestion, RAG pipeline, knowledge-graph store, freshness sweep |
| `vault-api` | Spring Boot REST API (port 8084) + Flyway migrations |
| `vault-infra` | Docker Compose, Kubernetes manifests, standalone Flyway migrations |

## Key API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents` | Ingest a document from inline text (chunk + embed + index) |
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/from-source` | Ingest from a source URI via a connector (freshness-aware) |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents` | List documents in a collection |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/count` | Document + chunk counts |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/{id}` | Fetch a document |
| `DELETE` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/{id}` | Delete a document and its chunks |
| `POST` | `/api/v1/rag/query` | Vector search + bounded context assembly for RAG; `"includeGraph": true` also returns a bounded knowledge-graph projection (entities the query matches + their neighbours + a summary) |
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities` | Create / reinforce a knowledge-graph entity (also populated automatically on ingest) |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities` | List entities (most-mentioned first) |
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities/{id}/relations` | Relate two entities |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities/{id}/neighbours` | Traverse an entity's neighbours |
| `GET`/`PUT` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/freshness-policy` | Per-collection re-index interval + auto-reingest opt-in (GET returns the default when none is set) |
| `DELETE` | `/api/v1/tenants/{tenantId}/collections/{collectionId}` | Right-to-erasure — erase a collection's documents, chunks, and graph (GDPR Art. 17) |
| `GET` | `/actuator/health` | Liveness + readiness probes |

## Knowledge Model

A **collection** (`tenantId` + `collectionId`) is a corpus of documents. Ingestion turns each document into embedded chunks; retrieval and the graph never cross the collection boundary.

| Concept | Description |
|---|---|
| `KnowledgeDocument` | A source document with a lifecycle: `PENDING → INDEXED → STALE / FAILED` |
| `DocumentChunk` | An ordered, overlapping slice of a document — the unit of embedding + vector search |
| `RagContext` | Retrieved chunks + a single length-bounded context string, ready for an agent's prompt |
| `KnowledgeEntity` | A named entity (PERSON, ORGANISATION, LOCATION, CONCEPT, PRODUCT, EVENT, OTHER) with a `mentionCount` |
| `EntityRelation` | A directed, typed, confidence-weighted edge between two entities |

### RAG Pipeline

`POST /api/v1/rag/query` embeds the query, runs cosine-distance vector search **within the requested collection only**, and returns the nearest chunks together with an assembled context string bounded to `RagContext.MAX_CONTEXT_CHARS`. `topK` is clamped to `DefaultRagPipelineService.MAX_TOP_K`. Vault performs the *retrieval* half of RAG; the calling agent performs generation.

### Source Connectors

Documents can be ingested from inline text *or* pulled from a source URI. `POST …/documents/from-source` with `{"sourceUri": "…"}` resolves a **connector** by scheme through a **default-deny registry** — a URI no enabled connector supports is rejected (`400`), never fetched. Two connectors ship:

- **Filesystem** (`file:`) — confined to a configured **allowed root**; path-traversal and absolute-path escapes are rejected before any read. Off unless enabled with an explicit root.
- **HTTP(S)** (`http:` / `https:`) — fetched with a request timeout and a response-size cap; non-2xx is an error. On by default.
- **S3 / object store** (`s3:`) — `s3://bucket/key` via the AWS SDK v2 with credentials from the standard provider chain (no hardcoded secrets); supports an endpoint override for S3-compatible stores (e.g. MinIO). Off by default.

Source ingestion is **freshness-aware**: re-pointing at the same `sourceUri` checksums the fetched content and reports an `outcome` — `UNCHANGED` (identical content, *not* re-embedded), `INDEXED` (new or changed, re-indexed under the **same document ID**), or `FAILED`. One source URI maps to one stable document across re-ingestions.

Chunk token counts (for context budgeting) come from a pluggable `TokenCounter` — a real BPE tokenizer (jtokkit `cl100k_base`) by default, replaceable via config, rather than a `chars/4` estimate.

### Knowledge Freshness

Indexed knowledge ages. A scheduled sweep (default 04:00 daily) flags every `INDEXED` document whose last index is older than the re-index interval (default 30 days) as `STALE`, so a re-indexing job knows what to refresh. Existing chunks stay searchable until they are re-ingested — freshness **marks, never deletes**.

The re-index interval is **per-collection**: `PUT …/freshness-policy` with `{"reindexIntervalDays": 7, "autoReingest": true}` overrides the global default for one collection (a fast-moving handbook weekly, a stable archive yearly). The sweep applies each collection's override, falling back to the global default when none is set.

Collections that opt in with `autoReingest: true` are **auto-refreshed**: a second scheduled sweep (default 04:30 daily, just after the staleness sweep) lists each opted-in collection's `STALE` documents and re-fetches them through their source connector, re-indexing changed content in place. It is best-effort per document (a failed re-fetch is counted, never stops the sweep) and a no-op when no source connector is enabled — Vault still runs standalone. Tune with `VAULT_REINGEST_CRON`, `VAULT_REINGEST_MAX_SCOPES`, `VAULT_REINGEST_MAX_PER_SCOPE`; observe via the `aether.vault.reingest.{documents,failures}` counters.

### Governance — right to erasure (GDPR Art. 17)

A whole collection's derived knowledge can be erased on request: `DELETE …/collections/{collectionId}` removes the collection's documents, embedded chunks, and knowledge-graph entities (relations cascade) in one governed, tenant-scoped, idempotent operation, returning the counts removed. This is Vault's only bulk-delete path — freshness only marks.

### Deployment (Kubernetes / Helm)

A production Helm chart lives at `vault-infra/helm/aether-vault/` (mirroring the Core and Flow charts): namespace, service-account (token disabled), configmap, ClusterIP service (8084), a hardened deployment (non-root uid 1000, read-only rootfs, dropped capabilities, topology spread by zone, startup/liveness/readiness probes, config-checksum rollout), HPA (min 2 / max 8 / CPU 70%), ingress, OpenShift Route, and a ServiceMonitor. Value sets ship for vanilla Kubernetes, AWS EKS (ALB + IRSA), and OpenShift (Route + SCC). Secrets are never in-chart — the pods read them from a pre-existing `existingSecret` (`postgres-url`, `postgres-user`, `postgres-password`). The `helm-release.yml` workflow lints every value set, dry-runs `helm template`, and packages + pushes the chart to GHCR as an OCI artifact on `main`.

## Ecosystem

```
Aether Ecosystem
├── aether          (suplab/aether)         — philosophy, standards, ADRs
├── aether-core     (suplab/aether-core)    — personal cognitive engine (port 8082)
├── aether-grid     (suplab/aether-grid)    — enterprise agent mesh (ports 8080/8081)
├── aether-memory   (suplab/aether-memory)  — shared team memory platform (port 8083)
└── aether-vault    (suplab/aether-vault)   ← you are here — knowledge platform (port 8084)
```

Aether Vault owns the **Knowledge** capability exclusively. Personal memory stays in Core, shared memory stays in Memory; Vault owns documents and the knowledge derived from them.

---

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/aether_vault` | PostgreSQL connection |
| `POSTGRES_USER` | `aether` | DB username |
| `POSTGRES_PASSWORD` | `aether` | DB password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama embedding endpoint |
| `EMBEDDING_ENABLED` | `true` | Toggle embedding (zero-vector fallback when off) |
| `EMBEDDING_MODEL` | `all-minilm` | Embedding model name |
| `VAULT_CHUNK_SIZE` | `1000` | Characters per chunk |
| `VAULT_CHUNK_OVERLAP` | `150` | Overlap characters between adjacent chunks |
| `VAULT_SOURCE_ENABLED` | `true` | Toggle the connector-driven `/from-source` ingest path |
| `VAULT_SOURCE_HTTP_ENABLED` | `true` | Enable the HTTP(S) source connector |
| `VAULT_SOURCE_HTTP_TIMEOUT_SECONDS` | `15` | HTTP fetch request timeout |
| `VAULT_SOURCE_HTTP_MAX_BYTES` | `8388608` | Max HTTP response body size (8 MiB) |
| `VAULT_SOURCE_FS_ENABLED` | `false` | Enable the filesystem source connector (requires an allowed root) |
| `VAULT_SOURCE_FS_ALLOWED_ROOT` | _(unset)_ | Absolute directory tree `file:` sources are confined to |
| `VAULT_SOURCE_FS_MAX_BYTES` | `8388608` | Max file size for `file:` sources (8 MiB) |
| `VAULT_SOURCE_S3_ENABLED` | `false` | Enable the S3 / object-store source connector |
| `VAULT_SOURCE_S3_REGION` | `us-east-1` | AWS region for the S3 client |
| `VAULT_SOURCE_S3_ENDPOINT` | _(unset)_ | Endpoint override for an S3-compatible store (e.g. MinIO) |
| `VAULT_SOURCE_S3_MAX_BYTES` | `8388608` | Max object size for `s3:` sources (8 MiB) |
| `VAULT_TOKENIZER` | `bpe` | Chunk token counter: `bpe` (real tokenizer) or `heuristic` |
| `VAULT_FRESHNESS_ENABLED` | `true` | Toggle the scheduled freshness sweep |
| `VAULT_REINDEX_INTERVAL_DAYS` | `30` | Global fallback age beyond which an indexed document is flagged stale (a per-collection `freshness-policy` override wins when set) |
| `VAULT_FRESHNESS_CRON` | `0 0 4 * * *` | Freshness sweep schedule |
| `VAULT_REINGEST_CRON` | `0 30 4 * * *` | Auto-reingestion sweep schedule (STALE re-fetch for `autoReingest` collections) |
| `VAULT_REINGEST_MAX_SCOPES` | `100` | Max auto-reingest collections processed per sweep |
| `VAULT_REINGEST_MAX_PER_SCOPE` | `50` | Max STALE documents re-ingested per collection per sweep |
| `SERVER_PORT` | `8084` | HTTP port |
