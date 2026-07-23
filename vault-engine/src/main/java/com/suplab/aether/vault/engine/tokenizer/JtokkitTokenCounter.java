package com.suplab.aether.vault.engine.tokenizer;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.suplab.aether.vault.ports.TokenCounter;

/**
 * {@link TokenCounter} backed by a real byte-pair-encoding tokenizer (jtokkit).
 *
 * <p>Uses the {@code cl100k_base} encoding by default — the tokenizer of the GPT-3.5/4 family and a
 * strong proxy for context-window budgeting, since a RAG context assembled by Vault is consumed by
 * the caller's LLM, not by the embedding model. This is a genuine tokenizer rather than the
 * {@code chars / 4} heuristic it replaces; a different {@link EncodingType} can be supplied to match
 * a different downstream model.</p>
 *
 * <p>The jtokkit {@link Encoding} is thread-safe and immutable, so a single instance is shared.</p>
 */
public class JtokkitTokenCounter implements TokenCounter {

    private final Encoding encoding;

    /** Creates a counter using the {@code cl100k_base} encoding. */
    public JtokkitTokenCounter() {
        this(EncodingType.CL100K_BASE);
    }

    /**
     * @param encodingType the byte-pair-encoding to count with
     */
    public JtokkitTokenCounter(EncodingType encodingType) {
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(encodingType);
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }
}
