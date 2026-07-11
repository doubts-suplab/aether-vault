# Approved Patterns — Aether Vault

## Persistence
- **pgvector cosine search:** `ORDER BY embedding <=> :query::vector` with the query vector serialised as `[x,y,…]` and cast `::vector`. Explicit column lists always.
- **Document-joined projection:** `findSimilar` joins `knowledge_documents` to return `RetrievedChunk` (title + source URI + distance) — a read model, not a raw chunk.
- **UPSERT:** `INSERT … ON CONFLICT (id) DO UPDATE SET …` for idempotent chunk/document saves; `ON CONFLICT (…) DO UPDATE SET mention_count = mention_count + 1 RETURNING …` for entity upsert.
- **FK cascade:** `document_chunks.document_id … ON DELETE CASCADE` — deleting a document removes its chunks.
- **Set-based freshness:** the sweep is a single `UPDATE` marking `INDEXED` rows past the re-index interval `STALE`. No per-row round trips.

## Domain
- **Immutable records** with compact-constructor validation; mutation returns a new instance (`markIndexed`, `markStale`, `markFailed`, `mention`).
- **Factory methods:** `KnowledgeDocument.create(...)`, `DocumentChunk.create(document, …)`, `KnowledgeEntity.create(...)`, `EntityRelation.of(...)`, `RagContext.assemble(...)`, `KnowledgeScope.of(...)`.
- **Bounded projections:** `RagContext.assemble` caps context at `MAX_CONTEXT_CHARS`; `RetrievedChunk` carries only what a caller needs to cite.

## Spring wiring
- **Constructor injection only.** Beans declared in `VaultApiConfig` / `KnowledgeFreshnessConfig`; adapters live in `vault-engine` and are pure (no Spring annotations).
- **Optional beans:** `Optional<KnowledgeEmbeddingService>` so the app runs with embeddings disabled (`@ConditionalOnProperty`, zero-vector fallback) — ingestion, graph, and document management stay operational.
- **Config via `@Value` with env-backed defaults** in `application.yml` (chunk size/overlap, re-index interval, cron).

## Ingestion
- **Idempotent pipeline:** delete prior chunks → chunk (`TextChunker`, overlap) → embed → save → `markIndexed`; empty text → `markFailed`.
- **Cheap token estimate:** `chars / 4` per chunk — intentionally approximate, replaced by a real tokenizer in a later phase.

## API
- **Tenant+collection scoping in the path:** `/api/v1/tenants/{tenantId}/collections/{collectionId}/…`; RAG at `/api/v1/rag/query` carries the scope in the body.
- **Lenient request parsing:** tolerant `asString`/`asInt`/`asDouble` helpers; validation errors → 400 with an `error` field.
- **Always-usable responses:** list/count return empty rather than 404; missing document → explicit 404.
- **Null-safe views:** document views use a `HashMap` because `indexedAt` may be null (`Map.of` rejects nulls).

## Testing
- **Unit tests** (`*Test`, surefire) for domain logic and pure services (in-memory fake stores, `SimpleMeterRegistry`).
- **Integration tests** (`*IT`, Testcontainers `pgvector/pgvector:pg16`, Flyway-migrated) for JDBC/pgvector adapters — run in CI where Docker is present.
