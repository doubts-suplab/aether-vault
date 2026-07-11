package com.suplab.aether.vault.engine.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw document text into overlapping, fixed-size chunks suitable for embedding.
 *
 * <p>Chunking is deterministic and character-based: the text is walked in windows of
 * {@code chunkSize} characters, each window starting {@code chunkSize - overlap} characters after
 * the previous one so adjacent chunks share {@code overlap} characters of context (which keeps a
 * fact that straddles a boundary retrievable). The chunker is a pure utility with no framework or
 * I/O coupling, so it is trivially unit-testable.</p>
 */
public final class TextChunker {

    private final int chunkSize;
    private final int overlap;

    /**
     * @param chunkSize maximum characters per chunk (must be > 0)
     * @param overlap   characters shared between adjacent chunks (0 <= overlap < chunkSize)
     */
    public TextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        if (overlap < 0 || overlap >= chunkSize)
            throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * Splits text into chunks. Whitespace is trimmed; blank input yields an empty list. Each chunk
     * is itself trimmed and blank chunks are skipped.
     *
     * @param text the source text
     * @return ordered list of chunk strings (may be empty)
     */
    public List<String> chunk(String text) {
        var chunks = new ArrayList<String>();
        if (text == null) return chunks;
        var normalised = text.strip();
        if (normalised.isEmpty()) return chunks;

        int step = chunkSize - overlap;
        for (int start = 0; start < normalised.length(); start += step) {
            int end = Math.min(start + chunkSize, normalised.length());
            var slice = normalised.substring(start, end).strip();
            if (!slice.isEmpty()) {
                chunks.add(slice);
            }
            if (end == normalised.length()) break;
        }
        return chunks;
    }
}
