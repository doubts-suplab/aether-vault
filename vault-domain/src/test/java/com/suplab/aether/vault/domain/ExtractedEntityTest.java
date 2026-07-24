package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractedEntityTest {

    @Test
    void ofTrimsNameAndKeepsType() {
        var e = ExtractedEntity.of("  Ada Lovelace  ", EntityType.PERSON);
        assertThat(e.name()).isEqualTo("Ada Lovelace");
        assertThat(e.type()).isEqualTo(EntityType.PERSON);
    }

    @Test
    void nullTypeDefaultsToOther() {
        assertThat(ExtractedEntity.of("Something", null).type()).isEqualTo(EntityType.OTHER);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> ExtractedEntity.of("  ", EntityType.CONCEPT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }
}
