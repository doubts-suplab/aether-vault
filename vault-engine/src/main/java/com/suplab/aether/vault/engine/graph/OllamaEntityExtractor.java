package com.suplab.aether.vault.engine.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.ExtractedEntity;
import com.suplab.aether.vault.ports.EntityExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A model-based {@link EntityExtractor} that calls an LLM via Ollama's {@code /api/generate} endpoint
 * to name entities in text, with a deterministic fallback.
 *
 * <p>It prompts the model (JSON-mode) for a list of {@code {name, type}} entities, parses the reply,
 * and maps each to an {@link ExtractedEntity}. This is the "smarter NER" upgrade the
 * {@link EntityExtractor} port was designed for — richer than the {@link HeuristicEntityExtractor}, but
 * behind the same interface, so nothing above the extractor changes.</p>
 *
 * <p>It is <strong>fail-safe</strong>: on any transport failure, a null/blank reply, or an
 * unavailable model, it delegates to the injected fallback extractor (the heuristic), so Vault keeps
 * building a graph even when Ollama is absent — mirroring the zero-vector degradation of the embedding
 * service. Output is de-duplicated and bounded by {@code maxEntities}.</p>
 */
public class OllamaEntityExtractor implements EntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(OllamaEntityExtractor.class);

    /** Default cap on distinct entities returned from a single text. */
    public static final int DEFAULT_MAX_ENTITIES = 32;

    private static final String PROMPT = """
            Extract the distinct named entities from the text. Respond with JSON only, of the form
            {"entities":[{"name":"<surface form>","type":"<TYPE>"}]}, where <TYPE> is one of
            PERSON, ORGANISATION, LOCATION, CONCEPT, PRODUCT, EVENT, OTHER. Do not invent entities.

            Text:
            """;

    private final RestClient restClient;
    private final String model;
    private final EntityExtractor fallback;
    private final int maxEntities;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaEntityExtractor(String ollamaBaseUrl, String model, EntityExtractor fallback) {
        this(RestClient.builder().baseUrl(ollamaBaseUrl).build(), model, fallback, DEFAULT_MAX_ENTITIES);
    }

    public OllamaEntityExtractor(RestClient restClient, String model, EntityExtractor fallback,
                                 int maxEntities) {
        if (fallback == null) throw new IllegalArgumentException("fallback extractor required");
        this.restClient = restClient;
        this.model = model;
        this.fallback = fallback;
        this.maxEntities = maxEntities < 1 ? DEFAULT_MAX_ENTITIES : maxEntities;
    }

    @Override
    public List<ExtractedEntity> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        try {
            var response = restClient.post()
                    .uri("/api/generate")
                    .body(Map.of("model", model, "prompt", PROMPT + text, "stream", false, "format", "json"))
                    .retrieve()
                    .body(Map.class);
            var modelText = response == null ? null : asString(response.get("response"));
            if (modelText == null || modelText.isBlank()) {
                log.warn("Empty LLM entity response for model={} — falling back to heuristic", model);
                return fallback.extract(text);
            }
            return parseResponse(modelText);
        } catch (RuntimeException e) {
            log.warn("LLM entity extraction failed for model={} — falling back to heuristic: {}",
                    model, e.getMessage());
            return fallback.extract(text);
        }
    }

    /**
     * Parses an LLM reply (a JSON object with an {@code entities} array, or a bare array) into
     * de-duplicated, bounded entity mentions. Malformed JSON yields an empty list rather than throwing.
     */
    List<ExtractedEntity> parseResponse(String json) {
        List<ExtractedEntity> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode array = root.isArray() ? root : root.get("entities");
            if (array == null || !array.isArray()) return out;
            for (JsonNode node : array) {
                var name = node.path("name").asText("").trim();
                if (name.isBlank()) continue;
                var type = mapType(node.path("type").asText(""));
                if (seen.add(type.name() + '|' + name.toLowerCase())) {
                    out.add(new ExtractedEntity(name, type));
                }
                if (out.size() >= maxEntities) break;
            }
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            log.warn("Unparseable LLM entity JSON — treating as no entities: {}", e.getMessage());
        }
        return out;
    }

    /** Maps a model's type string to an {@link EntityType}, tolerating common synonyms. */
    private static EntityType mapType(String raw) {
        if (raw == null) return EntityType.OTHER;
        return switch (raw.trim().toUpperCase()) {
            case "PERSON", "PEOPLE", "PER" -> EntityType.PERSON;
            case "ORGANISATION", "ORGANIZATION", "ORG", "COMPANY", "INSTITUTION" -> EntityType.ORGANISATION;
            case "LOCATION", "PLACE", "LOC", "GPE" -> EntityType.LOCATION;
            case "CONCEPT", "TOPIC", "IDEA" -> EntityType.CONCEPT;
            case "PRODUCT", "SYSTEM", "ARTEFACT", "ARTIFACT" -> EntityType.PRODUCT;
            case "EVENT" -> EntityType.EVENT;
            default -> EntityType.OTHER;
        };
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
