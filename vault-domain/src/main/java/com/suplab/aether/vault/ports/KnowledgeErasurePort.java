package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.KnowledgeErasureResult;
import com.suplab.aether.vault.domain.KnowledgeScope;

/**
 * Erases a knowledge collection on a right-to-erasure request (GDPR Art. 17) — Vault's data-subject
 * seam.
 *
 * <p>A collection's documents, embedded chunks, and knowledge-graph entities are all derived from
 * source content that may reference a data subject. This port removes all of it for a collection in
 * one governed operation, scoped to the tenant, and reports the counts removed. Erasure is
 * idempotent — a collection with nothing stored is a no-op that returns zero counts.</p>
 *
 * <p>Unlike the freshness sweep (which only marks), erasure genuinely deletes and must report what
 * it removed, so the deletion is auditable.</p>
 */
public interface KnowledgeErasurePort {

    /**
     * Erases every document, chunk, and graph entity in a collection.
     *
     * @param scope the owning tenant + collection (isolation boundary)
     * @return the counts actually removed (zero when the collection was empty)
     */
    KnowledgeErasureResult eraseCollection(KnowledgeScope scope);
}
