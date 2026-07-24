package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityRelation;
import com.suplab.aether.vault.domain.ExtractedEntity;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeEntity;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.EntityExtractor;
import com.suplab.aether.vault.ports.GraphExtractionPort;
import com.suplab.aether.vault.ports.KnowledgeGraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link GraphExtractionPort} — turns a document's chunks into knowledge-graph nodes and edges.
 *
 * <p>For each chunk it runs the pluggable {@link EntityExtractor}, upserts every mention as a
 * {@link KnowledgeEntity} (which reinforces its mention count), then records a
 * {@code co_occurs_with} relation between each distinct pair of entities that appeared together in
 * that chunk — the classic co-occurrence heuristic for relation extraction. Pairing is bounded to the
 * first {@code maxEntitiesForRelations} entities of a chunk so a dense chunk cannot produce a
 * quadratic edge blow-up. Edges are stored in a canonical direction (smaller UUID → larger) so a
 * symmetric co-occurrence is recorded once, and both entity upsert and relation insert are idempotent
 * in the store, making re-extraction safe.</p>
 *
 * <p>All work is scoped to the document's {@link KnowledgeScope}; the graph never spans collections.</p>
 */
public class DefaultKnowledgeGraphExtractionService implements GraphExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeGraphExtractionService.class);

    /** Default cap on entities-per-chunk considered for pairwise relations. */
    public static final int DEFAULT_MAX_ENTITIES_FOR_RELATIONS = 8;
    private static final String CO_OCCURS_WITH = "co_occurs_with";
    private static final double CO_OCCURRENCE_CONFIDENCE = 0.5;

    private final EntityExtractor extractor;
    private final KnowledgeGraphStore graphStore;
    private final int maxEntitiesForRelations;

    public DefaultKnowledgeGraphExtractionService(EntityExtractor extractor, KnowledgeGraphStore graphStore) {
        this(extractor, graphStore, DEFAULT_MAX_ENTITIES_FOR_RELATIONS);
    }

    public DefaultKnowledgeGraphExtractionService(EntityExtractor extractor, KnowledgeGraphStore graphStore,
                                                  int maxEntitiesForRelations) {
        this.extractor = extractor;
        this.graphStore = graphStore;
        this.maxEntitiesForRelations = maxEntitiesForRelations < 2
                ? DEFAULT_MAX_ENTITIES_FOR_RELATIONS : maxEntitiesForRelations;
    }

    @Override
    public ExtractionSummary extract(KnowledgeDocument document, List<String> chunks) {
        var scope = document.scope();
        Set<UUID> entitiesFound = new HashSet<>();
        Set<String> relationKeys = new HashSet<>();

        for (String chunk : chunks) {
            List<ExtractedEntity> mentions = extractor.extract(chunk);
            if (mentions.isEmpty()) continue;

            List<KnowledgeEntity> persisted = new ArrayList<>(mentions.size());
            for (ExtractedEntity mention : mentions) {
                var stored = graphStore.upsertEntity(KnowledgeEntity.create(scope, mention.name(), mention.type()));
                persisted.add(stored);
                entitiesFound.add(stored.id());
            }
            relateCoOccurring(scope, persisted, relationKeys);
        }

        var summary = new ExtractionSummary(entitiesFound.size(), relationKeys.size());
        log.info("Graph extraction documentId={} tenantId={} collectionId={} entities={} relations={}",
                document.id(), document.tenantId(), document.collectionId(),
                summary.entitiesFound(), summary.relationsCreated());
        return summary;
    }

    private void relateCoOccurring(KnowledgeScope scope, List<KnowledgeEntity> persisted,
                                   Set<String> relationKeys) {
        int cap = Math.min(persisted.size(), maxEntitiesForRelations);
        for (int i = 0; i < cap; i++) {
            for (int j = i + 1; j < cap; j++) {
                UUID a = persisted.get(i).id();
                UUID b = persisted.get(j).id();
                if (a.equals(b)) continue; // distinct surface forms should never collide, but guard anyway
                UUID source = a.compareTo(b) <= 0 ? a : b;
                UUID target = a.compareTo(b) <= 0 ? b : a;
                if (relationKeys.add(source + "|" + target)) {
                    graphStore.relate(scope, new EntityRelation(source, target, CO_OCCURS_WITH,
                            CO_OCCURRENCE_CONFIDENCE));
                }
            }
        }
    }
}
