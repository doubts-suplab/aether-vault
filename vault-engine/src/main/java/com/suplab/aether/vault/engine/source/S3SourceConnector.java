package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.FetchedContent;
import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;

/**
 * {@link DocumentSourceConnector} for {@code s3://bucket/key} object-store sources.
 *
 * <p>Fetches an object through an injected {@link S3Client} (AWS SDK v2) — credentials and region
 * come from the standard provider chain, never from code — reads it as text, and caps the size. The
 * SDK client is injected rather than built here so the connector stays testable and Vault holds no
 * hardcoded secrets. The title is derived from the object key's last segment.</p>
 */
public class S3SourceConnector implements DocumentSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(S3SourceConnector.class);
    private static final String PREFIX = "s3://";

    private final S3Client s3Client;
    private final long maxBytes;

    /**
     * @param s3Client the AWS SDK v2 S3 client (credentials/region via the provider chain)
     * @param maxBytes maximum object size in bytes; larger objects are rejected
     */
    public S3SourceConnector(S3Client s3Client, long maxBytes) {
        if (s3Client == null) throw new IllegalArgumentException("s3Client required");
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be > 0");
        this.s3Client = s3Client;
        this.maxBytes = maxBytes;
    }

    @Override
    public String scheme() {
        return "s3";
    }

    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && sourceUri.startsWith(PREFIX);
    }

    @Override
    public FetchedContent fetch(String sourceUri) {
        if (!supports(sourceUri)) {
            throw new SourceFetchException("Unsupported URI for S3 connector: " + sourceUri);
        }
        var location = parse(sourceUri);

        try {
            var request = GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build();
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(request);
            byte[] body = object.asByteArray();
            if (body.length > maxBytes) {
                throw new SourceFetchException(
                        "Source exceeds max size (" + body.length + " > " + maxBytes + " bytes): " + sourceUri);
            }
            var contentType = normaliseContentType(object.response().contentType());
            var text = new String(body, StandardCharsets.UTF_8);
            log.info("Fetched S3 source uri={} bucket={} key={} bytes={}",
                    sourceUri, location.bucket(), location.key(), body.length);
            return new FetchedContent(sourceUri, titleFrom(location.key()), contentType, text);
        } catch (S3Exception e) {
            throw new SourceFetchException("Failed to fetch S3 source: " + sourceUri, e);
        }
    }

    private record S3Location(String bucket, String key) {}

    private static S3Location parse(String sourceUri) {
        var withoutScheme = sourceUri.substring(PREFIX.length());
        int slash = withoutScheme.indexOf('/');
        if (slash <= 0 || slash == withoutScheme.length() - 1) {
            throw new SourceFetchException("S3 URI must be s3://bucket/key: " + sourceUri);
        }
        return new S3Location(withoutScheme.substring(0, slash), withoutScheme.substring(slash + 1));
    }

    private static String normaliseContentType(String header) {
        if (header == null || header.isBlank()) {
            return "text/plain";
        }
        int semi = header.indexOf(';');
        var type = (semi >= 0 ? header.substring(0, semi) : header).trim().toLowerCase();
        return type.isBlank() ? "text/plain" : type;
    }

    private static String titleFrom(String key) {
        var segment = key.substring(key.lastIndexOf('/') + 1);
        return segment.isBlank() ? key : segment;
    }
}
