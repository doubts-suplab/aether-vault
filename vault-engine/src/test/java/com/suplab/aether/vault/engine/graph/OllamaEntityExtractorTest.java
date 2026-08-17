package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.ExtractedEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaEntityExtractorTest {

    private final OllamaEntityExtractor extractor =
            new OllamaEntityExtractor("http://localhost:11434", "llama3", new HeuristicEntityExtractor());

    @Test
    void parsesEntitiesObject_andMapsTypeSynonyms() {
        var json = """
                {"entities":[
                  {"name":"Ada Lovelace","type":"PERSON"},
                  {"name":"Suplab","type":"company"},
                  {"name":"Lisbon","type":"place"},
                  {"name":"Aether Grid","type":"system"}
                ]}""";

        var result = extractor.parseResponse(json);

        assertThat(result).extracting(ExtractedEntity::name)
                .containsExactly("Ada Lovelace", "Suplab", "Lisbon", "Aether Grid");
        assertThat(result).extracting(ExtractedEntity::type)
                .containsExactly(EntityType.PERSON, EntityType.ORGANISATION, EntityType.LOCATION,
                        EntityType.PRODUCT);
    }

    @Test
    void parsesBareArray_andUnknownTypeBecomesOther() {
        var result = extractor.parseResponse("[{\"name\":\"Widget\",\"type\":\"gadget\"}]");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(EntityType.OTHER);
    }

    @Test
    void deduplicatesByNameAndType() {
        var json = "{\"entities\":[{\"name\":\"Ada\",\"type\":\"PERSON\"},"
                + "{\"name\":\"Ada\",\"type\":\"PERSON\"}]}";
        assertThat(extractor.parseResponse(json)).hasSize(1);
    }

    @Test
    void malformedJson_yieldsEmptyNotThrown() {
        assertThat(extractor.parseResponse("{not json")).isEmpty();
        assertThat(extractor.parseResponse("{\"entities\": \"oops\"}")).isEmpty();
    }

    @Test
    void unreachableOllama_fallsBackToHeuristic() {
        // Port 1 is unroutable → the LLM call fails → the heuristic extractor is used instead.
        var failing = new OllamaEntityExtractor("http://127.0.0.1:1", "llama3",
                new HeuristicEntityExtractor());

        var result = failing.extract("Ada Lovelace wrote about the Analytical Engine.");

        assertThat(result).extracting(ExtractedEntity::name).contains("Ada Lovelace");
    }

    @Test
    void constructor_requiresFallback() {
        assertThatThrownBy(() -> new OllamaEntityExtractor("http://localhost", "m", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
