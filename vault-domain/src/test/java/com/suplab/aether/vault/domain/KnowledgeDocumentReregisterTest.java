package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeDocumentReregisterTest {

    private static final KnowledgeScope SCOPE = KnowledgeScope.of("tenant-1", "handbook");

    @Test
    void reregisterKeepsIdentityAndResetsForReindex() {
        var indexed = KnowledgeDocument
                .create(SCOPE, "file:policy.md", "policy.md", "text/markdown", "sum-old")
                .markIndexed(5);

        var reregistered = indexed.reregister("sum-new", "Policy v2", "text/plain");

        // identity preserved
        assertThat(reregistered.id()).isEqualTo(indexed.id());
        assertThat(reregistered.sourceUri()).isEqualTo("file:policy.md");
        assertThat(reregistered.tenantId()).isEqualTo("tenant-1");
        assertThat(reregistered.collectionId()).isEqualTo("handbook");
        assertThat(reregistered.createdAt()).isEqualTo(indexed.createdAt());
        // reset for a fresh index pass
        assertThat(reregistered.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(reregistered.chunkCount()).isZero();
        assertThat(reregistered.checksum()).isEqualTo("sum-new");
        assertThat(reregistered.title()).isEqualTo("Policy v2");
        assertThat(reregistered.contentType()).isEqualTo("text/plain");
    }

    @Test
    void reregisterMarksThePreviouslyIndexedContentStaleByChecksum() {
        var indexed = KnowledgeDocument
                .create(SCOPE, "file:policy.md", "policy.md", "text/markdown", "sum-old")
                .markIndexed(3);

        assertThat(indexed.isStale("sum-new")).isTrue();
        assertThat(indexed.isStale("sum-old")).isFalse();
    }

    @Test
    void reregisterRejectsBlankChecksum() {
        var doc = KnowledgeDocument.create(SCOPE, "file:policy.md", "policy.md", "text/markdown", "sum");

        assertThatThrownBy(() -> doc.reregister(" ", "t", "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newChecksum");
    }
}
