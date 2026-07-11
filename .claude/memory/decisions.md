# Architecture Decisions — Aether Vault

> Chronological log of significant decisions. Promote any that need full rationale to an ADR under `docs/adr/`.

## ADR-0001 — Standalone platform, mirroring the ecosystem structure (Phase 0)
- **Decision:** Bootstrap Aether Vault as an independent Maven multi-module project (`vault-domain`, `vault-engine`, `vault-api`, `vault-infra`) mirroring the shape and conventions of `aether-core` and `aether-memory`.
- **Rationale:** The ecosystem standardises on Java 21 / Spring Boot 3.3 / pgvector / Flyway. Consistency makes the platform legible, reviewable, and integrable. Divergent stacks were rejected for fragmenting the ecosystem.
- **Consequence:** Reuses the ecosystem's golden rules, CI patterns, Docker/k8s patterns, and 384-dim embedding contract.

## ADR-0002 — Collection as the ownership unit; KnowledgeScope = (tenantId, collectionId) (Phase 0)
- **Decision:** Knowledge is owned by a collection within a tenant, keyed by `KnowledgeScope`. Every query is scoped by both.
- **Rationale:** A collection (a corpus) is the natural knowledge boundary — the Vault analogue of Memory's team. It preserves strict multi-tenant isolation while grouping related documents.

## ADR-0003 — Document lifecycle with checksum-based freshness (Phase 0)
- **Decision:** `KnowledgeDocument` moves through `PENDING → INDEXED → STALE/FAILED`. A SHA-256 content checksum detects source drift (`isStale`); a scheduled sweep flags indexed documents past a re-index interval `STALE`.
- **Rationale:** Knowledge decays differently from memory — it does not fade, it goes *out of date*. Freshness marks documents for re-indexing rather than reducing a strength score.

## ADR-0004 — RAG returns bounded, collection-scoped context (Phase 0)
- **Decision:** The RAG pipeline retrieves only within the query's collection, clamps `topK` to `MAX_TOP_K`, and assembles a context capped at `RagContext.MAX_CONTEXT_CHARS`. Vault does retrieval + assembly; generation is the caller's.
- **Rationale:** A single query must not pull an unbounded slice of a corpus, and knowledge must never leak across collections. Keeping generation out of Vault preserves the model-agnostic boundary (LLM calls live in the consuming agent / Grid).

## ADR-0005 — Idempotent ingestion (chunks replaced, never accumulated) (Phase 0)
- **Decision:** `DefaultDocumentIngestionService` deletes a document's existing chunks before writing new ones; empty input marks the document `FAILED`.
- **Rationale:** Re-indexing a changed source must not leave orphaned stale chunks that would pollute retrieval.

## ADR-0006 — Knowledge graph edges confined to a scope (Phase 0)
- **Decision:** `JdbcKnowledgeGraphStore.relate` verifies both endpoints exist within the given `KnowledgeScope` before inserting an edge; entities are unique per `(tenant, collection, name, type)` and upsert bumps `mention_count`.
- **Rationale:** The graph is a per-collection structure; an edge that bridged collections would breach isolation. Mention count gives cheap salience without a separate scoring pass.

## ADR-0007 — Char-based chunking now, tokenizer later (Phase 0)
- **Decision:** Ingest uses a deterministic character-window `TextChunker` with overlap and a `chars/4` token estimate.
- **Rationale:** Deterministic, dependency-free, and unit-testable for the scaffold. A model-accurate tokenizer is a Phase 1 concern and slots in behind the same interface.
