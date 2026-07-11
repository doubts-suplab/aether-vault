package com.suplab.aether.vault.engine.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextChunkerTest {

    @Test
    void chunk_splitsIntoFixedSizeWindows() {
        var chunker = new TextChunker(10, 0);

        var chunks = chunker.chunk("abcdefghijklmnopqrst"); // 20 chars

        assertThat(chunks).containsExactly("abcdefghij", "klmnopqrst");
    }

    @Test
    void chunk_appliesOverlapBetweenAdjacentChunks() {
        var chunker = new TextChunker(10, 4); // step = 6

        var chunks = chunker.chunk("abcdefghijklmnopqr"); // 18 chars

        // windows start at 0, 6, 12 → last window covers chars 12..17
        assertThat(chunks).containsExactly("abcdefghij", "ghijklmnop", "mnopqr");
    }

    @Test
    void chunk_returnsSingleChunkWhenShorterThanChunkSize() {
        var chunker = new TextChunker(100, 10);

        assertThat(chunker.chunk("short text")).containsExactly("short text");
    }

    @Test
    void chunk_returnsEmptyForBlankOrNull() {
        var chunker = new TextChunker(10, 2);

        assertThat(chunker.chunk("   ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void chunk_trimsSurroundingWhitespace() {
        var chunker = new TextChunker(100, 0);

        assertThat(chunker.chunk("  hello  ")).containsExactly("hello");
    }

    @Test
    void constructor_rejectsNonPositiveChunkSize() {
        assertThatThrownBy(() -> new TextChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize must be > 0");
    }

    @Test
    void constructor_rejectsOverlapNotLessThanChunkSize() {
        assertThatThrownBy(() -> new TextChunker(10, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap must be in [0, chunkSize)");
    }
}
