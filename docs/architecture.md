# Aether Vault — Architecture

> **Scope:** This document covers **Aether Vault** (`suplab/aether-vault`) only.
> For the ecosystem-wide view see [suplab/aether](https://github.com/suplab/aether).

---

## 1. Purpose & Position

Aether Vault owns the **Knowledge** capability of the Aether ecosystem: structured, organisational knowledge derived from documents. It is a **platform layer** — above the runtime (Grid) and cognitive (Core) layers, below domain products.

```
Domain Products  (aether-flow, aether-enterprise, domain apps)
        ↓
Platform Layer   →  aether-vault  ← this repo   (alongside aether-memory)
        ↓
Runtime Layer    (aether-grid)
        ↓
Cognitive Layer  (aether-core)
```

Vault is distinct from the memory layers: Aether Core owns *personal* memory, Aether Memory owns *shared team* memory, and Aether Vault owns **documents and the knowledge extracted from them**. The three are complementary and never duplicate one another.

---

## 2. Module Boundaries

| Module | Package root | Responsibility |
|---|---|---|
| `vault-domain` | `com.suplab.aether.vault.domain` / `.ports` | Pure records + port interfaces. No framework. |
| `vault-engine` | `com.suplab.aether.vault.engine.*` | pgvector chunk store, embedding, ingestion, RAG, knowledge graph, freshness. |
| `vault-api` | `com.suplab.aether.vault.api.*` | Spring Boot app, REST controllers, Flyway, scheduling, config. |
| `vault-infra` | — | Docker Compose, Kubernetes manifests, migration reference copies. |

Dependency direction is strictly inward: `vault-api → vault-engine → vault-domain`. The domain never depends on Spring.

---

## 3. Domain Model

```
KnowledgeDocument
  id, tenantId, collectionId, sourceUri, title, contentType, checksum,
  status (PENDING|INDEXED|STALE|FAILED), chunkCount, createdAt, indexedAt, updatedAt
  ├── markIndexed(chunkCount) → status INDEXED, chunkCount, indexedAt        (after ingest)
  ├── markStale()             → status STALE                                (freshness sweep)
  ├── markFailed()            → status FAILED                               (no usable chunks)
  ├── reregister(checksum, title, contentType) → PENDING copy, same identity (changed-source re-index)
  └── isStale(checksum)        → content drift detection

FetchedContent   = (sourceUri, title, contentType, rawText)   — raw content a connector pulls
ContentChecksum  = sha256(text)   — shared freshness fingerprint (inline text + source connectors)
DocumentChunk    = (id, documentId, tenantId, collectionId, ordinal, content, tokenCount)
KnowledgeScope   = (tenantId, collectionId)   — the ownership + isolation key
RetrievalQuery   = (tenantId, collectionId, queryText, topK)
RetrievedChunk   = (documentId, documentTitle, sourceUri, ordinal, content, score)   — read projection
RagContext       = (query, chunks, assembledContext≤4000)   — bounded, prompt-ready
KnowledgeEntity  = (id, scope, name, type, mentionCount)   — graph node
EntityRelation   = (sourceEntityId, targetEntityId, relationType, confidence)   — graph edge
EntityType       = PERSON | ORGANISATION | LOCATION | CONCEPT | PRODUCT | EVENT | OTHER
```

### Ports

| Port | Implementation | Purpose |
|---|---|---|
| `KnowledgeDocumentStore` | `JdbcKnowledgeDocumentStore` | Persist/retrieve document metadata; scoped |
| `DocumentChunkStore` | `PGVectorDocumentChunkStore` | Persist chunks + embeddings; cosine vector search |
| `KnowledgeGraphStore` | `JdbcKnowledgeGraphStore` | Entities (upsert + mention) and relations; neighbour traversal |
| `DocumentIngestionPort` | `DefaultDocumentIngestionService` | Chunk + embed + index a document (idempotent); runs graph extraction after indexing |
| `EntityExtractor` | `HeuristicEntityExtractor` (default) | NER over chunk text → `ExtractedEntity` mentions; dependency-free, model-/LLM-based extractor pluggable |
| `GraphExtractionPort` | `DefaultKnowledgeGraphExtractionService` | Upsert entities + record co-occurrence relations from a document's chunks |
| `DocumentSourceConnector` | `FilesystemSourceConnector`, `HttpSourceConnector`, `S3SourceConnector` | Fetch raw content from a source URI (one scheme each); trust boundary |
| `SourceIngestionPort` | `DefaultSourceIngestionService` | Fetch → checksum → skip-if-unchanged → (re-)index from a source URI |
| `TokenCounter` | `JtokkitTokenCounter` (default), `HeuristicTokenCounter` | Count chunk tokens for context budgeting; tokenizer is replaceable |
| `RagPipelinePort` | `DefaultRagPipelineService` | Embed query → vector search → bounded context; `retrieveWithGraph` adds a bounded knowledge-graph projection (entity-aware RAG, best-effort) |
| `KnowledgeFreshnessPort` | `DocumentFreshnessService` | Set-based re-index staleness sweep |

Connector selection goes through `SourceConnectorRegistry` — **default-deny**: a URI no registered
connector supports is never fetched. Each connector enforces its own safety limits (the filesystem
connector is confined to a configured allowed root; the HTTP connector caps body size and timeout; the
S3 connector uses an injected AWS SDK v2 client with credentials from the standard provider chain — no
hardcoded secrets — and its own size cap). Connectors are opt-in: HTTP is on by default, filesystem and
S3 are off until explicitly enabled.

Chunk token counts come from a pluggable `TokenCounter` (default `JtokkitTokenCounter`, a real BPE
tokenizer) rather than a `chars / 4` estimate — the tokenizer is replaceable through the port, so the
exact downstream-model tokenizer can be swapped in without touching ingestion or RAG.

---

## 4. Data Model (PostgreSQL 16 + pgvector)

| Migration | Object | Notes |
|---|---|---|
| `V001` | `knowledge_documents` | Collection-scoped documents; indexes on `(tenant_id, collection_id, updated_at)`, partial index on `status='INDEXED'` for the freshness sweep |
| `V002` | `document_chunks` | Ordered chunks; `document_id` FK `ON DELETE CASCADE`; unique `(document_id, ordinal)` |
| `V003` | `document_chunks.embedding vector(384)` | IVFFlat cosine index (`lists=100`) |
| `V004` | `knowledge_entities`, `entity_relations` | Graph node/edge tables; entity unique on `(tenant_id, collection_id, name, entity_type)`; relations keyed on `(source, target, relation_type)` |
| `V005` | index `idx_knowledge_documents_source_uri` | `(tenant_id, collection_id, source_uri)` — backs `findBySourceUri` for freshness-aware source re-ingestion |

All embeddings are 384-dim (all-MiniLM-L6-v2), consistent across the ecosystem.

The knowledge graph is persisted as relational adjacency tables rather than a native graph DB, keeping Vault single-store on PostgreSQL like the rest of the ecosystem. This is sufficient for the current shallow (1-hop, mention-ordered) traversals; a move to Apache AGE or a native graph DB is gated on future multi-hop / graph-algorithm needs — see ADR-0008 in `.claude/memory/decisions.md`.

---

## 5. Key Flows

### 5.1 Ingest (document indexing)
1. `POST …/collections/{collectionId}/documents` → compute SHA-256 `checksum`, register `KnowledgeDocument` (PENDING).
2. `DocumentIngestionPort.ingest` deletes any prior chunks, splits text with `TextChunker` (size/overlap configurable), embeds each chunk via Ollama, saves chunks, and marks the document `INDEXED` (or `FAILED` when no chunks are produced). Re-posting the same document re-indexes it in place.
3. After indexing, if a `GraphExtractionPort` is present the pipeline runs **best-effort graph extraction** over the chunks (Phase 2 — see §5.3). A failure there is logged and swallowed: the document stays `INDEXED`.

### 5.1.1 Ingest from a source (connector-driven)
1. `POST …/collections/{collectionId}/documents/from-source` with `{"sourceUri": "…"}`.
2. `SourceConnectorRegistry` resolves a `DocumentSourceConnector` by scheme (default-deny — unknown schemes are rejected `400`); the connector fetches the content into a `FetchedContent`.
3. `DefaultSourceIngestionService` checksums the content and looks up any existing document for that `sourceUri`: an already-`INDEXED` document with a matching checksum is **UNCHANGED** (skipped, not re-embedded); a new source is registered; a changed source is re-registered under the **same document ID** (`reregister`) and re-indexed via `DocumentIngestionPort`.
4. Response reports `outcome` = `INDEXED | UNCHANGED | FAILED`. Connectors are off unless enabled (HTTP on by default; the filesystem connector requires an explicit allowed root).

### 5.2 RAG retrieval
1. `POST /api/v1/rag/query` → `DefaultRagPipelineService` clamps `topK`, embeds `queryText`.
2. `DocumentChunkStore.findSimilar` runs `ORDER BY embedding <=> :query::vector` **scoped to one collection**, joining `knowledge_documents` for citation.
3. Retrieved chunks are assembled into a `RagContext` bounded to `MAX_CONTEXT_CHARS`.
4. **Entity-aware RAG** (opt-in `"includeGraph": true` → `retrieveWithGraph`): the `EntityExtractor` recognises entities in the query text, `KnowledgeGraphStore.findByName` resolves each against the collection graph, and `neighbours` expands matched entities by one edge. The result is a bounded `GraphContext` (`MAX_ENTITIES`, matched-first then by salience) with matched + related entities and a prompt-ready summary, returned alongside the text context. All within the same `tenantId` + `collectionId` scope; **best-effort** — a missing extractor/graph or a failed lookup yields an empty graph and never breaks retrieval.

### 5.3 Knowledge graph
1. `POST …/entities` upserts a node (`ON CONFLICT … mention_count + 1`).
2. `POST …/entities/{id}/relations` verifies both endpoints exist in scope, then inserts an edge.
3. `GET …/entities/{id}/neighbours` traverses edges from either direction, scoped.
4. **Automatic extraction (Phase 2):** at the end of ingestion, `DefaultKnowledgeGraphExtractionService` runs the pluggable `EntityExtractor` over each chunk, upserts every mention (bumping `mention_count`), and records a `co_occurs_with` relation between each distinct pair of entities co-occurring in a chunk. Pairing is bounded to the first *N* entities per chunk (default 8) to avoid a quadratic edge blow-up; edges are written in a canonical direction (smaller UUID → larger) so a symmetric co-occurrence is stored once, and both upsert and relate are idempotent, so re-ingesting a document is safe. The default extractor is dependency-free (`HeuristicEntityExtractor`); a model-/LLM-based extractor is a drop-in behind the `EntityExtractor` port.

### 5.4 Freshness (set-based)
1. Scheduler (`@Scheduled`, default 04:00) → `KnowledgeFreshnessPort.sweep`.
2. Single `UPDATE`: `INDEXED` documents with `indexed_at < NOW() - reindex_interval` become `STALE`.
3. Micrometer: `aether.vault.freshness.stale` counter, `aether.vault.documents.indexed` gauge.

---

## 6. Multi-Tenancy & Isolation

- Every document, chunk, and graph query is scoped by `tenant_id` **and** `collection_id`. There is no cross-collection read path.
- RAG retrieval is confined to the query's collection — a query never sees another collection's knowledge.
- Knowledge-graph edges are rejected unless both endpoints exist in the same scope.

---

## 7. Configuration Surface

Reads from environment variables (never hardcoded). Defaults target local Docker Compose. See `README.md` for the full table. Chunk size/overlap and the re-index interval are all configurable.

---

## 8. Standalone Guarantee

Aether Vault has no compile-time or runtime dependency on Core, Grid, or Memory. It boots, migrates, serves, and runs its freshness sweep entirely on its own PostgreSQL schema (`aether_vault`).
