package com.suplab.aether.vault.domain;

import java.util.List;

/**
 * The assembled, prompt-ready output of the RAG pipeline: the retrieved chunks plus a single
 * length-bounded context string built from them.
 *
 * <p>Aether Vault performs the <em>retrieval</em> half of Retrieval-Augmented Generation and
 * returns the assembled context to the caller (typically an Aether Grid agent) to place into its
 * own prompt. The context is bounded to {@link #MAX_CONTEXT_CHARS} characters so a single query
 * cannot return an unbounded slice of the corpus, mirroring the bounded-projection discipline of
 * the wider ecosystem.</p>
 *
 * @param query            the original query text
 * @param chunks           the retrieved chunks, nearest-first
 * @param assembledContext the concatenated, length-bounded context built from the chunks
 */
public record RagContext(
        String query,
        List<RetrievedChunk> chunks,
        String assembledContext
) {
    /** Maximum characters of assembled context returned by a single RAG query. */
    public static final int MAX_CONTEXT_CHARS = 4000;

    private static final String SEPARATOR = "\n\n---\n\n";

    public RagContext {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query required");
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        if (assembledContext == null) assembledContext = "";
    }

    /**
     * Assembles a bounded context from retrieved chunks. Chunk contents are joined with a
     * separator in relevance order and truncated to {@link #MAX_CONTEXT_CHARS} characters.
     *
     * @param query  the original query text
     * @param chunks the retrieved chunks, nearest-first
     * @return an assembled {@code RagContext}
     */
    public static RagContext assemble(String query, List<RetrievedChunk> chunks) {
        var builder = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            if (builder.length() > 0) builder.append(SEPARATOR);
            builder.append(chunk.content());
            if (builder.length() >= MAX_CONTEXT_CHARS) break;
        }
        var context = builder.length() <= MAX_CONTEXT_CHARS
                ? builder.toString()
                : builder.substring(0, MAX_CONTEXT_CHARS);
        return new RagContext(query, chunks, context);
    }
}
