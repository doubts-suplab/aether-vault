package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.EntityAwareRagContext;
import com.suplab.aether.vault.domain.RagContext;
import com.suplab.aether.vault.domain.RetrievalQuery;

/**
 * Port interface for the Retrieval-Augmented Generation pipeline.
 *
 * <p>This is the sanctioned read path for agent context enrichment: it embeds the query, runs
 * vector search <em>within the query's collection only</em>, and returns a bounded
 * {@link RagContext} — the retrieved chunks plus a length-capped, prompt-ready context string.
 * Vault performs retrieval and context assembly; generation happens in the calling agent.</p>
 */
public interface RagPipelinePort {

    /**
     * Retrieves the most relevant chunks for a query and assembles a bounded context.
     *
     * @param query the retrieval request (tenant, collection, query text, topK)
     * @return the assembled RAG context (may contain zero chunks); never {@code null}
     */
    RagContext retrieve(RetrievalQuery query);

    /**
     * Entity-aware retrieval: the ordinary text {@link RagContext} plus a bounded knowledge-graph
     * projection of the entities the query touches and their neighbours. The default implementation
     * returns an empty graph (plain RAG), so a graph-less pipeline still satisfies the port.
     *
     * @param query the retrieval request
     * @return the retrieval augmented with a graph context (empty when no graph is available)
     */
    default EntityAwareRagContext retrieveWithGraph(RetrievalQuery query) {
        return EntityAwareRagContext.of(retrieve(query));
    }
}
