package com.suplab.aether.vault.ports;

import com.suplab.aether.vault.domain.ExtractedEntity;

import java.util.List;

/**
 * Port for named-entity extraction over a piece of document text.
 *
 * <p>An extractor turns raw text into a list of {@link ExtractedEntity} mentions — the "NER" step
 * of knowledge-graph construction. The port is deliberately model-agnostic: the default engine
 * implementation is a dependency-free heuristic (so Vault extracts a graph with no NLP runtime or
 * Ollama present), and a richer LLM- or model-based extractor can be substituted behind the same
 * interface, mirroring how {@code TokenCounter} and the embedding service are pluggable.</p>
 *
 * <p>Extraction is per-text and side-effect-free: it neither persists nor scopes anything. The
 * caller (the graph-extraction service) resolves the returned mentions against a collection.</p>
 */
public interface EntityExtractor {

    /**
     * Extracts entity mentions from a single piece of text.
     *
     * @param text the text to analyse (a document chunk, typically)
     * @return the distinct entity mentions found (may be empty; never null)
     */
    List<ExtractedEntity> extract(String text);
}
