package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.SourceFetchException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3SourceConnectorTest {

    private static ResponseBytes<GetObjectResponse> object(String contentType, String body) {
        var response = GetObjectResponse.builder().contentType(contentType).build();
        return ResponseBytes.fromByteArray(response, body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fetchesAnObject() {
        var s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(object("text/markdown", "# runbook\nrollback steps"));
        var connector = new S3SourceConnector(s3, 1_000_000);

        var fetched = connector.fetch("s3://kb-bucket/runbooks/deploy.md");

        assertThat(fetched.rawText()).contains("rollback steps");
        assertThat(fetched.title()).isEqualTo("deploy.md");
        assertThat(fetched.contentType()).isEqualTo("text/markdown");
        assertThat(connector.scheme()).isEqualTo("s3");
        assertThat(connector.supports("s3://b/k")).isTrue();
        assertThat(connector.supports("https://x/y")).isFalse();
    }

    @Test
    void defaultsBlankContentType() {
        var s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(object(null, "body"));
        var connector = new S3SourceConnector(s3, 1_000_000);

        assertThat(connector.fetch("s3://b/k.txt").contentType()).isEqualTo("text/plain");
    }

    @Test
    void rejectsObjectsOverTheSizeCap() {
        var s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(object("text/plain", "0123456789"));
        var connector = new S3SourceConnector(s3, 4);

        assertThatThrownBy(() -> connector.fetch("s3://b/big.txt"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("max size");
    }

    @Test
    void wrapsS3Errors() {
        var s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("nope").build());
        var connector = new S3SourceConnector(s3, 1_000_000);

        assertThatThrownBy(() -> connector.fetch("s3://b/missing"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("Failed to fetch S3 source");
    }

    @Test
    void rejectsMalformedS3Uri() {
        var connector = new S3SourceConnector(mock(S3Client.class), 1_000_000);

        assertThatThrownBy(() -> connector.fetch("s3://bucket-only"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("bucket/key");
    }

    @Test
    void rejectsUnsupportedScheme() {
        var connector = new S3SourceConnector(mock(S3Client.class), 1_000_000);

        assertThatThrownBy(() -> connector.fetch("file:/etc/hosts"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("Unsupported");
    }
}
