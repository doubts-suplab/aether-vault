package com.suplab.aether.vault.engine.erasure;

import com.suplab.aether.vault.domain.KnowledgeErasureResult;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.KnowledgeErasurePort;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link KnowledgeErasurePort} — right-to-erasure for a knowledge collection (GDPR
 * Art. 17).
 *
 * <p>Removes everything Vault derived from a collection's sources, in dependency order: the embedded
 * chunks first, then the document records, then the knowledge-graph entities (relations cascade with
 * their entities). It reports the counts removed. A collection with nothing stored returns zero
 * counts — idempotent and safe to retry.</p>
 *
 * <p>The service is framework-free and depends only on port interfaces; the API module assembles it
 * via constructor injection.</p>
 */
public class DefaultKnowledgeErasureService implements KnowledgeErasurePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeErasureService.class);

    private final KnowledgeDocumentStore documentStore;
    private final DocumentChunkStore chunkStore;
    private final KnowledgeGraphStore graphStore;

    public DefaultKnowledgeErasureService(KnowledgeDocumentStore documentStore,
                                          DocumentChunkStore chunkStore,
                                          KnowledgeGraphStore graphStore) {
        this.documentStore = documentStore;
        this.chunkStore = chunkStore;
        this.graphStore = graphStore;
    }

    @Override
    public KnowledgeErasureResult eraseCollection(KnowledgeScope scope) {
        int chunksErased = chunkStore.deleteByCollection(scope);
        int documentsErased = documentStore.deleteByCollection(scope);
        int entitiesErased = graphStore.deleteByCollection(scope);

        var result = new KnowledgeErasureResult(scope.tenantId(), scope.collectionId(),
                documentsErased, chunksErased, entitiesErased);
        if (result.isEmpty()) {
            log.info("Erasure requested for tenantId={} collectionId={} — nothing to erase",
                    scope.tenantId(), scope.collectionId());
        } else {
            log.info("Erased collection tenantId={} collectionId={} documents={} chunks={} entities={}",
                    scope.tenantId(), scope.collectionId(), documentsErased, chunksErased, entitiesErased);
        }
        return result;
    }
}
