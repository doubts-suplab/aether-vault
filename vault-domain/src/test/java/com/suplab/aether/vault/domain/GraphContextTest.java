package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.suplab.aether.vault.domain.RelevantEntity.Relevance.MATCHED;
import static com.suplab.aether.vault.domain.RelevantEntity.Relevance.RELATED;
import static org.assertj.core.api.Assertions.assertThat;

class GraphContextTest {

    private static RelevantEntity e(String name, EntityType type, int mentions,
                                    RelevantEntity.Relevance rel) {
        return new RelevantEntity(name, type, mentions, rel);
    }

    @Test
    void empty_hasNoEntitiesAndBlankSummary() {
        var graph = GraphContext.empty();
        assertThat(graph.entities()).isEmpty();
        assertThat(graph.summary()).isEmpty();
    }

    @Test
    void assemble_ordersMatchedFirstThenBySalience() {
        var matched = List.of(
                e("Ada Lovelace", EntityType.PERSON, 3, MATCHED),
                e("Suplab", EntityType.ORGANISATION, 9, MATCHED));
        var related = List.of(e("Hexagonal Architecture", EntityType.CONCEPT, 12, RELATED));

        var graph = GraphContext.assemble(matched, related);

        // Matched entities precede related ones; within matched, higher mentionCount first.
        assertThat(graph.entities()).extracting(RelevantEntity::name)
                .containsExactly("Suplab", "Ada Lovelace", "Hexagonal Architecture");
        assertThat(graph.entities().get(2).relevance()).isEqualTo(RELATED);
    }

    @Test
    void assemble_dedupesKeepingMatchedOverRelated() {
        var matched = List.of(e("Suplab", EntityType.ORGANISATION, 9, MATCHED));
        // Same entity also shows up as a neighbour — the matched instance must win.
        var related = List.of(e("suplab", EntityType.ORGANISATION, 9, RELATED));

        var graph = GraphContext.assemble(matched, related);

        assertThat(graph.entities()).hasSize(1);
        assertThat(graph.entities().get(0).relevance()).isEqualTo(MATCHED);
    }

    @Test
    void assemble_capsAtMaxEntities() {
        List<RelevantEntity> many = new ArrayList<>();
        for (int i = 0; i < GraphContext.MAX_ENTITIES + 10; i++) {
            many.add(e("Entity" + i, EntityType.CONCEPT, 1, MATCHED));
        }
        var graph = GraphContext.assemble(many, List.of());
        assertThat(graph.entities()).hasSize(GraphContext.MAX_ENTITIES);
    }

    @Test
    void summary_labelsMatchedAndRelatedSections() {
        var graph = GraphContext.assemble(
                List.of(e("Ada Lovelace", EntityType.PERSON, 3, MATCHED)),
                List.of(e("Analytical Engine", EntityType.CONCEPT, 5, RELATED)));

        assertThat(graph.summary())
                .contains("Relevant entities: Ada Lovelace (PERSON)")
                .contains("related: Analytical Engine (CONCEPT)");
    }
}
