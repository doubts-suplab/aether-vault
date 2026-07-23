package com.suplab.aether.vault.engine.tokenizer;

import com.suplab.aether.vault.ports.TokenCounter;

/**
 * Dependency-free {@link TokenCounter} fallback for when the BPE tokenizer is disabled.
 *
 * <p>Counts by splitting on whitespace and adding a sub-word allowance for long words plus a term
 * for standalone punctuation — a closer approximation than {@code chars / 4} without pulling in a
 * tokenizer library. It is deliberately an estimate; when accuracy matters,
 * {@link JtokkitTokenCounter} is the default.</p>
 */
public class HeuristicTokenCounter implements TokenCounter {

    /** Real tokenizers split long words into sub-word pieces roughly every this many characters. */
    private static final int CHARS_PER_SUBWORD = 4;

    @Override
    public int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            // A word contributes at least one token, plus one per extra sub-word slice for long words.
            tokens += Math.max(1, (word.length() + CHARS_PER_SUBWORD - 1) / CHARS_PER_SUBWORD);
        }
        return tokens;
    }
}
