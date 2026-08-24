package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.FreshnessPolicy;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.FreshnessPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Per-collection knowledge-freshness policy — the re-index interval and auto-reingestion opt-in.
 *
 * <p>Scoped by {@code tenantId} + {@code collectionId}. A {@code GET} returns the stored policy or,
 * when none is set, the implicit default (global interval, auto-reingest off). A {@code PUT} upserts
 * the collection's override. The freshness sweep reads these overrides to decide when a collection's
 * documents go {@code STALE}.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/collections/{collectionId}/freshness-policy")
public class FreshnessPolicyController {

    private static final Logger log = LoggerFactory.getLogger(FreshnessPolicyController.class);

    private final FreshnessPolicyStore policyStore;

    public FreshnessPolicyController(FreshnessPolicyStore policyStore) {
        this.policyStore = policyStore;
    }

    /**
     * Returns the collection's freshness policy, or the implicit default when none is stored.
     *
     * @return 200 OK with the policy view
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@PathVariable String tenantId,
                                                   @PathVariable String collectionId) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        var policy = policyStore.find(scope).orElseGet(() -> FreshnessPolicy.defaultFor(scope));
        return ResponseEntity.ok(toView(policy, policyStore.find(scope).isPresent()));
    }

    /**
     * Upserts the collection's freshness policy.
     *
     * <p>Request body: {@code {"reindexIntervalDays": 7, "autoReingest": true}}.
     * {@code reindexIntervalDays} defaults to the global default and must be >= 1; {@code autoReingest}
     * defaults to false.</p>
     *
     * @return 200 OK with the saved policy view; 400 on an invalid interval
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> put(@PathVariable String tenantId,
                                                   @PathVariable String collectionId,
                                                   @RequestBody Map<String, Object> body) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        int intervalDays = body.get("reindexIntervalDays") instanceof Number n
                ? n.intValue() : FreshnessPolicy.DEFAULT_REINDEX_INTERVAL_DAYS;
        boolean autoReingest = Boolean.TRUE.equals(body.get("autoReingest"));
        try {
            var policy = FreshnessPolicy.of(scope, intervalDays, autoReingest);
            policyStore.save(policy);
            log.info("Saved freshness policy tenantId={} collectionId={} intervalDays={} autoReingest={}",
                    tenantId, collectionId, intervalDays, autoReingest);
            return ResponseEntity.ok(toView(policy, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static Map<String, Object> toView(FreshnessPolicy policy, boolean explicit) {
        return Map.of(
                "tenantId", policy.tenantId(),
                "collectionId", policy.collectionId(),
                "reindexIntervalDays", policy.reindexIntervalDays(),
                "autoReingest", policy.autoReingest(),
                "explicit", explicit,
                "updatedAt", policy.updatedAt().toString());
    }
}
