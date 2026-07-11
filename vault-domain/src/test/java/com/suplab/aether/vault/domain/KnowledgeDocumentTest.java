package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeDocumentTest {

    private static final KnowledgeScope SCOPE = KnowledgeScope.of("tenant-1", "handbook");

    @Test
    void create_startsPendingWithNoChunks() {
        var doc = KnowledgeDocument.create(SCOPE, "s3://docs/readme.md", "Readme", "text/markdown", "abc123");

        assertThat(doc.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(doc.chunkCount()).isZero();
        assertThat(doc.indexedAt()).isNull();
        assertThat(doc.tenantId()).isEqualTo("tenant-1");
        assertThat(doc.collectionId()).isEqualTo("handbook");
        assertThat(doc.checksum()).isEqualTo("abc123");
        assertThat(doc.id()).isNotNull();
    }

    @Test
    void create_assignsDistinctIds() {
        var a = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x");
        var b = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x");

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void scope_returnsOwningTenantAndCollection() {
        var doc = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x");

        assertThat(doc.scope()).isEqualTo(SCOPE);
    }

    @Test
    void markIndexed_setsStatusChunkCountAndIndexedAt() {
        var doc = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x");

        var indexed = doc.markIndexed(7);

        assertThat(indexed.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexed.chunkCount()).isEqualTo(7);
        assertThat(indexed.indexedAt()).isNotNull();
        assertThat(indexed.id()).isEqualTo(doc.id());
    }

    @Test
    void markIndexed_rejectsNegativeChunkCount() {
        var doc = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x");

        assertThatThrownBy(() -> doc.markIndexed(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("producedChunks must be >= 0");
    }

    @Test
    void markStale_transitionsToStale() {
        var doc = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x").markIndexed(3);

        var stale = doc.markStale();

        assertThat(stale.status()).isEqualTo(DocumentStatus.STALE);
        assertThat(stale.chunkCount()).isEqualTo(3);
        assertThat(stale.indexedAt()).isEqualTo(doc.indexedAt());
    }

    @Test
    void markFailed_transitionsToFailed() {
        var doc = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "x");

        assertThat(doc.markFailed().status()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void isStale_detectsChecksumDrift() {
        var doc = KnowledgeDocument.create(SCOPE, "uri", "t", "text/plain", "checksum-v1");

        assertThat(doc.isStale("checksum-v2")).isTrue();
        assertThat(doc.isStale("checksum-v1")).isFalse();
        assertThat(doc.isStale(null)).isFalse();
    }

    @Test
    void constructor_defaultsBlankContentTypeToTextPlain() {
        var doc = new KnowledgeDocument(UUID.randomUUID(), "t", "c", "uri", "title", "  ", "sum",
                DocumentStatus.PENDING, 0, Instant.now(), null, Instant.now());

        assertThat(doc.contentType()).isEqualTo("text/plain");
    }

    @Test
    void constructor_rejectsBlankTenantId() {
        assertThatThrownBy(() -> new KnowledgeDocument(UUID.randomUUID(), " ", "c", "uri", "title",
                "text/plain", "sum", DocumentStatus.PENDING, 0, Instant.now(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId required");
    }

    @Test
    void constructor_rejectsBlankCollectionId() {
        assertThatThrownBy(() -> new KnowledgeDocument(UUID.randomUUID(), "t", "", "uri", "title",
                "text/plain", "sum", DocumentStatus.PENDING, 0, Instant.now(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionId required");
    }

    @Test
    void constructor_rejectsBlankChecksum() {
        assertThatThrownBy(() -> new KnowledgeDocument(UUID.randomUUID(), "t", "c", "uri", "title",
                "text/plain", " ", DocumentStatus.PENDING, 0, Instant.now(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum required");
    }

    @Test
    void constructor_rejectsNegativeChunkCount() {
        assertThatThrownBy(() -> new KnowledgeDocument(UUID.randomUUID(), "t", "c", "uri", "title",
                "text/plain", "sum", DocumentStatus.PENDING, -1, Instant.now(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkCount must be >= 0");
    }

    @Test
    void needsIndexing_trueForPendingStaleFailed() {
        assertThat(DocumentStatus.PENDING.needsIndexing()).isTrue();
        assertThat(DocumentStatus.STALE.needsIndexing()).isTrue();
        assertThat(DocumentStatus.FAILED.needsIndexing()).isTrue();
        assertThat(DocumentStatus.INDEXED.needsIndexing()).isFalse();
    }
}
