package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.KnowledgeErasurePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Right-to-erasure seam for a knowledge collection (GDPR Art. 17).
 *
 * <p>Erases everything Vault derived from a collection's sources — its documents, embedded chunks,
 * and knowledge-graph entities (relations cascade) — scoped to the tenant. Idempotent: erasing an
 * already-empty collection returns 200 with zero counts, so the operation is safe to retry.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/collections/{collectionId}")
public class CollectionErasureController {

    private static final Logger log = LoggerFactory.getLogger(CollectionErasureController.class);

    private final KnowledgeErasurePort erasure;

    public CollectionErasureController(KnowledgeErasurePort erasure) {
        this.erasure = erasure;
    }

    /**
     * Erases a collection's documents, chunks, and graph.
     *
     * @return 200 OK with the counts removed
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> erase(@PathVariable String tenantId,
                                                     @PathVariable String collectionId) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        var result = erasure.eraseCollection(scope);
        log.info("Erased collection tenantId={} collectionId={} documents={} chunks={} entities={}",
                tenantId, collectionId, result.documentsErased(), result.chunksErased(),
                result.entitiesErased());
        return ResponseEntity.ok(Map.of(
                "tenantId", result.tenantId(),
                "collectionId", result.collectionId(),
                "documentsErased", result.documentsErased(),
                "chunksErased", result.chunksErased(),
                "entitiesErased", result.entitiesErased()));
    }
}
