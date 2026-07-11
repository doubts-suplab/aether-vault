package com.suplab.aether.vault.engine.rag;

import com.suplab.aether.vault.domain.RagContext;
import com.suplab.aether.vault.domain.RetrievalQuery;
import com.suplab.aether.vault.engine.embedding.KnowledgeEmbeddingService;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.RagPipelinePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
 */
public class DefaultRagPipelineService implements RagPipelinePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultRagPipelineService.class);

    /** Hard ceiling on chunks returned by any single retrieval. */
    public static final int MAX_TOP_K = 20;
    private static final int EMBEDDING_DIM = 384;

    private final DocumentChunkStore chunkStore;
    private final Optional<KnowledgeEmbeddingService> embeddingService;

    public DefaultRagPipelineService(DocumentChunkStore chunkStore,
                                     Optional<KnowledgeEmbeddingService> embeddingService) {
        this.chunkStore = chunkStore;
        this.embeddingService = embeddingService;
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
}
