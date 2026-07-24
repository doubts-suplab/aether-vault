package com.suplab.aether.vault.domain;

/**
 * A single entity mention extracted from document text, <em>before</em> it is persisted to the
 * knowledge graph.
 *
 * <p>This is the raw output of an {@link com.suplab.aether.vault.ports.EntityExtractor}: a surface
 * form and a coarse {@link EntityType}. It carries no identity or scope — the graph-extraction
 * service resolves it against a {@link KnowledgeScope} (upserting a {@link KnowledgeEntity} and
 * bumping its mention count) when it lands the extraction. Keeping extraction output distinct from
 * the persisted node keeps the extractor free of any store or tenancy concern.</p>
 *
 * @param name the extracted surface form (trimmed, non-blank)
 * @param type the extracted entity category (never null — defaults to {@link EntityType#OTHER})
 */
public record ExtractedEntity(String name, EntityType type) {

    public ExtractedEntity {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        name = name.trim();
        if (type == null) type = EntityType.OTHER;
    }

    /**
     * Factory mirroring the {@code of(...)} idiom used across the domain.
     */
    public static ExtractedEntity of(String name, EntityType type) {
        return new ExtractedEntity(name, type);
    }
}
