package com.suplab.aether.vault.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A named entity extracted from a collection's documents — a node in the knowledge graph.
 *
 * <p>Entities are scoped to a {@code tenantId} + {@code collectionId} like every other Vault
 * aggregate. An entity's {@code mentionCount} grows as it is observed across documents, giving a
 * cheap salience signal (more mentions → more central to the corpus), analogous to Aether
 * Memory's reinforcement.</p>
 *
 * @param id           stable identifier
 * @param tenantId     owning tenant
 * @param collectionId owning collection
 * @param name         the surface form of the entity
 * @param type         the entity's category
 * @param mentionCount how many times the entity has been observed (>= 1)
 * @param createdAt    when the entity was first recorded
 */
public record KnowledgeEntity(
        UUID id,
        String tenantId,
        String collectionId,
        String name,
        EntityType type,
        int mentionCount,
        Instant createdAt
) {
    public KnowledgeEntity {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (collectionId == null || collectionId.isBlank())
            throw new IllegalArgumentException("collectionId required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (type == null) type = EntityType.OTHER;
        if (mentionCount < 1) throw new IllegalArgumentException("mentionCount must be >= 1");
        if (createdAt == null) createdAt = Instant.now();
    }

    /**
     * Factory for a newly observed entity: random ID, {@code mentionCount} of 1.
     */
    public static KnowledgeEntity create(KnowledgeScope scope, String name, EntityType type) {
        return new KnowledgeEntity(UUID.randomUUID(), scope.tenantId(), scope.collectionId(), name, type, 1,
                Instant.now());
    }

    /**
     * Returns a copy with {@code mentionCount} incremented — the entity was observed again.
     */
    public KnowledgeEntity mention() {
        return new KnowledgeEntity(id, tenantId, collectionId, name, type, mentionCount + 1, createdAt);
    }

    /**
     * Returns the owning scope ({@code tenantId} + {@code collectionId}) of this entity.
     */
    public KnowledgeScope scope() {
        return new KnowledgeScope(tenantId, collectionId);
    }
}
