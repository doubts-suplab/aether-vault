# Constraints — Aether Vault

## Ten Golden Rules (Non-Negotiable)
1. Constructor injection exclusively — no field `@Autowired`/`@Inject`; fields `final`
2. No hardcoded secrets — credentials via environment variables only
3. SLF4J parameterized logging — never `System.out.println()` or string concatenation
4. SOLID design principles
5. DDD bounded contexts — cross-module calls go through port interfaces
6. Explicit column lists in SQL — never `SELECT *`
7. Parameterized queries only — `NamedParameterJdbcTemplate`, never string concatenation
8. Conventional Commits — `type(scope): description`
9. No `// TODO` in committed code
10. `jakarta.*` exclusively — `javax.*` is a build-breaking error

## Aether Vault-Specific Hard Constraints
- **Scoping:** every document, chunk, and graph query includes `tenant_id` AND `collection_id` in WHERE — no cross-collection or cross-tenant read path
- **RAG confinement:** retrieval only ever runs inside the query's collection; the assembled context is bounded to `RagContext.MAX_CONTEXT_CHARS` and `topK` is clamped to `DefaultRagPipelineService.MAX_TOP_K`
- **Graph confinement:** an `EntityRelation` is rejected unless both endpoints exist in the same scope
- **Idempotent ingestion:** re-indexing a document deletes its prior chunks first — never accumulate stale chunks
- **Freshness marks, never deletes:** the sweep only transitions `INDEXED → STALE`
- **Embedding dimension:** 384 (all-MiniLM-L6-v2). Changing requires a full re-embedding migration.
- **Ollama replaceable:** all embedding calls go through `KnowledgeEmbeddingService` — never direct HTTP from callers
- **Standalone:** must boot, migrate, serve, and run its freshness sweep with no dependency on Core, Grid, or Memory
- **Ports:** Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083, Vault=**8084** — do not collide

## Prohibited Patterns
- `javax.*`, field injection, `SELECT *`, hardcoded credentials
- `Thread.sleep()` in tests (use Testcontainers/Awaitility)
- Empty `catch` blocks, `Optional.get()` without guard
- Missing `tenant_id`/`collection_id` in a WHERE clause
- RAG retrieval or graph traversal that escapes the collection boundary
