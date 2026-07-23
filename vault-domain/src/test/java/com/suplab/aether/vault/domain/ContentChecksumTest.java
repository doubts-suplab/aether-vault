package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentChecksumTest {

    @Test
    void isDeterministicForSameContent() {
        assertThat(ContentChecksum.sha256("hello world"))
                .isEqualTo(ContentChecksum.sha256("hello world"));
    }

    @Test
    void differsForDifferentContent() {
        assertThat(ContentChecksum.sha256("a")).isNotEqualTo(ContentChecksum.sha256("b"));
    }

    @Test
    void producesKnownSha256HexLength() {
        var sum = ContentChecksum.sha256("anything");

        assertThat(sum).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void matchesKnownVector() {
        // SHA-256 of the empty string.
        assertThat(ContentChecksum.sha256(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> ContentChecksum.sha256(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
