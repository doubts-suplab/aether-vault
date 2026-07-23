package com.suplab.aether.vault.ports;

/**
 * Port for counting the tokens in a piece of text.
 *
 * <p>Token counts drive downstream budgeting — how much of a chunk fits in an agent's context
 * window. The naive {@code chars / 4} estimate is cheap but wrong for anything but average English
 * prose; a real tokenizer is far closer. Like the embedding service, the tokenizer is
 * <em>replaceable</em>: the ingestion and RAG paths depend on this port, never on a concrete
 * tokenizer, so the exact model tokenizer can be swapped in without touching them.</p>
 *
 * <p>Implementations live in {@code vault-engine}; the domain stays dependency-free.</p>
 */
public interface TokenCounter {

    /**
     * Counts the tokens in {@code text}.
     *
     * @param text the text to tokenize (may be empty)
     * @return the number of tokens (never negative)
     */
    int countTokens(String text);
}
