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

## ADR-0008 — Knowledge graph on relational adjacency tables, not a native graph DB (Phase 0)
- **Decision:** Persist the knowledge graph as relational adjacency tables (`knowledge_entities`, `entity_relations`) in PostgreSQL behind the `KnowledgeGraphStore` port — no native graph database (Neo4j, etc.).
- **Rationale:**
  - **Ecosystem consistency:** every Aether runtime repo (Core, Grid, Memory) is single-store on PostgreSQL 16 + pgvector; no graph DB exists anywhere in the ecosystem. Adding one would fragment the standardised stack, break Vault's "boots standalone on its own Postgres schema" guarantee, and add a second stateful service to operate, back up, and secure.
  - **Sufficiency:** the Phase-0 graph is manual-write and its queries are shallow — entity upsert and 1-hop neighbour traversal ordered by mention count. Adjacency tables serve this cheaply; deeper traversal is available today via recursive CTEs.
  - The port abstraction (`KnowledgeGraphStore`) means the backing store can change later without touching callers.
- **Revisit trigger:** re-evaluate when the graph needs **low-latency variable-length multi-hop traversal, path-finding, or graph algorithms** (PageRank, community detection) over high-degree nodes — realistic once Phase-2 automatic entity/relation extraction produces a dense graph.
- **Migration ladder (single-store first):** recursive-CTE traversal in PostgreSQL → **Apache AGE** (openCypher inside PostgreSQL, keeps the single-store guarantee) → a dedicated native graph DB only if AGE cannot meet traversal-depth/latency targets. Any move past step 1 requires its own ADR.
