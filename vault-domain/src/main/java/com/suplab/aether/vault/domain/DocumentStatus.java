package com.suplab.aether.vault.domain;

/**
 * The indexing lifecycle state of a {@link KnowledgeDocument}.
 *
 * <ul>
 *   <li>PENDING — registered but not yet chunked/embedded</li>
 *   <li>INDEXED — chunked, embedded, and queryable by the RAG pipeline</li>
 *   <li>STALE   — the source changed (checksum drift) or the re-index interval elapsed; the
 *                 document is due for re-indexing but its existing chunks remain searchable</li>
 *   <li>FAILED  — ingestion failed and the document has no usable chunks</li>
 * </ul>
 */
public enum DocumentStatus {
    PENDING,
    INDEXED,
    STALE,
    FAILED;

    /**
     * @return {@code true} if a document in this state should be re-indexed.
     */
    public boolean needsIndexing() {
        return this == PENDING || this == STALE || this == FAILED;
    }
}
