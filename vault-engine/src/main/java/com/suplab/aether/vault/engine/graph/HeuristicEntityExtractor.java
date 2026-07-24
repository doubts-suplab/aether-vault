package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.ExtractedEntity;
import com.suplab.aether.vault.ports.EntityExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A dependency-free, deterministic {@link EntityExtractor} — the default NER for the Phase 2 graph
 * pipeline.
 *
 * <p>It finds candidate entities as maximal runs of capitalised words (stripping sentence-initial
 * stopwords so "The team…" does not yield "The"), then assigns a coarse {@link EntityType}:</p>
 * <ul>
 *   <li><b>ORGANISATION</b> — a run ending in a company/institution suffix ({@code Inc}, {@code Ltd},
 *       {@code Corp}, {@code University}, …), or a single all-caps acronym ({@code NASA}).</li>
 *   <li><b>PERSON</b> — a classic two-token "First Last" capitalised name.</li>
 *   <li><b>CONCEPT</b> — a single capitalised token, or a longer Title-Case phrase.</li>
 * </ul>
 *
 * <p>Classification is intentionally coarse: this extractor exists so Vault can build a graph with no
 * NLP runtime or Ollama on the classpath (mirroring {@code HeuristicTokenCounter}). A model- or
 * LLM-based extractor implementing the same {@link EntityExtractor} port is a later-phase upgrade and
 * needs no change to the pipeline. Output is deduplicated per call and bounded by
 * {@code maxEntitiesPerText}.</p>
 */
public class HeuristicEntityExtractor implements EntityExtractor {

    /** Default cap on distinct entities returned from a single text. */
    public static final int DEFAULT_MAX_ENTITIES = 32;

    private static final int MIN_SURFACE_LENGTH = 2;
    private static final int MAX_RUN_TOKENS = 4;

    private static final Set<String> STOPWORDS = Set.of(
            "The", "A", "An", "This", "That", "These", "Those", "It", "We", "They", "He", "She",
            "I", "In", "On", "At", "For", "And", "But", "Or", "Nor", "If", "When", "While", "As",
            "To", "Of", "From", "With", "By", "Its", "Their", "Our", "His", "Her", "There", "Then",
            "So", "Yet", "Because", "However", "Meanwhile", "Also", "Both", "Each", "Every");

    private static final Set<String> ORG_SUFFIXES = Set.of(
            "Inc", "Ltd", "Llc", "Corp", "Co", "Company", "Gmbh", "Plc", "Foundation", "University",
            "Institute", "Group", "Labs", "Lab", "Systems", "Technologies", "Holdings", "Association");

    private final int maxEntitiesPerText;

    public HeuristicEntityExtractor() {
        this(DEFAULT_MAX_ENTITIES);
    }

    public HeuristicEntityExtractor(int maxEntitiesPerText) {
        this.maxEntitiesPerText = maxEntitiesPerText < 1 ? DEFAULT_MAX_ENTITIES : maxEntitiesPerText;
    }

    @Override
    public List<ExtractedEntity> extract(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Preserve insertion order and dedupe by surface form (case-sensitive).
        Map<String, ExtractedEntity> found = new LinkedHashMap<>();

        for (String sentence : text.split("[.!?\\n\\r]+")) {
            for (String surface : capitalisedRuns(sentence)) {
                if (surface.length() < MIN_SURFACE_LENGTH) continue;
                found.putIfAbsent(surface, new ExtractedEntity(surface, classify(surface)));
                if (found.size() >= maxEntitiesPerText) return new ArrayList<>(found.values());
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Maximal runs of capitalised tokens in a sentence, with leading stopwords stripped. */
    private static List<String> capitalisedRuns(String sentence) {
        List<String> runs = new ArrayList<>();
        String[] tokens = sentence.trim().split("\\s+");
        List<String> current = new ArrayList<>();
        for (String raw : tokens) {
            String token = raw.replaceAll("^[^A-Za-z0-9&]+|[^A-Za-z0-9&]+$", ""); // trim edge punctuation
            if (isCapitalised(token)) {
                current.add(token);
                if (current.size() == MAX_RUN_TOKENS) {
                    emit(runs, current);
                    current = new ArrayList<>();
                }
            } else if (!current.isEmpty()) {
                emit(runs, current);
                current = new ArrayList<>();
            }
        }
        emit(runs, current);
        return runs;
    }

    private static void emit(List<String> runs, List<String> tokens) {
        if (tokens.isEmpty()) return;
        int start = 0;
        while (start < tokens.size() && STOPWORDS.contains(tokens.get(start))) start++;
        if (start >= tokens.size()) return; // run was all stopwords
        runs.add(String.join(" ", tokens.subList(start, tokens.size())));
    }

    private static boolean isCapitalised(String token) {
        if (token.length() < 1) return false;
        return Character.isUpperCase(token.charAt(0)) && token.chars().allMatch(Character::isLetterOrDigit)
                || (token.contains("&") && Character.isUpperCase(token.charAt(0))); // e.g. AT&T
    }

    private static EntityType classify(String surface) {
        String[] tokens = surface.split("[\\s&]+");
        String last = tokens[tokens.length - 1].replace(".", "");
        if (ORG_SUFFIXES.contains(capitalise(last))) return EntityType.ORGANISATION;
        if (tokens.length == 1 && isAcronym(tokens[0])) return EntityType.ORGANISATION;
        if (tokens.length == 2 && isNameToken(tokens[0]) && isNameToken(tokens[1])) return EntityType.PERSON;
        return EntityType.CONCEPT;
    }

    private static boolean isAcronym(String token) {
        return token.length() >= 2 && token.chars().allMatch(Character::isUpperCase);
    }

    /** A "First"/"Last" style token: initial uppercase, remainder lowercase letters. */
    private static boolean isNameToken(String token) {
        if (token.length() < 2 || !Character.isUpperCase(token.charAt(0))) return false;
        return token.substring(1).chars().allMatch(Character::isLowerCase);
    }

    private static String capitalise(String token) {
        if (token.isEmpty()) return token;
        return Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase();
    }
}
