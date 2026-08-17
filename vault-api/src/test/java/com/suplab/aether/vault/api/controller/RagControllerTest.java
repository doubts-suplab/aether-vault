package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.EntityAwareRagContext;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.GraphContext;
import com.suplab.aether.vault.domain.RagContext;
import com.suplab.aether.vault.domain.RelevantEntity;
import com.suplab.aether.vault.domain.RetrievalQuery;
import com.suplab.aether.vault.domain.RetrievedChunk;
import com.suplab.aether.vault.ports.RagPipelinePort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RagControllerTest {

    /** Fake pipeline recording which path was taken and returning fixed content. */
    private static final class FakePipeline implements RagPipelinePort {
        boolean graphCalled;
        boolean plainCalled;

        private RagContext context(RetrievalQuery q) {
            var chunk = new RetrievedChunk(UUID.randomUUID(), "Doc", "uri", 0, "alpha", 0.2);
            return RagContext.assemble(q.queryText(), List.of(chunk));
        }

        @Override public RagContext retrieve(RetrievalQuery query) {
            plainCalled = true;
            return context(query);
        }

        @Override public EntityAwareRagContext retrieveWithGraph(RetrievalQuery query) {
            graphCalled = true;
            var graph = GraphContext.assemble(
                    List.of(new RelevantEntity("Ada Lovelace", EntityType.PERSON, 3,
                            RelevantEntity.Relevance.MATCHED)),
                    List.of());
            return new EntityAwareRagContext(context(query), graph);
        }
    }

    private Map<String, Object> body(boolean includeGraph) {
        return Map.of("tenantId", "t-1", "collectionId", "c-1", "queryText", "Ada Lovelace",
                "topK", 5, "includeGraph", includeGraph);
    }

    @Test
    @SuppressWarnings("unchecked")
    void query_withoutGraph_omitsGraphAndUsesPlainPath() {
        var pipeline = new FakePipeline();
        var res = new RagController(pipeline).query(body(false));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pipeline.plainCalled).isTrue();
        assertThat(pipeline.graphCalled).isFalse();
        var payload = (Map<String, Object>) res.getBody();
        assertThat(payload).containsKeys("query", "chunks", "context");
        assertThat(payload).doesNotContainKey("graph");
    }

    @Test
    @SuppressWarnings("unchecked")
    void query_withGraph_includesGraphProjection() {
        var pipeline = new FakePipeline();
        var res = new RagController(pipeline).query(body(true));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pipeline.graphCalled).isTrue();
        var payload = (Map<String, Object>) res.getBody();
        var graph = (Map<String, Object>) payload.get("graph");
        assertThat(graph).isNotNull();
        assertThat((String) graph.get("summary")).contains("Ada Lovelace");
        var entities = (List<Map<String, Object>>) graph.get("entities");
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0)).containsEntry("name", "Ada Lovelace")
                .containsEntry("type", "PERSON")
                .containsEntry("relevance", "MATCHED");
    }

    @Test
    void query_missingQueryText_is400() {
        var res = new RagController(new FakePipeline())
                .query(Map.of("tenantId", "t-1", "collectionId", "c-1"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
