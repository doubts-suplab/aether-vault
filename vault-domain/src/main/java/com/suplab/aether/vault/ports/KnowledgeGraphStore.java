package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.KnowledgeScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for the knowledge graph — named entities and the relationships between them.
 *
 * <p>Entities are nodes and {@link EntityRelation}s are edges. All operations are scoped by
 * {@link KnowledgeScope}; the graph never spans collections. Implementations must reject a
 * relation whose two endpoints are not both entities in the same scope.</p>
 */
public interface KnowledgeGraphStore {

    /**
     * Inserts a new entity, or increments {@code mentionCount} on the existing entity with the
     * same {@code (scope, name, type)}. Returns the persisted entity (new or reinforced).
     *
     * @param entity the entity to upsert
     * @return the stored entity
     */
    KnowledgeEntity upsertEntity(KnowledgeEntity entity);

    /**
     * Looks up an entity by name and type within a scope.
     *
     * @param scope the owning tenant + collection
     * @param name  the entity surface form
     * @param type  the entity type
     * @return the entity if present, otherwise empty
     */
    Optional<KnowledgeEntity> findByName(KnowledgeScope scope, String name,
                                         com.suplab.aether.vault.domain.EntityType type);

    /**
     * Lists entities in a collection, most-mentioned first.
     *
     * @param scope the owning tenant + collection
     * @param limit maximum number of entities to return
     * @return entities in the collection (may be empty)
     */
    List<KnowledgeEntity> findEntities(KnowledgeScope scope, int limit);

    /**
     * Records a directed relationship between two entities in the same scope. Both endpoints must
     * exist within {@code scope}; implementations reject cross-collection edges.
     *
     * @param scope    the owning tenant + collection of both endpoints
     * @param relation the edge to persist
     */
    void relate(KnowledgeScope scope, EntityRelation relation);

    /**
     * Returns the entities directly connected to the given entity by any relation (either
     * direction), within scope.
     *
     * @param scope    the owning tenant + collection
     * @param entityId the entity whose neighbours to return
     * @param limit    maximum number of neighbours to return
     * @return neighbouring entities (may be empty)
     */
    List<KnowledgeEntity> neighbours(KnowledgeScope scope, UUID entityId, int limit);

    /**
     * Deletes every entity in a collection — the knowledge-graph half of a right-to-erasure request.
     * Their relations are removed by cascade. Scoped by tenant + collection so there is no
     * cross-collection deletion path.
     *
     * @param scope the owning tenant + collection
     * @return the number of entities deleted
     */
    int deleteByCollection(KnowledgeScope scope);
}
