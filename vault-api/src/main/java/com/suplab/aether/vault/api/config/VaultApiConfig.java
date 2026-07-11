package com.suplab.aether.vault.api.config;

import com.suplab.aether.vault.engine.embedding.KnowledgeEmbeddingService;
import com.suplab.aether.vault.engine.freshness.DocumentFreshnessService;
import com.suplab.aether.vault.engine.graph.JdbcKnowledgeGraphStore;
import com.suplab.aether.vault.engine.ingestion.DefaultDocumentIngestionService;
import com.suplab.aether.vault.engine.ingestion.TextChunker;
import com.suplab.aether.vault.engine.rag.DefaultRagPipelineService;
import com.suplab.aether.vault.engine.store.JdbcKnowledgeDocumentStore;
import com.suplab.aether.vault.engine.store.PGVectorDocumentChunkStore;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.DocumentIngestionPort;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import com.suplab.aether.vault.ports.RagPipelinePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Optional;

/**
 * Spring configuration for Aether Vault API beans.
 *
 * <p>Wires the pgvector chunk store, document store, knowledge-graph store, Ollama embedding
 * service, text chunker, ingestion pipeline, RAG pipeline, and freshness service using
 * constructor injection. All beans are declared here — never via field {@code @Autowired}. The
 * engine adapters are framework-free; only this config knows how to assemble them.</p>
 */
@Configuration
public class VaultApiConfig {

    /**
     * Creates the document-metadata store.
     */
    @Bean
    public KnowledgeDocumentStore knowledgeDocumentStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcKnowledgeDocumentStore(jdbc);
    }

    /**
     * Creates the pgvector chunk store (vector search + chunk persistence).
     */
    @Bean
    public DocumentChunkStore documentChunkStore(NamedParameterJdbcTemplate jdbc) {
        return new PGVectorDocumentChunkStore(jdbc);
    }

    /**
     * Creates the knowledge-graph store (entities + relations).
     */
    @Bean
    public KnowledgeGraphStore knowledgeGraphStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcKnowledgeGraphStore(jdbc);
    }

    /**
     * Creates the text chunker used by the ingestion pipeline.
     *
     * @param chunkSize maximum characters per chunk (default 1000)
     * @param overlap   characters shared between adjacent chunks (default 150)
     */
    @Bean
    public TextChunker textChunker(
            @Value("${aether.vault.ingestion.chunk-size:1000}") int chunkSize,
            @Value("${aether.vault.ingestion.chunk-overlap:150}") int overlap) {
        return new TextChunker(chunkSize, overlap);
    }

    /**
     * Creates the document-indexing pipeline. The embedding service is optional so ingestion
     * remains available (chunks stored with zero vectors) when Ollama is disabled.
     */
    @Bean
    public DocumentIngestionPort documentIngestionPort(KnowledgeDocumentStore documentStore,
                                                       DocumentChunkStore chunkStore,
                                                       Optional<KnowledgeEmbeddingService> embeddingService,
                                                       TextChunker chunker) {
        return new DefaultDocumentIngestionService(documentStore, chunkStore, embeddingService, chunker);
    }

    /**
     * Creates the RAG pipeline. The embedding service is optional so retrieval degrades to
     * zero-vector matching rather than failing when Ollama is disabled.
     */
    @Bean
    public RagPipelinePort ragPipelinePort(DocumentChunkStore chunkStore,
                                           Optional<KnowledgeEmbeddingService> embeddingService) {
        return new DefaultRagPipelineService(chunkStore, embeddingService);
    }

    /**
     * Creates the knowledge-freshness sweep service.
     *
     * @param reindexIntervalDays age beyond which an INDEXED document is flagged STALE (default 30)
     */
    @Bean
    public KnowledgeFreshnessPort knowledgeFreshnessPort(
            NamedParameterJdbcTemplate jdbc,
            @Value("${aether.vault.freshness.reindex-interval-days:30}") int reindexIntervalDays) {
        return new DocumentFreshnessService(jdbc, reindexIntervalDays);
    }

    /**
     * Creates the embedding service that calls Ollama's {@code /api/embeddings} endpoint.
     *
     * <p>Conditional on {@code aether.vault.embedding.enabled=true} (default). Set to
     * {@code false} in environments where Ollama is unavailable — chunks are stored with zero
     * vectors and semantic retrieval is non-functional, but ingestion, the knowledge graph, and
     * document management all remain operational.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "aether.vault.embedding.enabled", havingValue = "true", matchIfMissing = true)
    public KnowledgeEmbeddingService knowledgeEmbeddingService(
            @Value("${aether.vault.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${aether.vault.embedding.model:all-minilm}") String model) {
        return new KnowledgeEmbeddingService(ollamaUrl, model);
    }
}
