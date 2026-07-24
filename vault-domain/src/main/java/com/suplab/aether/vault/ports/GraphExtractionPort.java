package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.KnowledgeDocument;

import java.util.List;

/**
 * Driving port for populating the knowledge graph from a document's chunks.
 *
 * <p>This is the Phase 2 pipeline seam: given a document's chunk texts, run entity extraction over
 * each chunk, upsert the entities into the {@link KnowledgeGraphStore} (reinforcing mention counts),
 * and record co-occurrence relations between entities that appear together in a chunk. It is invoked
 * automatically at the end of ingestion, but is a distinct port so it can also be driven on its own
 * (e.g. re-extraction) and so ingestion has no hard dependency on the graph.</p>
 *
 * <p>Extraction is best-effort augmentation: a failure here never invalidates the indexed document.
 * All work is scoped to the document's {@code tenantId} + {@code collectionId}; the graph never spans
 * collections.</p>
 */
public interface GraphExtractionPort {

    /**
     * The outcome of extracting a graph from one document.
     *
     * @param entitiesFound   distinct entities observed across the document's chunks
     * @param relationsCreated co-occurrence relations recorded between them
     */
    record ExtractionSummary(int entitiesFound, int relationsCreated) {}

    /**
     * Extracts entities and co-occurrence relations from a document's chunks into the knowledge graph.
     *
     * @param document the (already indexed) document supplying scope and provenance
     * @param chunks   the document's chunk texts, in order
     * @return a summary of how many entities and relations were landed
     */
    ExtractionSummary extract(KnowledgeDocument document, List<String> chunks);
}
