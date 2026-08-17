package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.GraphContext;
import com.suplab.aether.vault.domain.RagContext;
import com.suplab.aether.vault.domain.RelevantEntity;
import com.suplab.aether.vault.domain.RetrievalQuery;
import com.suplab.aether.vault.ports.RagPipelinePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieval-Augmented Generation query endpoint.
 *
 * <p>{@code POST /api/v1/rag/query} embeds the query, runs vector search <em>within the requested
 * collection only</em>, and returns the retrieved chunks plus a single length-bounded context
 * string ready to drop into an agent's prompt. This is the sanctioned context-enrichment path for
 * consumers such as Aether Grid agents.</p>
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagPipelinePort ragPipeline;

    public RagController(RagPipelinePort ragPipeline) {
        this.ragPipeline = ragPipeline;
    }

    /**
     * Executes a RAG retrieval.
     *
     * <p>Request body: {@code {"tenantId": "...", "collectionId": "...", "queryText": "...",
     * "topK": 5, "includeGraph": false}}. {@code topK} is clamped by the pipeline. When
     * {@code includeGraph} is true the response gains a {@code graph} object — the knowledge-graph
     * entities relevant to the query (matched + their neighbours) plus a prompt-ready summary.</p>
     *
     * @return 200 OK with {@code query}, {@code chunks}, {@code context} (and {@code graph} when
     *         requested); 400 on missing fields
     */
    @PostMapping("/query")
    public ResponseEntity<Object> query(@RequestBody Map<String, Object> body) {
        var tenantId = asString(body.get("tenantId"));
        var collectionId = asString(body.get("collectionId"));
        var queryText = asString(body.get("queryText"));
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId is required"));
        }
        if (collectionId == null || collectionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "collectionId is required"));
        }
        if (queryText == null || queryText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "queryText is required"));
        }

        int topK = asInt(body.get("topK"), 5);
        boolean includeGraph = asBool(body.get("includeGraph"), false);
        var request = new RetrievalQuery(tenantId, collectionId, queryText, topK);

        RagContext context;
        GraphContext graph = null;
        if (includeGraph) {
            var enriched = ragPipeline.retrieveWithGraph(request);
            context = enriched.retrieval();
            graph = enriched.graph();
        } else {
            context = ragPipeline.retrieve(request);
        }

        var chunks = context.chunks().stream()
                .map(chunk -> Map.<String, Object>of(
                        "documentId", chunk.documentId().toString(),
                        "documentTitle", chunk.documentTitle(),
                        "sourceUri", chunk.sourceUri(),
                        "ordinal", chunk.ordinal(),
                        "content", chunk.content(),
                        "score", chunk.score()))
                .toList();

        var response = new LinkedHashMap<String, Object>();
        response.put("query", context.query());
        response.put("chunks", chunks);
        response.put("context", context.assembledContext());
        if (graph != null) {
            response.put("graph", graphView(graph));
        }

        log.info("RAG query served tenantId={} collectionId={} topK={} chunks={} graph={}",
                tenantId, collectionId, topK, chunks.size(), includeGraph);
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> graphView(GraphContext graph) {
        List<Map<String, Object>> entities = graph.entities().stream()
                .map(RagController::entityView)
                .toList();
        return Map.of("summary", graph.summary(), "entities", entities);
    }

    private static Map<String, Object> entityView(RelevantEntity entity) {
        return Map.of(
                "name", entity.name(),
                "type", entity.type().name(),
                "mentionCount", entity.mentionCount(),
                "relevance", entity.relevance().name());
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean asBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
