package com.suplab.aether.vault.engine.freshness;

import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeScope;

import com.suplab.aether.vault.ports.FreshnessPolicyStore;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.SourceIngestionPort;
import com.suplab.aether.vault.ports.StaleReingestionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link StaleReingestionPort} — refreshes {@code STALE} documents in auto-reingest
 * collections through their source connector.
 *
 * <p>It reads the collections that opted into {@code autoReingest} from the
 * {@link FreshnessPolicyStore}, finds each one's {@code STALE} documents, and re-ingests them via the
 * connector-driven {@link SourceIngestionPort} (which is checksum-aware — an unchanged source is
 * skipped, a changed one re-indexed in place). Best-effort: a source that fails to fetch is logged and
 * left {@code STALE} for the next run, so one bad source never stalls the sweep.</p>
 */
public class DefaultStaleReingestionService implements StaleReingestionPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultStaleReingestionService.class);

    private final FreshnessPolicyStore policyStore;
    private final KnowledgeDocumentStore documentStore;
    private final SourceIngestionPort sourceIngestion;

    public DefaultStaleReingestionService(FreshnessPolicyStore policyStore,
                                          KnowledgeDocumentStore documentStore,
                                          SourceIngestionPort sourceIngestion) {
        this.policyStore = policyStore;
        this.documentStore = documentStore;
        this.sourceIngestion = sourceIngestion;
    }

    @Override
    public ReingestionResult reingestStale(int maxScopes, int maxPerScope) {
        var scopes = policyStore.findAutoReingestScopes(maxScopes);
        long reingested = 0;
        long failed = 0;
        for (var scope : scopes) {
            for (var doc : documentStore.findByStatus(scope, DocumentStatus.STALE, maxPerScope)) {
                if (reingestOne(scope, doc.sourceUri())) {
                    reingested++;
                } else {
                    failed++;
                }
            }
        }
        log.info("Auto-reingestion sweep complete: scopes={} reingested={} failed={}",
                scopes.size(), reingested, failed);
        return new ReingestionResult(scopes.size(), reingested, failed);
    }

    /** Best-effort re-ingest of one source — a fetch failure is logged and does not stop the sweep. */
    private boolean reingestOne(KnowledgeScope scope, String sourceUri) {
        try {
            var result = sourceIngestion.ingestFromSource(scope, sourceUri);
            log.debug("Re-ingested stale document tenantId={} collectionId={} sourceUri={} outcome={}",
                    scope.tenantId(), scope.collectionId(), sourceUri, result.outcome());
            return true;
        } catch (RuntimeException e) {
            // Includes SourceFetchException — a source that cannot be fetched is left STALE for next run.
            log.warn("Auto-reingest failed tenantId={} collectionId={} sourceUri={} — left STALE: {}",
                    scope.tenantId(), scope.collectionId(), sourceUri, e.getMessage());
            return false;
        }
    }
}
