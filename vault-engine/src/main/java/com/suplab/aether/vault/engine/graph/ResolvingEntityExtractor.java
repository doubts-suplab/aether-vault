package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.ExtractedEntity;
import com.suplab.aether.vault.ports.EntityExtractor;
import com.suplab.aether.vault.ports.EntityResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link EntityExtractor} decorator that applies {@link EntityResolver entity resolution} to a
 * delegate extractor's output and de-duplicates the canonical mentions.
 *
 * <p>Wrapping the extractor (rather than resolving inside each caller) means resolution happens
 * uniformly wherever the extractor is used — both at ingest, when the graph-extraction service reads
 * mentions to upsert, and at query time, when entity-aware RAG recognises entities in the query — so
 * a document's {@code "Ada Lovelace"} and a query's {@code "ada lovelace"} resolve to the same node.
 * Duplicate canonical mentions (same name + type, case-insensitive) are collapsed, preserving first
 * appearance order.</p>
 */
public class ResolvingEntityExtractor implements EntityExtractor {

    private final EntityExtractor delegate;
    private final EntityResolver resolver;

    public ResolvingEntityExtractor(EntityExtractor delegate, EntityResolver resolver) {
        this.delegate = delegate;
        this.resolver = resolver == null ? EntityResolver.NONE : resolver;
    }

    @Override
    public List<ExtractedEntity> extract(String text) {
        Map<String, ExtractedEntity> deduped = new LinkedHashMap<>();
        for (ExtractedEntity mention : delegate.extract(text)) {
            var resolved = resolver.resolve(mention);
            deduped.putIfAbsent(key(resolved), resolved);
        }
        return new ArrayList<>(deduped.values());
    }

    private static String key(ExtractedEntity e) {
        return e.type().name() + '|' + e.name().toLowerCase();
    }
}
