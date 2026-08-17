package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.ExtractedEntity;
import com.suplab.aether.vault.ports.EntityResolver;

import java.util.ArrayList;

/**
 * A deterministic, dependency-free {@link EntityResolver} — the default entity-resolution strategy.
 *
 * <p>It canonicalises a surface form so common variants of the same entity collapse to one node:</p>
 * <ul>
 *   <li>surrounding and repeated whitespace is collapsed to single spaces;</li>
 *   <li>a trailing possessive ({@code "Suplab's"} / {@code "Suplab’s"}) is stripped;</li>
 *   <li>leading/trailing punctuation is trimmed from each token;</li>
 *   <li>case is normalised — a <em>single-token</em> all-caps acronym ({@code "NASA"}, {@code "AT&T"})
 *       is kept as is; any other mention has each token Title-cased ({@code "ada lovelace"} /
 *       {@code "ADA LOVELACE"} → {@code "Ada Lovelace"}). Preserving acronyms only when the whole
 *       mention is one token keeps genuine acronyms intact while still collapsing shouted multi-word
 *       names — a multi-token phrase like {@code "IBM Corp"} normalises to {@code "Ibm Corp"}.</li>
 * </ul>
 *
 * <p>The {@link com.suplab.aether.vault.domain.EntityType} is preserved. Normalisation is idempotent:
 * resolving an already-canonical mention yields the same mention. If normalisation would produce a
 * blank name (e.g. a mention of only punctuation), the original mention is returned unchanged rather
 * than constructing an invalid entity.</p>
 */
public class NormalizingEntityResolver implements EntityResolver {

    @Override
    public ExtractedEntity resolve(ExtractedEntity mention) {
        var canonical = canonicalName(mention.name());
        if (canonical.isBlank()) {
            return mention;
        }
        return new ExtractedEntity(canonical, mention.type());
    }

    private static String canonicalName(String raw) {
        var stripped = stripPossessive(raw.strip());
        var cleaned = new ArrayList<String>();
        for (String token : stripped.split("\\s+")) {
            var t = trimEdgePunctuation(token);
            if (!t.isEmpty()) cleaned.add(t);
        }
        if (cleaned.isEmpty()) return "";
        // Preserve a genuine acronym only when the whole mention is a single token (NASA, AT&T);
        // a multi-token phrase is always Title-cased so shouted names collapse (ADA LOVELACE → Ada …).
        if (cleaned.size() == 1 && isAcronym(cleaned.get(0))) {
            return cleaned.get(0);
        }
        var out = new StringBuilder();
        for (String token : cleaned) {
            if (out.length() > 0) out.append(' ');
            out.append(titleCase(token));
        }
        return out.toString();
    }

    /** Removes a trailing possessive {@code 's} (straight or curly apostrophe). */
    private static String stripPossessive(String s) {
        if (s.endsWith("'s") || s.endsWith("’s")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static String trimEdgePunctuation(String token) {
        return token.replaceAll("^[^\\p{Alnum}&]+|[^\\p{Alnum}&]+$", "");
    }

    private static String titleCase(String token) {
        var lower = token.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean isAcronym(String token) {
        if (token.length() < 2) return false;
        boolean hasLetter = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) return false;
            }
        }
        return hasLetter;
    }
}
