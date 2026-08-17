package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.ExtractedEntity;

/**
 * Port for <em>entity resolution</em> — canonicalising an extracted mention so that surface-form
 * variants of the same entity collapse to one node.
 *
 * <p>An extractor emits raw surface forms ({@code "Ada Lovelace"}, {@code "ada lovelace"},
 * {@code "Suplab's"}); without resolution each variant would upsert a distinct
 * {@link com.suplab.aether.vault.domain.KnowledgeEntity}, fragmenting the graph. A resolver maps a
 * mention to a canonical mention (same {@link com.suplab.aether.vault.domain.EntityType}, normalised
 * name) so equivalent forms share a node. Applied uniformly at ingest <em>and</em> at query time (both
 * go through the same {@link EntityExtractor}), it keeps writes and lookups aligned.</p>
 *
 * <p>Like {@link EntityExtractor}, resolution is deliberately pluggable: the default is a
 * deterministic, dependency-free normaliser; a smarter resolver (alias tables, embedding-based
 * clustering) can be substituted behind this port. {@link #NONE} is the identity resolver.</p>
 */
public interface EntityResolver {

    /**
     * Returns the canonical form of a mention. Implementations must be idempotent — resolving an
     * already-canonical mention returns an equivalent mention.
     *
     * @param mention the raw extracted mention
     * @return the canonicalised mention (never {@code null})
     */
    ExtractedEntity resolve(ExtractedEntity mention);

    /** The identity resolver — returns the mention unchanged (resolution disabled). */
    EntityResolver NONE = mention -> mention;
}
