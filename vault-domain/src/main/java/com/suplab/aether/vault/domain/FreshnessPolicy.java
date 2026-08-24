package com.suplab.aether.vault.domain;

import java.time.Instant;

/**
 * Per-collection knowledge-freshness policy — how aggressively a collection's indexed documents are
 * kept current.
 *
 * <p>The freshness sweep flags an {@code INDEXED} document {@code STALE} once it is older than the
 * re-index interval. Without a policy every collection shares one global interval; a
 * {@code FreshnessPolicy} lets a tenant tune the interval per collection (a fast-moving handbook
 * re-indexed weekly, a stable archive yearly) and opt a collection into automatic re-ingestion of
 * stale documents. A collection with no stored policy falls back to the global default — policy is an
 * override, not a prerequisite.</p>
 *
 * @param tenantId            owning tenant (isolation boundary)
 * @param collectionId        owning collection within the tenant
 * @param reindexIntervalDays age (days since last index) beyond which a document is flagged STALE
 * @param autoReingest        whether the stale-reingestion sweep may re-fetch + re-index this
 *                            collection's stale documents through their source connector
 * @param updatedAt           when the policy last changed
 */
public record FreshnessPolicy(
        String tenantId,
        String collectionId,
        int reindexIntervalDays,
        boolean autoReingest,
        Instant updatedAt
) {
    /** Global fallback interval used when a collection has no stored policy. */
    public static final int DEFAULT_REINDEX_INTERVAL_DAYS = 30;

    public FreshnessPolicy {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
        if (reindexIntervalDays < 1) throw new IllegalArgumentException("reindexIntervalDays must be >= 1");
        if (updatedAt == null) updatedAt = Instant.now();
    }

    /** The owning scope ({@code tenantId} + {@code collectionId}). */
    public KnowledgeScope scope() {
        return new KnowledgeScope(tenantId, collectionId);
    }

    /** A policy with the given interval and auto-reingest flag, stamped now. */
    public static FreshnessPolicy of(KnowledgeScope scope, int reindexIntervalDays, boolean autoReingest) {
        return new FreshnessPolicy(scope.tenantId(), scope.collectionId(), reindexIntervalDays,
                autoReingest, Instant.now());
    }

    /** The implicit default policy for a collection that has none stored (global interval, no auto-reingest). */
    public static FreshnessPolicy defaultFor(KnowledgeScope scope) {
        return new FreshnessPolicy(scope.tenantId(), scope.collectionId(), DEFAULT_REINDEX_INTERVAL_DAYS,
                false, Instant.now());
    }
}
