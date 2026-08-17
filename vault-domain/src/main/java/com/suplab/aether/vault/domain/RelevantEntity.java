package com.suplab.aether.vault.domain;

/**
 * A knowledge-graph entity surfaced as relevant to a RAG query — a bounded projection of a
 * {@link KnowledgeEntity} for prompt enrichment.
 *
 * <p>{@link Relevance} records <em>why</em> the entity is here: {@code MATCHED} means the entity's
 * surface form was recognised in the query text itself; {@code RELATED} means it is a graph
 * neighbour of a matched entity (reachable by one relation edge). This lets a caller weight
 * directly-named entities above merely-adjacent ones.</p>
 *
 * @param name         the entity surface form
 * @param type         the entity category
 * @param mentionCount corpus salience (how often the entity is mentioned across the collection)
 * @param relevance    why the entity is relevant to the query
 */
public record RelevantEntity(String name, EntityType type, int mentionCount, Relevance relevance) {

    /** Why a {@link RelevantEntity} was surfaced for a query. */
    public enum Relevance {
        /** The entity's surface form was recognised in the query text. */
        MATCHED,
        /** A graph neighbour (one relation edge) of a matched entity. */
        RELATED
    }

    public RelevantEntity {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        name = name.trim();
        if (type == null) type = EntityType.OTHER;
        if (mentionCount < 1) mentionCount = 1;
        if (relevance == null) relevance = Relevance.MATCHED;
    }

    /** Projects a persisted {@link KnowledgeEntity} into a query-relevant view with the given relevance. */
    public static RelevantEntity from(KnowledgeEntity entity, Relevance relevance) {
        return new RelevantEntity(entity.name(), entity.type(), entity.mentionCount(), relevance);
    }
}
