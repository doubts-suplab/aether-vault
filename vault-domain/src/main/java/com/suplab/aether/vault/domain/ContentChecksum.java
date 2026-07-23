package com.suplab.aether.vault.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the content fingerprint used for knowledge freshness / change detection.
 *
 * <p>The checksum of a document's source text is what drives {@link KnowledgeDocument#isStale}:
 * re-fetching a source and getting a different checksum means the content has changed and the
 * document must be re-indexed. Centralising the algorithm here keeps the ingest path (inline text
 * and source connectors) consistent — the same bytes always yield the same fingerprint.</p>
 */
public final class ContentChecksum {

    private ContentChecksum() {
    }

    /**
     * Returns the lowercase hex SHA-256 digest of {@code text} (UTF-8 encoded).
     *
     * @param text the source content to fingerprint (must not be {@code null})
     * @return a 64-character hex checksum
     */
    public static String sha256(String text) {
        if (text == null) throw new IllegalArgumentException("text required");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
