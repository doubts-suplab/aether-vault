package com.suplab.aether.vault.engine.rag;

import com.suplab.aether.vault.domain.EntityAwareRagContext;
import com.suplab.aether.vault.domain.GraphContext;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.RagContext;
import com.suplab.aether.vault.domain.RelevantEntity;
import com.suplab.aether.vault.domain.RetrievalQuery;
import com.suplab.aether.vault.engine.embedding.KnowledgeEmbeddingService;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.EntityExtractor;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import com.suplab.aether.vault.ports.RagPipelinePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link RagPipelinePort}.
 *
 * <p>The pipeline is deliberately bounded and scoped:</p>
 * <ol>
 *   <li>The requested {@code topK} is clamped to {@link #MAX_TOP_K} so a single query cannot pull
 *       an unbounded slice of a collection.</li>
 *   <li>The query text is embedded via {@link KnowledgeEmbeddingService}; when embeddings are
 *       disabled it degrades to a zero-vector match rather than failing.</li>
 *   <li>Vector search runs through {@link DocumentChunkStore#findSimilar} <em>within the query's
 *       collection only</em> — retrieval never crosses the tenant/collection boundary.</li>
 *   <li>Retrieved chunks are assembled into a length-bounded {@link RagContext} for the caller's
 *       prompt.</li>
 * </ol>
 *
 * <p>{@link #retrieveWithGraph} additionally enriches the answer with a {@link GraphContext}: it
 * recognises entities in the query text (via the injected {@link EntityExtractor}), resolves them
 * against the collection's knowledge graph, and expands to their graph neighbours — all within the
 * same scope. The enrichment is <strong>best-effort</strong>: if no extractor or graph store is
 * wired, or a graph lookup fails, retrieval still returns the text context with an empty graph, so
 * entity-awareness never degrades plain RAG.</p>
 */
public class DefaultRagPipelineService implements RagPipelinePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultRagPipelineService.class);

    /** Hard ceiling on chunks returned by any single retrieval. */
    public static final int MAX_TOP_K = 20;
    /** Graph neighbours fetched per matched entity when expanding the query subgraph. */
    public static final int NEIGHBOURS_PER_ENTITY = 5;
    private static final int EMBEDDING_DIM = 384;

    private final DocumentChunkStore chunkStore;
    private final Optional<KnowledgeEmbeddingService> embeddingService;
    private final EntityExtractor entityExtractor;
    private final KnowledgeGraphStore graphStore;

    /** Text-only pipeline (no graph enrichment) — {@link #retrieveWithGraph} returns an empty graph. */
    public DefaultRagPipelineService(DocumentChunkStore chunkStore,
                                     Optional<KnowledgeEmbeddingService> embeddingService) {
        this(chunkStore, embeddingService, null, null);
    }

    /**
     * @param entityExtractor recognises entities in the query text ({@code null} → graph disabled)
     * @param graphStore      resolves query entities + neighbours in the graph ({@code null} → disabled)
     */
    public DefaultRagPipelineService(DocumentChunkStore chunkStore,
                                     Optional<KnowledgeEmbeddingService> embeddingService,
                                     EntityExtractor entityExtractor,
                                     KnowledgeGraphStore graphStore) {
        this.chunkStore = chunkStore;
        this.embeddingService = embeddingService;
        this.entityExtractor = entityExtractor;
        this.graphStore = graphStore;
    }

    @Override
    public RagContext retrieve(RetrievalQuery query) {
        int topK = Math.min(query.topK(), MAX_TOP_K);
        var embedding = embeddingService
                .map(svc -> svc.embed(query.queryText()))
                .orElseGet(() -> new float[EMBEDDING_DIM]);

        var chunks = chunkStore.findSimilar(query.scope(), embedding, topK);
        var context = RagContext.assemble(query.queryText(), chunks);

        log.info("RAG retrieval tenantId={} collectionId={} topK={} retrieved={} contextChars={}",
                query.tenantId(), query.collectionId(), topK, chunks.size(),
                context.assembledContext().length());
        return context;
    }

    @Override
    public EntityAwareRagContext retrieveWithGraph(RetrievalQuery query) {
        var retrieval = retrieve(query);
        var graph = buildGraphContext(query);
        return new EntityAwareRagContext(retrieval, graph);
    }

    /**
     * Resolves the query's entities against the collection graph and expands to their neighbours.
     * Best-effort: any absence or failure yields {@link GraphContext#empty()} so plain RAG is intact.
     */
    private GraphContext buildGraphContext(RetrievalQuery query) {
        if (entityExtractor == null || graphStore == null) {
            return GraphContext.empty();
        }
        try {
            var scope = query.scope();
            List<RelevantEntity> matched = new ArrayList<>();
            List<RelevantEntity> related = new ArrayList<>();
            Set<UUID> matchedIds = new LinkedHashSet<>();

            for (var mention : entityExtractor.extract(query.queryText())) {
                graphStore.findByName(scope, mention.name(), mention.type()).ifPresent(entity -> {
                    if (matchedIds.add(entity.id())) {
                        matched.add(RelevantEntity.from(entity, RelevantEntity.Relevance.MATCHED));
                    }
                });
            }

            if (matched.isEmpty()) {
                return GraphContext.empty();
            }

            Set<UUID> seen = new LinkedHashSet<>(matchedIds);
            for (UUID matchedId : matchedIds) {
                for (KnowledgeEntity neighbour : graphStore.neighbours(scope, matchedId, NEIGHBOURS_PER_ENTITY)) {
                    if (seen.add(neighbour.id())) {
                        related.add(RelevantEntity.from(neighbour, RelevantEntity.Relevance.RELATED));
                    }
                }
            }

            var graph = GraphContext.assemble(matched, related);
            log.info("RAG graph enrichment tenantId={} collectionId={} matched={} related={}",
                    query.tenantId(), query.collectionId(), matched.size(), related.size());
            return graph;
        } catch (RuntimeException e) {
            // Best-effort: a graph failure must never break retrieval.
            log.warn("RAG graph enrichment failed for tenantId={} collectionId={} — returning text only: {}",
                    query.tenantId(), query.collectionId(), e.getMessage());
            return GraphContext.empty();
        }
    }
}
