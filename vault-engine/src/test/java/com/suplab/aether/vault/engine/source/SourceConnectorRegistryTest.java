package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.FetchedContent;
import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceConnectorRegistryTest {

    /** A minimal connector that supports one scheme prefix. */
    private static DocumentSourceConnector connector(String scheme, String prefix) {
        return new DocumentSourceConnector() {
            @Override public String scheme() { return scheme; }
            @Override public boolean supports(String sourceUri) {
                return sourceUri != null && sourceUri.startsWith(prefix);
            }
            @Override public FetchedContent fetch(String sourceUri) {
                return new FetchedContent(sourceUri, "t", "text/plain", "body-from-" + scheme);
            }
        };
    }

    @Test
    void resolvesFirstSupportingConnector() {
        var registry = new SourceConnectorRegistry(List.of(
                connector("file", "file:"), connector("http", "http")));

        assertThat(registry.resolve("file:x").scheme()).isEqualTo("file");
        assertThat(registry.resolve("https://x").scheme()).isEqualTo("http");
    }

    @Test
    void defaultDeniesUnsupportedScheme() {
        var registry = new SourceConnectorRegistry(List.of(connector("file", "file:")));

        assertThatThrownBy(() -> registry.resolve("s3://bucket/key"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("No connector supports");
    }

    @Test
    void rejectsBlankUri() {
        var registry = new SourceConnectorRegistry(List.of(connector("file", "file:")));

        assertThatThrownBy(() -> registry.resolve(" "))
                .isInstanceOf(SourceFetchException.class);
    }

    @Test
    void rejectsEmptyConnectorList() {
        assertThatThrownBy(() -> new SourceConnectorRegistry(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
