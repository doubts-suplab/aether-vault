package com.suplab.aether.vault.domain;

/**
 * The ownership key for organisational knowledge: a {@code collectionId} within a
 * {@code tenantId}.
 *
 * <p>Every query into the document, chunk, and knowledge-graph stores is scoped by this pair —
 * there is no cross-tenant or cross-collection read path that does not pass a
 * {@code KnowledgeScope}. This is the multi-tenancy boundary of Aether Vault, analogous to
 * Aether Memory's per-{@code (tenantId, teamId)} scoping but keyed on a <em>knowledge
 * collection</em> (a corpus of documents) rather than a team.</p>
 */
public record KnowledgeScope(String tenantId, String collectionId) {

    public KnowledgeScope {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
    }

    /**
     * Convenience factory mirroring the {@code of(...)} idiom used across the domain.
     */
    public static KnowledgeScope of(String tenantId, String collectionId) {
        return new KnowledgeScope(tenantId, collectionId);
    }
}
