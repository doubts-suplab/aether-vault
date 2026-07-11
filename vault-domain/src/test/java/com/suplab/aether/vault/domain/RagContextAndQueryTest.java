package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagContextAndQueryTest {

    private static RetrievedChunk chunk(String content) {
        return new RetrievedChunk(UUID.randomUUID(), "Doc", "uri", 0, content, 0.1);
    }

    @Test
    void retrievalQuery_defaultsNonPositiveTopK() {
        var query = new RetrievalQuery("tenant-1", "handbook", "how do we deploy?", 0);

        assertThat(query.topK()).isEqualTo(5);
        assertThat(query.scope()).isEqualTo(KnowledgeScope.of("tenant-1", "handbook"));
    }

    @Test
    void retrievalQuery_rejectsBlankQueryText() {
        assertThatThrownBy(() -> new RetrievalQuery("tenant-1", "handbook", "  ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queryText required");
    }

    @Test
    void ragContext_assembleJoinsChunksInOrder() {
        var context = RagContext.assemble("q", List.of(chunk("alpha"), chunk("beta")));

        assertThat(context.assembledContext()).contains("alpha").contains("beta");
        assertThat(context.assembledContext().indexOf("alpha"))
                .isLessThan(context.assembledContext().indexOf("beta"));
        assertThat(context.chunks()).hasSize(2);
    }

    @Test
    void ragContext_assembleBoundsContextLength() {
        var huge = "x".repeat(RagContext.MAX_CONTEXT_CHARS * 2);
        var context = RagContext.assemble("q", List.of(chunk(huge)));

        assertThat(context.assembledContext().length()).isLessThanOrEqualTo(RagContext.MAX_CONTEXT_CHARS);
    }

    @Test
    void ragContext_assembleHandlesNoChunks() {
        var context = RagContext.assemble("q", List.of());

        assertThat(context.chunks()).isEmpty();
        assertThat(context.assembledContext()).isEmpty();
    }

    @Test
    void ragContext_rejectsBlankQuery() {
        assertThatThrownBy(() -> RagContext.assemble("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query required");
    }

    @Test
    void retrievedChunk_rejectsBlankContent() {
        assertThatThrownBy(() -> new RetrievedChunk(UUID.randomUUID(), "Doc", "uri", 0, " ", 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }
}
