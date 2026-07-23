package com.suplab.aether.vault.engine.ingestion;

import com.suplab.aether.vault.domain.ContentChecksum;
import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.FetchedContent;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.engine.source.SourceConnectorRegistry;
import com.suplab.aether.vault.ports.DocumentIngestionPort;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.SourceIngestionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSourceIngestionServiceTest {

    private static final KnowledgeScope SCOPE = KnowledgeScope.of("tenant-1", "handbook");
    private static final String URI = "file:policy.md";

    /** A connector returning fixed text for the one URI. */
    private static DocumentSourceConnector connectorReturning(String text) {
        return new DocumentSourceConnector() {
            @Override public String scheme() { return "file"; }
            @Override public boolean supports(String sourceUri) { return URI.equals(sourceUri); }
            @Override public FetchedContent fetch(String sourceUri) {
                return new FetchedContent(sourceUri, "policy.md", "text/markdown", text);
            }
        };
    }

    /** Document store seeded with an optional existing record; captures saves. */
    private static final class FakeDocStore implements KnowledgeDocumentStore {
        private KnowledgeDocument current;
        KnowledgeDocument lastSaved;

        FakeDocStore(KnowledgeDocument seed) { this.current = seed; }

        @Override public void save(KnowledgeDocument document) { this.lastSaved = document; this.current = document; }
        @Override public Optional<KnowledgeDocument> findById(KnowledgeScope s, UUID id) { return Optional.empty(); }
        @Override public Optional<KnowledgeDocument> findBySourceUri(KnowledgeScope s, String uri) {
            return Optional.ofNullable(current);
        }
        @Override public List<KnowledgeDocument> findByCollection(KnowledgeScope s, int limit) { return List.of(); }
        @Override public long countByCollection(KnowledgeScope s) { return 0; }
        @Override public void delete(KnowledgeScope s, UUID id) { }
    }

    /** Ingestion port that records whether it ran and reports a fixed chunk count. */
    private static final class FakeIngestionPort implements DocumentIngestionPort {
        int calls = 0;
        @Override public IngestionResult ingest(KnowledgeDocument document, String rawText) {
            calls++;
            var indexed = document.markIndexed(3);
            return new IngestionResult(indexed.id(), indexed.chunkCount(), indexed.status());
        }
    }

    private static SourceIngestionPort service(FakeDocStore docStore, FakeIngestionPort ingest, String text) {
        var registry = new SourceConnectorRegistry(List.of(connectorReturning(text)));
        return new DefaultSourceIngestionService(registry, docStore, ingest);
    }

    @Test
    void newSourceIsIndexed() {
        var docStore = new FakeDocStore(null);
        var ingest = new FakeIngestionPort();

        var result = service(docStore, ingest, "phased rollouts are preferred")
                .ingestFromSource(SCOPE, URI);

        assertThat(result.outcome()).isEqualTo(SourceIngestionPort.Outcome.INDEXED);
        assertThat(result.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(result.chunkCount()).isEqualTo(3);
        assertThat(ingest.calls).isEqualTo(1);
        assertThat(docStore.lastSaved.sourceUri()).isEqualTo(URI);
    }

    @Test
    void unchangedSourceIsSkipped() {
        var text = "phased rollouts are preferred";
        // Existing INDEXED document with the SAME checksum as the fetched content.
        var existing = KnowledgeDocument
                .create(SCOPE, URI, "policy.md", "text/markdown", ContentChecksum.sha256(text))
                .markIndexed(3);
        var docStore = new FakeDocStore(existing);
        var ingest = new FakeIngestionPort();

        var result = service(docStore, ingest, text).ingestFromSource(SCOPE, URI);

        assertThat(result.outcome()).isEqualTo(SourceIngestionPort.Outcome.UNCHANGED);
        assertThat(result.documentId()).isEqualTo(existing.id());
        assertThat(ingest.calls).isZero();          // not re-embedded
        assertThat(docStore.lastSaved).isNull();     // not re-saved
    }

    @Test
    void changedSourceIsReindexedUnderSameDocumentId() {
        var existing = KnowledgeDocument
                .create(SCOPE, URI, "policy.md", "text/markdown", ContentChecksum.sha256("old content"))
                .markIndexed(2);
        var docStore = new FakeDocStore(existing);
        var ingest = new FakeIngestionPort();

        var result = service(docStore, ingest, "new content entirely").ingestFromSource(SCOPE, URI);

        assertThat(result.outcome()).isEqualTo(SourceIngestionPort.Outcome.INDEXED);
        assertThat(result.documentId()).isEqualTo(existing.id());   // identity reused
        assertThat(ingest.calls).isEqualTo(1);
        assertThat(docStore.lastSaved.id()).isEqualTo(existing.id());
        assertThat(docStore.lastSaved.checksum()).isEqualTo(ContentChecksum.sha256("new content entirely"));
    }

    @Test
    void staleExistingDocumentWithSameContentIsReindexed() {
        // Same checksum but status STALE (not INDEXED) → must re-index, not skip.
        var text = "content";
        var stale = KnowledgeDocument
                .create(SCOPE, URI, "policy.md", "text/markdown", ContentChecksum.sha256(text))
                .markIndexed(1)
                .markStale();
        var docStore = new FakeDocStore(stale);
        var ingest = new FakeIngestionPort();

        var result = service(docStore, ingest, text).ingestFromSource(SCOPE, URI);

        assertThat(result.outcome()).isEqualTo(SourceIngestionPort.Outcome.INDEXED);
        assertThat(ingest.calls).isEqualTo(1);
    }
}
