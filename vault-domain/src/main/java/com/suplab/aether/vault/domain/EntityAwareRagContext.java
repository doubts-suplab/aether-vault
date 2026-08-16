package com.suplab.aether.vault.domain;

/**
 * The full result of an entity-aware RAG retrieval: the ordinary text {@link RagContext} plus the
 * {@link GraphContext} of knowledge-graph entities relevant to the query.
 *
 * <p>The two halves are complementary — the retrieval carries the most relevant document text, the
 * graph carries the structured entities that text is about and the entities related to them. A
 * caller can drop both into a prompt: the passages for grounding, the entity summary for a concise
 * map of what the query touches. The graph is {@link GraphContext#empty()} when no query entity
 * matched the collection's graph (or the graph is unavailable), so this degrades cleanly to plain
 * RAG.</p>
 *
 * @param retrieval the bounded text retrieval (chunks + assembled context)
 * @param graph     the bounded knowledge-graph projection relevant to the query
 */
public record EntityAwareRagContext(RagContext retrieval, GraphContext graph) {

    public EntityAwareRagContext {
        if (retrieval == null) throw new IllegalArgumentException("retrieval required");
        if (graph == null) graph = GraphContext.empty();
    }

    /** Wraps a plain retrieval with an empty graph context (the graph-less path). */
    public static EntityAwareRagContext of(RagContext retrieval) {
        return new EntityAwareRagContext(retrieval, GraphContext.empty());
    }
}
