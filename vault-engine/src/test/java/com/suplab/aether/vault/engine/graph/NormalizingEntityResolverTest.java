package com.suplab.aether.vault.engine.graph;

import com.suplab.aether.vault.domain.EntityType;
import com.suplab.aether.vault.domain.ExtractedEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizingEntityResolverTest {

    private final NormalizingEntityResolver resolver = new NormalizingEntityResolver();

    private String canonical(String name, EntityType type) {
        return resolver.resolve(new ExtractedEntity(name, type)).name();
    }

    @Test
    void collapsesCaseVariantsToTitleCase() {
        assertThat(canonical("ada lovelace", EntityType.PERSON)).isEqualTo("Ada Lovelace");
        assertThat(canonical("ADA LOVELACE", EntityType.PERSON)).isEqualTo("Ada Lovelace");
        assertThat(canonical("Ada Lovelace", EntityType.PERSON)).isEqualTo("Ada Lovelace");
    }

    @Test
    void stripsPossessiveAndEdgePunctuation() {
        assertThat(canonical("Suplab's", EntityType.ORGANISATION)).isEqualTo("Suplab");
        assertThat(canonical("Suplab’s", EntityType.ORGANISATION)).isEqualTo("Suplab");
        assertThat(canonical("(Lisbon)", EntityType.LOCATION)).isEqualTo("Lisbon");
    }

    @Test
    void collapsesWhitespace() {
        assertThat(canonical("  Ada   Lovelace  ", EntityType.PERSON)).isEqualTo("Ada Lovelace");
    }

    @Test
    void preservesAcronyms() {
        assertThat(canonical("NASA", EntityType.ORGANISATION)).isEqualTo("NASA");
        assertThat(canonical("AT&T", EntityType.ORGANISATION)).isEqualTo("AT&T");
    }

    @Test
    void isIdempotent() {
        var once = resolver.resolve(new ExtractedEntity("ada LOVELACE", EntityType.PERSON));
        var twice = resolver.resolve(once);
        assertThat(twice.name()).isEqualTo(once.name());
    }

    @Test
    void preservesTypeAndFallsBackWhenCanonicalWouldBeBlank() {
        var punctuation = new ExtractedEntity("...", EntityType.CONCEPT);
        var resolved = resolver.resolve(punctuation);
        assertThat(resolved.type()).isEqualTo(EntityType.CONCEPT);
        assertThat(resolved.name()).isEqualTo("..."); // unchanged rather than an invalid blank
    }
}
