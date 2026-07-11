package com.suplab.aether.vault.domain;

import java.util.UUID;

/**
 * A directed, typed relationship between two {@link KnowledgeEntity} nodes — an edge in the
 * knowledge graph.
 *
 * <p>Relations are always between entities in the same collection (enforced by the graph store),
 * so an edge never bridges the tenancy boundary. The {@code relationType} is a free-form verb
 * phrase ({@code "works_at"}, {@code "depends_on"}, {@code "part_of"}), and {@code confidence}
 * carries the extractor's certainty.</p>
 *
 * @param sourceEntityId the edge's origin node
 * @param targetEntityId the edge's destination node
 * @param relationType   the relationship label (e.g. {@code "depends_on"})
 * @param confidence     extraction confidence in [0, 1]
 */
public record EntityRelation(
        UUID sourceEntityId,
        UUID targetEntityId,
        String relationType,
        double confidence
) {
    public EntityRelation {
        if (sourceEntityId == null) throw new IllegalArgumentException("sourceEntityId required");
        if (targetEntityId == null) throw new IllegalArgumentException("targetEntityId required");
        if (sourceEntityId.equals(targetEntityId))
            throw new IllegalArgumentException("a relation must connect two distinct entities");
        if (relationType == null || relationType.isBlank())
            throw new IllegalArgumentException("relationType required");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be 0-1");
    }

    /**
     * Factory for a relation with full confidence (1.0) — e.g. an explicitly asserted edge.
     */
    public static EntityRelation of(UUID sourceEntityId, UUID targetEntityId, String relationType) {
        return new EntityRelation(sourceEntityId, targetEntityId, relationType, 1.0);
    }
}
