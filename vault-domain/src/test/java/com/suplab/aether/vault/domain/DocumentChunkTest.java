package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentChunkTest {

    private static final KnowledgeScope SCOPE = KnowledgeScope.of("tenant-1", "handbook");

    private static KnowledgeDocument document() {
        return KnowledgeDocument.create(SCOPE, "uri", "title", "text/plain", "sum");
    }

    @Test
    void create_inheritsDocumentScopeAndAssignsId() {
        var doc = document();
        var chunk = DocumentChunk.create(doc, 0, "some content", 3);

        assertThat(chunk.documentId()).isEqualTo(doc.id());
        assertThat(chunk.tenantId()).isEqualTo(doc.tenantId());
        assertThat(chunk.collectionId()).isEqualTo(doc.collectionId());
        assertThat(chunk.ordinal()).isZero();
        assertThat(chunk.content()).isEqualTo("some content");
        assertThat(chunk.tokenCount()).isEqualTo(3);
        assertThat(chunk.id()).isNotNull();
    }

    @Test
    void scope_returnsOwningScope() {
        var chunk = DocumentChunk.create(document(), 1, "content", 2);

        assertThat(chunk.scope()).isEqualTo(SCOPE);
    }

    @Test
    void constructor_rejectsNullDocumentId() {
        assertThatThrownBy(() -> new DocumentChunk(UUID.randomUUID(), null, "t", "c", 0, "x", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentId required");
    }

    @Test
    void constructor_rejectsNegativeOrdinal() {
        assertThatThrownBy(() -> new DocumentChunk(UUID.randomUUID(), UUID.randomUUID(), "t", "c", -1, "x", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordinal must be >= 0");
    }

    @Test
    void constructor_rejectsBlankContent() {
        assertThatThrownBy(() -> new DocumentChunk(UUID.randomUUID(), UUID.randomUUID(), "t", "c", 0, "  ", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void constructor_rejectsNegativeTokenCount() {
        assertThatThrownBy(() -> new DocumentChunk(UUID.randomUUID(), UUID.randomUUID(), "t", "c", 0, "x", -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenCount must be >= 0");
    }
}
