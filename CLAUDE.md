# CLAUDE.md — Aether Vault Project Brief

> Read this at the start of every session. Single source of truth for what this project is, how it is built, and what rules apply.

---

## What This Project Is

**Aether Vault** (`suplab/aether-vault`) is the **knowledge platform** of the Aether ecosystem — the layer that owns **organisational knowledge**: ingesting documents, chunking and embedding them, serving semantic search and a Retrieval-Augmented Generation (RAG) pipeline, building a knowledge graph of entities and relations, and keeping all of it fresh.

> **Ecosystem navigation**
>
> | Layer | Repo | Purpose |
> |---|---|---|
> | Aether Philosophy | [`suplab/aether`](https://github.com/suplab/aether) | The vision: cognitive fabric connecting humans, memory, and AI |
> | **Aether Core** | [`suplab/aether-core`](https://github.com/suplab/aether-core) | Personal cognitive engine — individual memory, reasoning, emotional context |
> | **Aether Grid** | [`suplab/aether-grid`](https://github.com/suplab/aether-grid) | Distributed agent mesh — enterprise API governance platform |
> | **Aether Memory** | [`suplab/aether-memory`](https://github.com/suplab/aether-memory) | Shared team/org memory platform — federation, per-tenant policy |
> | **Aether Vault** | `suplab/aether-vault` ← **you are here** | Knowledge platform — document indexing, vector search, RAG, knowledge graph |

**Capability owned (exclusively):** *Knowledge* — Document Indexing, Vector Search, Knowledge Graph, RAG Pipeline, Knowledge Freshness. Personal memory stays in Aether Core; shared team memory stays in Aether Memory. Vault owns **documents and the knowledge derived from them**, not memories.

**Current status:** Phase 1 — Ingestion & Retrieval Engine ✅ complete. Phase 2 — Knowledge Graph Extraction 🔄 core complete: automatic entity + co-occurrence relation extraction during ingestion via a pluggable `EntityExtractor` (dependency-free heuristic default) and `GraphExtractionPort`, wired as best-effort augmentation to the ingest pipeline; Testcontainers ITs. Follow-up: entity-aware RAG, a model-/LLM-based extractor, entity resolution/de-dup, graph-store evaluation.

**One runnable application:**
- `vault-api` — Knowledge Platform API (port 8084)

**Three library modules:** `vault-domain`, `vault-engine`, `vault-infra`

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 (`jakarta.*` exclusively — never `javax.*`) |
| Database | PostgreSQL 16 + pgvector extension |
| Vector Store | pgvector (384-dim, all-MiniLM-L6-v2) |
| Embedding | all-MiniLM-L6-v2 via Ollama (`/api/embeddings`) |
| LLM Runtime | Ollama (local, model-agnostic) |
| DB Migrations | Flyway (classpath:db/migration in vault-api) |
| Build | Maven (multi-module, Java 21, --enable-preview) |
| Local Dev | Docker Compose (`vault-infra/docker/docker-compose.yml`) |
| CI/CD | GitHub Actions (OIDC, SHA-pinned actions) |

---

## Bounded Context

- Package root: `com.suplab.aether.vault`
- Port: **8084** (Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083, Vault=8084)
- Database: `aether_vault` (separate schema — data isolation from Core, Grid, and Memory)
- REST API surface:
  - `.../collections/{collectionId}/documents` — collection-scoped document indexing + management
  - `POST /api/v1/rag/query` — retrieval + bounded context assembly for RAG
  - `.../collections/{collectionId}/entities` — knowledge-graph entities and relations

---

## Module Structure

```
aether-vault-parent (pom.xml)
├── vault-domain   — domain types (KnowledgeDocument, DocumentChunk, KnowledgeEntity, RAG projections) + port interfaces
├── vault-engine   — pgvector chunk store, Ollama embedding, ingestion, RAG, knowledge graph, freshness services
├── vault-api      — Spring Boot REST API, Flyway migrations, config
└── vault-infra    — Docker Compose, k8s manifests, migration reference copies (no Java sources)
```

### Dependency Graph

```
vault-api
  ├── vault-domain
  └── vault-engine
        └── vault-domain
vault-infra  (no Java)
```

`vault-domain` has no framework dependency — pure Java 21 records and interfaces.

---

## Core Domain Concepts

| Concept | Meaning |
|---|---|
| **KnowledgeDocument** | A source document registered in a collection. Tracked through a `PENDING → INDEXED → STALE/FAILED` lifecycle; a content `checksum` drives freshness. |
| **KnowledgeScope** | The `tenantId` + `collectionId` ownership key — the multi-tenancy boundary. |
| **DocumentChunk** | A contiguous slice of a document, the unit of embedding and vector search. |
| **RAG Pipeline** | Embeds a query, runs vector search within one collection, assembles a length-bounded context for an agent's prompt. Vault does retrieval; generation happens in the caller. |
| **Knowledge Graph** | `KnowledgeEntity` nodes (with `mentionCount` salience) and typed `EntityRelation` edges, scoped to a collection. |
| **Knowledge Freshness** | A scheduled sweep that flags `INDEXED` documents older than the re-index interval as `STALE` — marks, never deletes. |

---

## Pre-Coding Checklist

Before writing any code:
- [ ] Which module does this change belong to? Does it respect bounded context?
- [ ] Is there an existing port interface or utility to reuse?
- [ ] Does this change require a new Flyway migration?
- [ ] Does this change affect the data model or API contract? → update `docs/architecture.md`
- [ ] Does this change affect the roadmap status? → update `docs/progress.md` and `docs/roadmap.md`
- [ ] Does retrieval or the graph stay inside the `tenantId` + `collectionId` boundary?

---

## Ten Golden Rules (Non-Negotiable)

1. **Constructor injection exclusively** — no field-level `@Autowired`, no `@Inject`, fields must be `final`
2. **No hardcoded secrets** — all credentials to environment variables; never committed to source
3. **SLF4J with parameterized messages** — never `System.out.println()` or string concatenation in logs
4. **SOLID design principles** — single responsibility, open/closed, Liskov, interface segregation, dependency inversion
5. **DDD bounded contexts** — cross-module calls go through port interfaces, never reach into another module's internals
6. **Explicit column lists in SQL** — never `SELECT *`; always name every column
7. **Parameterized queries only** — no string concatenation for SQL; use `NamedParameterJdbcTemplate`
8. **Conventional Commits** — `type(scope): description` (feat, fix, docs, chore, build, test, refactor)
9. **No `// TODO` in committed code** — if it's not done, don't commit it
10. **`jakarta.*` exclusively** — Spring Boot 3.x; `javax.*` imports are a build-breaking error

### Aether Vault-Specific Constraints

- All document, chunk, and graph queries scoped by `tenant_id` **and** `collection_id` — no cross-collection or cross-tenant read path
- RAG retrieval never crosses the collection boundary; the assembled context is length-bounded (`RagContext.MAX_CONTEXT_CHARS`) and `topK` is clamped (`DefaultRagPipelineService.MAX_TOP_K`)
- Embedding dimension is 384 (all-MiniLM-L6-v2) — changing requires a full re-embedding migration
- Ollama must be replaceable: all embedding calls go through `KnowledgeEmbeddingService` (not direct HTTP)
- Ingestion is idempotent per document: re-indexing replaces a document's chunks (never accumulates stale ones)
- Freshness marks documents `STALE`; it never deletes indexed knowledge
- Vault is a *platform* layer — it must run standalone without Core, Grid, or Memory present

---

## Slash Commands

| Command | Purpose |
|---|---|
| `/estimate` | P50/P80/P90 effort estimate (Human Days = Raw Hours / 6.4) |
| `/review` | Code review against golden rules |
| `/adr` | Create an Architecture Decision Record |
| `/security-scan` | Security review of current changes |
| `/memory-update` | Update `.claude/memory/` files after major decisions |

---

## Memory Files

| File | Contents |
|---|---|
| `project-context.md` | Service details, ports, environments |
| `domain-glossary.md` | Aether Vault terminology |
| `decisions.md` | Architecture decisions log |
| `constraints.md` | Hard constraints + golden rules |
| `patterns.md` | Approved patterns in use |
| `session-log.md` | Rolling session log |

---

## Prohibited Patterns

- `javax.*` in any Spring Boot 3.x file
- Field `@Autowired` or `@Inject`
- `SELECT *` in any SQL
- Hardcoded passwords, tokens, or connection strings
- `Thread.sleep()` in tests (use Awaitility or Testcontainers)
- Empty `catch` blocks
- `Optional.get()` without guard
- `System.out.println()` in any production code
- Cross-collection / cross-tenant data access (missing `tenant_id` or `collection_id` in WHERE clause)
- RAG retrieval or graph traversal that escapes the collection boundary

---

## Documentation Sync Rule

Every commit that changes system behavior MUST update:
- `docs/progress.md` — mark completed deliverables
- `README.md` — if architecture or scope changed
- `docs/index.html` — if conceptual overview or tech stack changed
- `docs/roadmap.md` — if milestones shift
- `docs/architecture.md` — if architectural decisions change
