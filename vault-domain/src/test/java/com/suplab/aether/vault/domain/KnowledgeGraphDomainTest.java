package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeGraphDomainTest {

    private static final KnowledgeScope SCOPE = KnowledgeScope.of("tenant-1", "handbook");

    @Test
    void entity_createStartsWithSingleMention() {
        var entity = KnowledgeEntity.create(SCOPE, "Aether Grid", EntityType.PRODUCT);

        assertThat(entity.mentionCount()).isEqualTo(1);
        assertThat(entity.name()).isEqualTo("Aether Grid");
        assertThat(entity.type()).isEqualTo(EntityType.PRODUCT);
        assertThat(entity.scope()).isEqualTo(SCOPE);
    }

    @Test
    void entity_mentionIncrementsCount() {
        var entity = KnowledgeEntity.create(SCOPE, "pgvector", EntityType.CONCEPT);

        assertThat(entity.mention().mention().mentionCount()).isEqualTo(3);
    }

    @Test
    void entity_defaultsNullTypeToOther() {
        var entity = new KnowledgeEntity(UUID.randomUUID(), "t", "c", "thing", null, 1, null);

        assertThat(entity.type()).isEqualTo(EntityType.OTHER);
    }

    @Test
    void entity_rejectsMentionCountBelowOne() {
        assertThatThrownBy(() -> new KnowledgeEntity(UUID.randomUUID(), "t", "c", "n", EntityType.OTHER, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentionCount must be >= 1");
    }

    @Test
    void relation_ofHasFullConfidence() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var relation = EntityRelation.of(a, b, "depends_on");

        assertThat(relation.confidence()).isEqualTo(1.0);
        assertThat(relation.sourceEntityId()).isEqualTo(a);
        assertThat(relation.targetEntityId()).isEqualTo(b);
    }

    @Test
    void relation_rejectsSelfLoop() {
        var a = UUID.randomUUID();

        assertThatThrownBy(() -> EntityRelation.of(a, a, "self"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two distinct entities");
    }

    @Test
    void relation_rejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> new EntityRelation(UUID.randomUUID(), UUID.randomUUID(), "r", 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence must be 0-1");
    }

    @Test
    void relation_rejectsBlankType() {
        assertThatThrownBy(() -> new EntityRelation(UUID.randomUUID(), UUID.randomUUID(), " ", 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationType required");
    }
}
