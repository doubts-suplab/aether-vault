package com.suplab.aether.vault.engine.tokenizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JtokkitTokenCounterTest {

    private final JtokkitTokenCounter counter = new JtokkitTokenCounter();

    @Test
    void emptyCountsZero() {
        assertThat(counter.countTokens("")).isZero();
        assertThat(counter.countTokens(null)).isZero();
    }

    @Test
    void countsRealBpeTokens() {
        // "hello world" is 2 tokens under cl100k_base.
        assertThat(counter.countTokens("hello world")).isEqualTo(2);
    }

    @Test
    void isMoreThanWordCountForSubwordHeavyText() {
        // A rare long word tokenizes into several sub-word pieces.
        assertThat(counter.countTokens("antidisestablishmentarianism")).isGreaterThan(1);
    }

    @Test
    void scalesWithText() {
        int small = counter.countTokens("short");
        int large = counter.countTokens("a much longer passage of text with many more tokens in it than the small one");
        assertThat(large).isGreaterThan(small);
    }
}
