package com.suplab.aether.vault.engine.tokenizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicTokenCounterTest {

    private final HeuristicTokenCounter counter = new HeuristicTokenCounter();

    @Test
    void emptyAndBlankCountZero() {
        assertThat(counter.countTokens("")).isZero();
        assertThat(counter.countTokens("   ")).isZero();
        assertThat(counter.countTokens(null)).isZero();
    }

    @Test
    void shortWordsAreOneTokenEach() {
        assertThat(counter.countTokens("the cat sat")).isEqualTo(3);
    }

    @Test
    void longWordsContributeMultipleSubwordTokens() {
        // 16-char word → ceil(16/4) = 4 tokens; single short word would be 1.
        assertThat(counter.countTokens("internationalis")).isGreaterThan(1);
    }

    @Test
    void scalesWithLength() {
        int small = counter.countTokens("a short sentence here");
        int large = counter.countTokens("a considerably longer sentence with many more words than before");
        assertThat(large).isGreaterThan(small);
    }
}
