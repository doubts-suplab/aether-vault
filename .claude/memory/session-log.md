# Session Log — Aether Vault

> Rolling log of working sessions. Newest first.

## Session 1 — Phase 0 Scaffold
- Bootstrapped the standalone `aether-vault` knowledge platform mirroring `aether-core` / `aether-memory` structure and quality bar.
- Created 4 modules: `vault-domain`, `vault-engine`, `vault-api`, `vault-infra`.
- Domain: KnowledgeDocument, DocumentChunk, KnowledgeScope, DocumentStatus, EntityType, RetrievalQuery, RetrievedChunk, RagContext, KnowledgeEntity, EntityRelation + 6 ports.
- Engine: PGVectorDocumentChunkStore, JdbcKnowledgeDocumentStore, KnowledgeEmbeddingService, TextChunker, DefaultDocumentIngestionService, DefaultRagPipelineService, JdbcKnowledgeGraphStore, DocumentFreshnessService.
- API: AetherVaultApplication (8084), Document/RAG/KnowledgeGraph controllers, freshness scheduler + config, application.yml, Dockerfile.
- Infra: Flyway V001–V004 (documents, chunks, pgvector embeddings, knowledge graph), docker-compose (postgres on 5435), k8s (namespace/deployment/service+HPA).
- Tests: 56 unit tests green (`mvn test`); Testcontainers ITs authored for CI (chunk store, document store, graph store).
- Docs: CLAUDE.md, README, aether.manifest.yaml, docs/{index.html, architecture.md, roadmap.md, progress.md}, .claude/{memory,agents}.
- CI: ci.yml, quality-gate.yml, docker-build.yml (SHA-pinned actions, OIDC).
- Key decisions logged as ADR-0001..0007 in decisions.md.
