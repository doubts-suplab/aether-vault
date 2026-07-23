package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.SourceFetchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemSourceConnectorTest {

    @Test
    void fetchesAFileWithinTheAllowedRoot(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("handbook.md"), "# Handbook\nphased rollouts");
        var connector = new FilesystemSourceConnector(root, 1_000_000);

        var fetched = connector.fetch("file:handbook.md");

        assertThat(fetched.rawText()).contains("phased rollouts");
        assertThat(fetched.title()).isEqualTo("handbook.md");
        assertThat(fetched.contentType()).isEqualTo("text/markdown");
        assertThat(connector.supports("file:handbook.md")).isTrue();
        assertThat(connector.supports("https://x/y")).isFalse();
    }

    @Test
    void rejectsPathTraversalOutsideTheRoot(@TempDir Path root) throws IOException {
        // A secret sibling of the allowed root; the connector must never read it.
        var secret = root.getParent().resolve("secret.txt");
        Files.writeString(secret, "top secret");
        var connector = new FilesystemSourceConnector(root, 1_000_000);

        assertThatThrownBy(() -> connector.fetch("file:../secret.txt"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("outside the allowed root");
    }

    @Test
    void rejectsAbsolutePathOutsideTheRoot(@TempDir Path root) {
        var connector = new FilesystemSourceConnector(root, 1_000_000);

        assertThatThrownBy(() -> connector.fetch("file:///etc/passwd"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("outside the allowed root");
    }

    @Test
    void rejectsFilesOverTheSizeCap(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("big.txt"), "0123456789");
        var connector = new FilesystemSourceConnector(root, 4);

        assertThatThrownBy(() -> connector.fetch("file:big.txt"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("max size");
    }

    @Test
    void rejectsMissingFile(@TempDir Path root) {
        var connector = new FilesystemSourceConnector(root, 1_000_000);

        assertThatThrownBy(() -> connector.fetch("file:nope.md"))
                .isInstanceOf(SourceFetchException.class);
    }

    @Test
    void rejectsUnsupportedUri(@TempDir Path root) {
        var connector = new FilesystemSourceConnector(root, 1_000_000);

        assertThatThrownBy(() -> connector.fetch("s3://bucket/key"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("Unsupported");
    }
}
