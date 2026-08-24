package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.FreshnessPolicy;
import com.suplab.aether.vault.domain.KnowledgeScope;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for per-collection freshness-policy persistence.
 *
 * <p>Stores the {@link FreshnessPolicy} override for a collection — its re-index interval and
 * auto-reingestion opt-in. A collection with no stored policy uses the global default, so absence is
 * a valid, expected state. Every read and write is scoped by {@link KnowledgeScope}. Implementations
 * live in {@code vault-engine}.</p>
 */
public interface FreshnessPolicyStore {

    /**
     * Persists a policy. UPSERT on {@code (tenant_id, collection_id)} — one policy per collection.
     *
     * @param policy the policy to persist
     */
    void save(FreshnessPolicy policy);

    /**
     * Looks up the policy for a collection.
     *
     * @param scope the owning tenant + collection
     * @return the stored policy if present, otherwise empty (the collection uses the global default)
     */
    Optional<FreshnessPolicy> find(KnowledgeScope scope);

    /**
     * Lists the collections that have opted into automatic re-ingestion of stale documents — the
     * scopes the stale-reingestion sweep considers. This is a system-wide read across tenants.
     *
     * @param limit maximum number of scopes to return
     * @return scopes with {@code autoReingest = true} (may be empty)
     */
    List<KnowledgeScope> findAutoReingestScopes(int limit);
}
