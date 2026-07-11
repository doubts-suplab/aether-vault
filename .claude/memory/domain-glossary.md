# Domain Glossary — Aether Vault

| Term | Definition |
|---|---|
| **Knowledge Document** | A source document registered in a collection. The unit of ingestion; tracked through a `PENDING → INDEXED → STALE/FAILED` lifecycle. |
| **Knowledge Scope** | The `(tenantId, collectionId)` ownership key. Every store read/write is scoped by it — the multi-tenancy boundary. |
| **Collection** | A corpus of documents within a tenant. Retrieval and the knowledge graph never cross a collection. |
| **Document Chunk** | An ordered, overlapping slice of a document — the unit of embedding and vector search. |
| **Checksum** | A SHA-256 fingerprint of a document's source content; drives change detection / freshness. |
| **Document Status** | `PENDING` (registered), `INDEXED` (searchable), `STALE` (due for re-index), `FAILED` (no usable chunks). |
| **Ingestion** | Chunk → embed → store → mark indexed. Idempotent per document: re-indexing replaces prior chunks. |
| **Vector Search** | Cosine-distance similarity over pgvector, scoped to one collection (`ORDER BY embedding <=> :query::vector`). |
| **RAG Pipeline** | Retrieval-Augmented Generation retrieval half: embed query → vector search → assemble bounded context. Generation is the caller's. |
| **Retrieved Chunk** | A read projection of a chunk joined to its document (title, source URI) plus a cosine-distance score. |
| **RAG Context** | The assembled output: retrieved chunks + a single length-bounded context string (`MAX_CONTEXT_CHARS`). |
| **Knowledge Entity** | A named-entity node in the knowledge graph, with a `mentionCount` salience signal. |
| **Entity Relation** | A directed, typed, confidence-weighted edge between two entities in the same collection. |
| **Knowledge Freshness** | A scheduled sweep marking `INDEXED` documents older than the re-index interval as `STALE`. Marks, never deletes. |
| **Entity Type** | PERSON \| ORGANISATION \| LOCATION \| CONCEPT \| PRODUCT \| EVENT \| OTHER. |

## Ecosystem terms
| Term | Definition |
|---|---|
| **Aether Core** | Personal cognitive engine — owns per-user memory. |
| **Aether Grid** | Distributed agent mesh / API governance runtime — a consumer of Vault's RAG pipeline. |
| **Aether Memory** | Shared team/org memory platform — sibling platform layer. Vault owns documents, Memory owns shared memories. |
| **Platform layer** | Aether Vault's position — above Grid/Core, alongside Memory, below domain products. |
| **Standalone guarantee** | Vault boots and runs with no dependency on Core, Grid, or Memory. |
