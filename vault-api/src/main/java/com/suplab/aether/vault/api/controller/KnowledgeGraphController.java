package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Knowledge-graph operations for a collection: entities (nodes) and relations (edges).
 *
 * <p>All paths are scoped by {@code tenantId} + {@code collectionId}; the graph never spans
 * collections. Entities are upserted (re-asserting an entity bumps its mention count), relations
 * are created between two existing entities, and a node's direct neighbours can be traversed.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/collections/{collectionId}/entities")
public class KnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphController.class);

    private final KnowledgeGraphStore graphStore;

    public KnowledgeGraphController(KnowledgeGraphStore graphStore) {
        this.graphStore = graphStore;
    }

    /**
     * Creates or reinforces an entity.
     *
     * <p>Request body: {@code {"name": "...", "type": "CONCEPT"}}. {@code type} defaults to
     * {@code OTHER}.</p>
     *
     * @return 201 Created with the stored entity view; 400 on invalid type
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> upsert(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @RequestBody Map<String, String> body) {

        var name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        EntityType type;
        try {
            type = EntityType.valueOf(body.getOrDefault("type", "OTHER").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "invalid type; valid values: PERSON, ORGANISATION, LOCATION, CONCEPT, PRODUCT, EVENT, OTHER"));
        }

        var scope = new KnowledgeScope(tenantId, collectionId);
        var stored = graphStore.upsertEntity(KnowledgeEntity.create(scope, name, type));
        log.info("Upserted entity id={} name={} type={} tenantId={} collectionId={}",
                stored.id(), stored.name(), stored.type(), tenantId, collectionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(stored));
    }

    /**
     * Lists entities in the collection, most-mentioned first.
     *
     * @param limit maximum number of entities (default 50)
     * @return 200 OK with the list of entity views
     */
    @GetMapping
    public ResponseEntity<Object> list(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @RequestParam(defaultValue = "50") int limit) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        var body = graphStore.findEntities(scope, limit).stream()
                .map(KnowledgeGraphController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Relates this entity to another by a typed edge.
     *
     * <p>Request body: {@code {"targetEntityId": "...", "relationType": "depends_on",
     * "confidence": 0.9}}. {@code confidence} defaults to 1.0.</p>
     *
     * @return 201 Created on success; 400 if the endpoints are invalid or not in this scope
     */
    @PostMapping("/{entityId}/relations")
    public ResponseEntity<Map<String, Object>> relate(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @PathVariable UUID entityId,
            @RequestBody Map<String, Object> body) {

        var targetRaw = asString(body.get("targetEntityId"));
        var relationType = asString(body.get("relationType"));
        if (targetRaw == null || relationType == null || relationType.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "targetEntityId and relationType are required"));
        }
        try {
            var target = UUID.fromString(targetRaw);
            double confidence = asDouble(body.get("confidence"), 1.0);
            var scope = new KnowledgeScope(tenantId, collectionId);
            graphStore.relate(scope, new EntityRelation(entityId, target, relationType, confidence));
            log.info("Related source={} target={} type={} tenantId={} collectionId={}",
                    entityId, target, relationType, tenantId, collectionId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "sourceEntityId", entityId.toString(),
                    "targetEntityId", target.toString(),
                    "relationType", relationType));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns the entities directly connected to the given entity.
     *
     * @param limit maximum number of neighbours (default 25)
     * @return 200 OK with the list of neighbouring entity views
     */
    @GetMapping("/{entityId}/neighbours")
    public ResponseEntity<Object> neighbours(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "25") int limit) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        var body = graphStore.neighbours(scope, entityId, limit).stream()
                .map(KnowledgeGraphController::toView).toList();
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> toView(KnowledgeEntity entity) {
        return Map.of(
                "entityId", entity.id().toString(),
                "name", entity.name(),
                "type", entity.type().name(),
                "mentionCount", entity.mentionCount(),
                "createdAt", entity.createdAt().toString());
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
