package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.ExtractedEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicEntityExtractorTest {

    private final HeuristicEntityExtractor extractor = new HeuristicEntityExtractor();

    private Map<String, EntityType> byName(String text) {
        return extractor.extract(text).stream()
                .collect(Collectors.toMap(ExtractedEntity::name, ExtractedEntity::type, (a, b) -> a));
    }

    @Test
    void extractsCapitalisedNamesAsPersons() {
        var found = byName("Ada Lovelace worked with Charles Babbage.");
        assertThat(found).containsEntry("Ada Lovelace", EntityType.PERSON);
        assertThat(found).containsEntry("Charles Babbage", EntityType.PERSON);
    }

    @Test
    void classifiesOrgSuffixAndAcronymAsOrganisation() {
        var found = byName("Acme Corp partnered with NASA last year.");
        assertThat(found).containsEntry("Acme Corp", EntityType.ORGANISATION);
        assertThat(found).containsEntry("NASA", EntityType.ORGANISATION);
    }

    @Test
    void stripsSentenceInitialStopword() {
        var found = byName("The Aether Grid is a governance platform.");
        assertThat(found).containsKey("Aether Grid");
        assertThat(found).doesNotContainKey("The Aether Grid");
        assertThat(found).doesNotContainKey("The");
    }

    @Test
    void longerTitleCasePhraseIsConcept() {
        var found = byName("We adopted Model Context Protocol early.");
        assertThat(found).containsEntry("Model Context Protocol", EntityType.CONCEPT);
    }

    @Test
    void dedupesAcrossSentences() {
        List<ExtractedEntity> found = extractor.extract("Acme Corp shipped. Acme Corp grew.");
        assertThat(found).extracting(ExtractedEntity::name).containsExactly("Acme Corp");
    }

    @Test
    void lowercaseOnlyTextYieldsNothing() {
        assertThat(extractor.extract("the quick brown fox jumps")).isEmpty();
        assertThat(extractor.extract("")).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void respectsMaxEntitiesCap() {
        var capped = new HeuristicEntityExtractor(2);
        var found = capped.extract("Alpha Bravo. Charlie Delta. Echo Foxtrot.");
        assertThat(found).hasSize(2);
    }
}
