package com.suplab.aether.vault.api.config;

import com.suplab.aether.vault.engine.embedding.KnowledgeEmbeddingService;
import com.suplab.aether.vault.engine.freshness.DocumentFreshnessService;
import com.suplab.aether.vault.engine.graph.JdbcKnowledgeGraphStore;
import com.suplab.aether.vault.engine.ingestion.DefaultDocumentIngestionService;
import com.suplab.aether.vault.engine.ingestion.DefaultSourceIngestionService;
import com.suplab.aether.vault.engine.ingestion.TextChunker;
import com.suplab.aether.vault.engine.rag.DefaultRagPipelineService;
import com.suplab.aether.vault.engine.source.FilesystemSourceConnector;
import com.suplab.aether.vault.engine.source.HttpSourceConnector;
import com.suplab.aether.vault.engine.source.SourceConnectorRegistry;
import com.suplab.aether.vault.engine.store.JdbcKnowledgeDocumentStore;
import com.suplab.aether.vault.engine.store.PGVectorDocumentChunkStore;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.DocumentIngestionPort;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import com.suplab.aether.vault.ports.RagPipelinePort;
import com.suplab.aether.vault.ports.SourceIngestionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
     * Creates the filesystem source connector, confined to a configured allowed root.
     *
     * <p>Conditional on {@code aether.vault.source.filesystem.enabled=true} — a filesystem connector
     * with no explicit boundary is unsafe, so it is <em>off by default</em>. When enabled,
     * {@code allowed-root} must be an absolute path; {@code file:} sources are confined to it.</p>
     *
     * @param allowedRoot the only directory tree the connector may read from (required when enabled)
     * @param maxBytes    maximum file size accepted (default 8 MiB)
     */
    @Bean
    @ConditionalOnProperty(name = "aether.vault.source.filesystem.enabled", havingValue = "true")
    public FilesystemSourceConnector filesystemSourceConnector(
            @Value("${aether.vault.source.filesystem.allowed-root:}") String allowedRoot,
            @Value("${aether.vault.source.filesystem.max-bytes:8388608}") long maxBytes) {
        if (allowedRoot == null || allowedRoot.isBlank()) {
            throw new IllegalStateException(
                    "aether.vault.source.filesystem.allowed-root must be set when the filesystem "
                            + "connector is enabled");
        }
        return new FilesystemSourceConnector(Path.of(allowedRoot), maxBytes);
    }

    /**
     * Creates the HTTP(S) source connector.
     *
     * <p>Conditional on {@code aether.vault.source.http.enabled=true} (default). Fetches over HTTP
     * with a request timeout and a response-size cap.</p>
     *
     * @param timeoutSeconds per-request timeout in seconds (default 15)
     * @param maxBytes       maximum response body size (default 8 MiB)
     */
    @Bean
    @ConditionalOnProperty(name = "aether.vault.source.http.enabled", havingValue = "true", matchIfMissing = true)
    public HttpSourceConnector httpSourceConnector(
            @Value("${aether.vault.source.http.timeout-seconds:15}") long timeoutSeconds,
            @Value("${aether.vault.source.http.max-bytes:8388608}") long maxBytes) {
        return new HttpSourceConnector(Duration.ofSeconds(timeoutSeconds), maxBytes);
    }

    /**
     * Creates the default-deny source connector registry over whatever connectors are enabled.
     *
     * <p>Spring injects every {@link DocumentSourceConnector} bean present; a URI that no enabled
     * connector supports is never fetched. Returns {@code null} (no registry, hence no source-ingest
     * bean) only if no connector is enabled at all.</p>
     */
    @Bean
    public SourceConnectorRegistry sourceConnectorRegistry(List<DocumentSourceConnector> connectors) {
        var enabled = new ArrayList<>(connectors);
        if (enabled.isEmpty()) {
            return null;
        }
        return new SourceConnectorRegistry(enabled);
    }

    /**
     * Creates the connector-driven ingest service (fetch → checksum → skip-if-unchanged → index).
     * Present only when at least one source connector is enabled.
     */
    @Bean
    @ConditionalOnProperty(name = "aether.vault.source.enabled", havingValue = "true", matchIfMissing = true)
    public SourceIngestionPort sourceIngestionPort(Optional<SourceConnectorRegistry> connectorRegistry,
                                                   KnowledgeDocumentStore documentStore,
                                                   DocumentIngestionPort ingestionPort) {
        return connectorRegistry
                .map(registry -> (SourceIngestionPort)
                        new DefaultSourceIngestionService(registry, documentStore, ingestionPort))
                .orElse(null);
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
