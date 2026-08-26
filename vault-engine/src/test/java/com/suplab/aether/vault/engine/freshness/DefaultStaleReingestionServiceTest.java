package com.suplab.aether.vault.engine.freshness;

import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.FreshnessPolicy;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.FreshnessPolicyStore;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.SourceIngestionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStaleReingestionServiceTest {

    private static final KnowledgeScope SCOPE = new KnowledgeScope("acme", "handbook");

    private static final class FakePolicyStore implements FreshnessPolicyStore {
        final List<KnowledgeScope> autoReingest;
        FakePolicyStore(List<KnowledgeScope> autoReingest) { this.autoReingest = autoReingest; }
        @Override public void save(FreshnessPolicy p) { }
        @Override public Optional<FreshnessPolicy> find(KnowledgeScope s) { return Optional.empty(); }
        @Override public List<KnowledgeScope> findAutoReingestScopes(int limit) { return autoReingest; }
    }

    /** Doc store returning a fixed set of STALE documents for the scope. */
    private static final class FakeDocStore implements KnowledgeDocumentStore {
        final List<KnowledgeDocument> stale;
        FakeDocStore(List<KnowledgeDocument> stale) { this.stale = stale; }
        @Override public void save(KnowledgeDocument d) { }
        @Override public Optional<KnowledgeDocument> findById(KnowledgeScope s, UUID id) { return Optional.empty(); }
        @Override public Optional<KnowledgeDocument> findBySourceUri(KnowledgeScope s, String u) { return Optional.empty(); }
        @Override public List<KnowledgeDocument> findByCollection(KnowledgeScope s, int l) { return List.of(); }
        @Override public List<KnowledgeDocument> findByStatus(KnowledgeScope s, DocumentStatus st, int l) {
            return st == DocumentStatus.STALE ? stale : List.of();
        }
        @Override public long countByCollection(KnowledgeScope s) { return 0; }
        @Override public void delete(KnowledgeScope s, UUID id) { }
        @Override public int deleteByCollection(KnowledgeScope s) { return 0; }
    }

    /** Ingestion port that succeeds, or fails for a nominated source URI. */
    private static final class FakeSourceIngestion implements SourceIngestionPort {
        final String failUri;
        int calls = 0;
        FakeSourceIngestion(String failUri) { this.failUri = failUri; }
        @Override public SourceIngestionResult ingestFromSource(KnowledgeScope scope, String sourceUri) {
            calls++;
            if (sourceUri.equals(failUri)) throw new SourceFetchException("unreachable");
            return new SourceIngestionResult(UUID.randomUUID(), DocumentStatus.INDEXED, 3, Outcome.INDEXED);
        }
    }

    private static KnowledgeDocument stale(String uri) {
        return KnowledgeDocument.create(SCOPE, uri, "t", "text/plain", "sum").markIndexed(1).markStale();
    }

    @Test
    void reingestsStaleDocumentsAndReportsCounts() {
        var docStore = new FakeDocStore(List.of(stale("file:a"), stale("file:b")));
        var ingestion = new FakeSourceIngestion(null);
        var service = new DefaultStaleReingestionService(
                new FakePolicyStore(List.of(SCOPE)), docStore, ingestion);

        var result = service.reingestStale(100, 50);

        assertThat(result.scopesScanned()).isEqualTo(1);
        assertThat(result.documentsReingested()).isEqualTo(2);
        assertThat(result.documentsFailed()).isZero();
        assertThat(ingestion.calls).isEqualTo(2);
    }

    @Test
    void aFailingSourceIsCountedAndDoesNotStopTheSweep() {
        var docStore = new FakeDocStore(List.of(stale("file:ok"), stale("file:bad"), stale("file:ok2")));
        var ingestion = new FakeSourceIngestion("file:bad");
        var service = new DefaultStaleReingestionService(
                new FakePolicyStore(List.of(SCOPE)), docStore, ingestion);

        var result = service.reingestStale(100, 50);

        assertThat(result.documentsReingested()).isEqualTo(2);   // ok + ok2
        assertThat(result.documentsFailed()).isEqualTo(1);       // bad
        assertThat(ingestion.calls).isEqualTo(3);                // all attempted
    }

    @Test
    void noAutoReingestScopesIsANoOp() {
        var service = new DefaultStaleReingestionService(
                new FakePolicyStore(List.of()), new FakeDocStore(List.of()), new FakeSourceIngestion(null));

        var result = service.reingestStale(100, 50);

        assertThat(result.scopesScanned()).isZero();
        assertThat(result.documentsReingested()).isZero();
    }
}
