package com.suplab.aether.vault.engine.ingestion;

import com.suplab.aether.vault.domain.ContentChecksum;
import com.suplab.aether.vault.domain.DocumentStatus;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.engine.source.SourceConnectorRegistry;
import com.suplab.aether.vault.ports.DocumentIngestionPort;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.SourceIngestionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link SourceIngestionPort} — the connector-driven ingest path.
 *
 * <p>It composes an existing {@link DocumentIngestionPort} with the {@link SourceConnectorRegistry}:
 * resolve a connector, fetch, checksum, and decide whether work is needed:</p>
 * <ol>
 *   <li>An already-{@code INDEXED} document whose checksum matches the freshly fetched content is
 *       {@link SourceIngestionPort.Outcome#UNCHANGED} — it is <em>not</em> re-embedded.</li>
 *   <li>A new source is registered as a fresh {@link KnowledgeDocument} and indexed.</li>
 *   <li>A changed source reuses its existing document identity via
 *       {@link KnowledgeDocument#reregister} and is re-indexed in place — one {@code sourceUri}
 *       keeps one stable document.</li>
 * </ol>
 *
 * <p>Checksum computation and the collection boundary are inherited from the domain: every lookup
 * and write is scoped by {@link KnowledgeScope}.</p>
 */
public class DefaultSourceIngestionService implements SourceIngestionPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultSourceIngestionService.class);

    private final SourceConnectorRegistry connectorRegistry;
    private final KnowledgeDocumentStore documentStore;
    private final DocumentIngestionPort ingestionPort;

    public DefaultSourceIngestionService(SourceConnectorRegistry connectorRegistry,
                                         KnowledgeDocumentStore documentStore,
                                         DocumentIngestionPort ingestionPort) {
        this.connectorRegistry = connectorRegistry;
        this.documentStore = documentStore;
        this.ingestionPort = ingestionPort;
    }

    @Override
    public SourceIngestionResult ingestFromSource(KnowledgeScope scope, String sourceUri) {
        var connector = connectorRegistry.resolve(sourceUri);
        var fetched = connector.fetch(sourceUri);
        var checksum = ContentChecksum.sha256(fetched.rawText());

        var existing = documentStore.findBySourceUri(scope, sourceUri);
        if (existing.isPresent()
                && existing.get().status() == DocumentStatus.INDEXED
                && !existing.get().isStale(checksum)) {
            var doc = existing.get();
            log.info("Source unchanged, skipping re-index tenantId={} collectionId={} sourceUri={} documentId={}",
                    scope.tenantId(), scope.collectionId(), sourceUri, doc.id());
            return new SourceIngestionResult(doc.id(), doc.status(), doc.chunkCount(), Outcome.UNCHANGED);
        }

        KnowledgeDocument document = existing
                .map(prior -> prior.reregister(checksum, fetched.title(), fetched.contentType()))
                .orElseGet(() -> KnowledgeDocument.create(
                        scope, sourceUri, fetched.title(), fetched.contentType(), checksum));
        documentStore.save(document);

        var result = ingestionPort.ingest(document, fetched.rawText());
        var outcome = result.status() == DocumentStatus.FAILED ? Outcome.FAILED : Outcome.INDEXED;
        log.info("Source ingested tenantId={} collectionId={} sourceUri={} documentId={} status={} chunks={} outcome={}",
                scope.tenantId(), scope.collectionId(), sourceUri, result.documentId(),
                result.status(), result.chunkCount(), outcome);
        return new SourceIngestionResult(result.documentId(), result.status(), result.chunkCount(), outcome);
    }
}
