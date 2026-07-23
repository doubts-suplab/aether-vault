package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchedContentTest {

    @Test
    void holdsFetchedFields() {
        var fetched = new FetchedContent("file:notes.md", "notes.md", "text/markdown", "# hello");

        assertThat(fetched.sourceUri()).isEqualTo("file:notes.md");
        assertThat(fetched.title()).isEqualTo("notes.md");
        assertThat(fetched.contentType()).isEqualTo("text/markdown");
        assertThat(fetched.rawText()).isEqualTo("# hello");
    }

    @Test
    void defaultsBlankContentTypeToTextPlain() {
        var fetched = new FetchedContent("http://x/y", "y", " ", "body");

        assertThat(fetched.contentType()).isEqualTo("text/plain");
    }

    @Test
    void allowsEmptyRawText() {
        var fetched = new FetchedContent("http://x/y", "y", "text/plain", "");

        assertThat(fetched.rawText()).isEmpty();
    }

    @Test
    void rejectsBlankSourceUri() {
        assertThatThrownBy(() -> new FetchedContent(" ", "t", "text/plain", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceUri");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new FetchedContent("u", " ", "text/plain", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void rejectsNullRawText() {
        assertThatThrownBy(() -> new FetchedContent("u", "t", "text/plain", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawText");
    }
}
