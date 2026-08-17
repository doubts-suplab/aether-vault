# ADR-001 — Knowledge-graph store: relational edge table vs. a dedicated graph database

- **Status:** Accepted
- **Date:** 2026-08
- **Context phase:** Phase 2 — Knowledge Graph Extraction (follow-up: graph-store evaluation)
- **Deciders:** Aether Vault maintainers

---

## Context

Aether Vault builds a per-collection knowledge graph — `KnowledgeEntity` nodes and typed,
confidence-weighted `EntityRelation` edges — from the entities extracted during ingestion, and now
serves it at query time through **entity-aware RAG** (`retrieveWithGraph`). The graph is persisted by
`JdbcKnowledgeGraphStore` in **two PostgreSQL tables** alongside the rest of Vault's data:

- `knowledge_entities` — `(id, tenant_id, collection_id, name, type, mention_count, created_at)`,
  uniqueness on `(tenant_id, collection_id, name, type)`, upsert bumps `mention_count`.
- `entity_relations` — `(source_id, target_id, type, confidence)`, edges stored in a canonical
  direction so a symmetric co-occurrence is recorded once.

The access patterns Vault actually issues today are shallow and scoped:

1. **Resolve** an entity by `(scope, name, type)` — `findByName` (entity-aware RAG, extraction upsert).
2. **List** a collection's entities by salience — `findEntities` ordered by `mention_count`.
3. **One-hop neighbours** of an entity — `neighbours` (entity-aware RAG expansion).
4. **Upsert** node / **insert** edge — idempotent writes during ingestion.

Every operation is confined to a single `tenant_id` + `collection_id` scope — the graph never spans
collections. There is **no multi-hop traversal, no shortest-path, no pattern/subgraph matching, and no
global analytics** in the product today.

The question: should the graph move to a **dedicated graph database** (e.g. Neo4j, JanusGraph, Neptune)
or stay in the **relational edge table** it lives in now?

## Decision

**Stay on the relational edge table in PostgreSQL for now.** Keep the `KnowledgeGraphStore` port as the
single seam so a dedicated graph engine can be introduced later without touching callers, and revisit
this decision when (and only when) a concrete trigger below is hit.

## Rationale

### Why relational is the right default here

- **Access patterns are shallow.** Resolve-by-key, list-by-salience, and one-hop neighbours are exactly
  what a B-tree index + a single join table serve well. `neighbours` is one indexed query over
  `entity_relations`; none of Vault's reads need iterative traversal, where a native graph engine's
  index-free adjacency would win.
- **Single-store discipline.** The whole Aether ecosystem is deliberately single-store on PostgreSQL
  (+ pgvector) — Core, Memory, Vault, Flow all follow it. Vault already runs pgvector for chunk search;
  co-locating the graph means **one database to run, back up, secure, and reason about**, and lets a
  future feature *join graph and vector data in one query* (e.g. "chunks whose document mentions entity
  X") without a cross-store fan-out.
- **Transactional consistency for free.** Entity upsert, edge insert, and chunk writes during ingestion
  share one transaction and one `tenant_id`/`collection_id` scoping rule. A separate graph store would
  need its own tenancy enforcement and a dual-write/outbox to stay consistent with the relational data.
- **Operational cost.** A dedicated graph DB is another clustered stateful service — more infra, another
  failure domain, another set of credentials and network policy, and (for managed options like Neptune)
  cloud lock-in. That cost is not justified by the current read shapes.
- **Scale headroom.** Per-collection graphs are bounded (a collection is one corpus). Indexed lookups
  and one-hop joins on a partitioned/indexed `entity_relations` table scale comfortably to the sizes a
  single collection produces.

### What we give up (and why it's acceptable today)

- **Deep multi-hop traversal / pathfinding.** Recursive CTEs can do bounded traversal in PostgreSQL but
  degrade past a few hops; a native graph engine is materially better here. Vault does no multi-hop
  today, so this is latent, not lost.
- **Pattern / subgraph matching (Cypher/Gremlin).** Expressive graph queries are awkward in SQL. Not a
  current requirement.
- **Graph algorithms** (PageRank-style centrality, community detection). Vault approximates salience
  cheaply with `mention_count`; full analytics are out of scope.

### The seam that makes this reversible

All graph access already goes through the `KnowledgeGraphStore` **port**. Introducing a dedicated engine
later means adding one adapter (e.g. `Neo4jKnowledgeGraphStore`) behind that port and a config switch —
no change to `DefaultKnowledgeGraphExtractionService`, `DefaultRagPipelineService`, or the controllers.
This is the same pluggability the `EntityExtractor` (heuristic ↔ Ollama) and `TokenCounter` seams use.

## Revisit triggers

Move to (or add) a dedicated graph store when a real need appears:

1. **Multi-hop / pathfinding** enters the product — e.g. "how is entity A connected to entity B",
   2+‑hop neighbourhoods, or influence propagation — and recursive-CTE latency is measured to be
   unacceptable.
2. **Pattern/subgraph queries** become a feature (Cypher/Gremlin-style expressiveness).
3. **Graph analytics** (centrality, communities) are needed beyond `mention_count`.
4. **Edge volume** per collection grows to where indexed joins on `entity_relations` miss latency SLOs
   after normal tuning (indexing, partitioning by scope).

When triggered, prefer an **embeddable or single-node** engine first (to preserve the single-store
operational simplicity as far as possible) and introduce it behind the existing port, migrating one
capability at a time.

## Consequences

- **Positive:** no new infrastructure; one database to operate; transactional consistency across graph,
  document, and vector data; graph+vector co-queries remain possible; the decision is cheaply reversible
  behind `KnowledgeGraphStore`.
- **Negative:** deep traversal, pattern matching, and graph analytics are not available without the
  future adapter; some future graph features will be more work in SQL than in a native engine.
- **Neutral:** entity resolution/normalisation (`NormalizingEntityResolver`) reduces node fragmentation
  regardless of the backing store, so it is orthogonal to this decision.

## References

- `vault-engine/.../graph/JdbcKnowledgeGraphStore.java` — the relational implementation.
- `vault-domain/.../ports/KnowledgeGraphStore.java` — the port this decision keeps stable.
- `docs/architecture.md` §Knowledge graph, §RAG retrieval (entity-aware RAG).
