# Aether Vault

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
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents` | Ingest a document (chunk + embed + index) |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents` | List documents in a collection |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/count` | Document + chunk counts |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/{id}` | Fetch a document |
| `DELETE` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/documents/{id}` | Delete a document and its chunks |
| `POST` | `/api/v1/rag/query` | Vector search + bounded context assembly for RAG |
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities` | Create / reinforce a knowledge-graph entity |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities` | List entities (most-mentioned first) |
| `POST` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities/{id}/relations` | Relate two entities |
| `GET` | `/api/v1/tenants/{tenantId}/collections/{collectionId}/entities/{id}/neighbours` | Traverse an entity's neighbours |
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

### Knowledge Freshness

Indexed knowledge ages. A scheduled sweep (default 04:00 daily) flags every `INDEXED` document whose last index is older than the re-index interval (default 30 days) as `STALE`, so a re-indexing job knows what to refresh. Existing chunks stay searchable until they are re-ingested — freshness **marks, never deletes**.

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
| `VAULT_FRESHNESS_ENABLED` | `true` | Toggle the scheduled freshness sweep |
| `VAULT_REINDEX_INTERVAL_DAYS` | `30` | Age beyond which an indexed document is flagged stale |
| `VAULT_FRESHNESS_CRON` | `0 0 4 * * *` | Freshness sweep schedule |
| `SERVER_PORT` | `8084` | HTTP port |
