package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.ExtractedEntity;
import com.suplab.aether.vault.ports.EntityExtractor;
import com.suplab.aether.vault.ports.EntityResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvingEntityExtractorTest {

    private static EntityExtractor fixed(List<ExtractedEntity> mentions) {
        return text -> mentions;
    }

    @Test
    void resolvesAndDeduplicatesSurfaceFormVariants() {
        var delegate = fixed(List.of(
                new ExtractedEntity("Ada Lovelace", EntityType.PERSON),
                new ExtractedEntity("ada lovelace", EntityType.PERSON),   // case variant → same node
                new ExtractedEntity("Suplab's", EntityType.ORGANISATION)));  // possessive → "Suplab"
        var extractor = new ResolvingEntityExtractor(delegate, new NormalizingEntityResolver());

        var result = extractor.extract("irrelevant");

        assertThat(result).extracting(ExtractedEntity::name)
                .containsExactly("Ada Lovelace", "Suplab");
    }

    @Test
    void distinctTypesWithSameNameAreKept() {
        var delegate = fixed(List.of(
                new ExtractedEntity("Aether", EntityType.PRODUCT),
                new ExtractedEntity("Aether", EntityType.CONCEPT)));
        var extractor = new ResolvingEntityExtractor(delegate, new NormalizingEntityResolver());

        assertThat(extractor.extract("x")).hasSize(2);
    }

    @Test
    void noneResolver_isTransparentPassThroughExceptDedup() {
        var delegate = fixed(List.of(
                new ExtractedEntity("Ada Lovelace", EntityType.PERSON),
                new ExtractedEntity("Ada Lovelace", EntityType.PERSON)));
        var extractor = new ResolvingEntityExtractor(delegate, EntityResolver.NONE);

        // Identity resolution still de-dupes exact repeats.
        assertThat(extractor.extract("x")).hasSize(1);
    }
}
