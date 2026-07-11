package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeScopeTest {

    @Test
    void of_buildsScope() {
        var scope = KnowledgeScope.of("tenant-1", "handbook");

        assertThat(scope.tenantId()).isEqualTo("tenant-1");
        assertThat(scope.collectionId()).isEqualTo("handbook");
    }

    @Test
    void equality_isValueBased() {
        assertThat(KnowledgeScope.of("t", "c")).isEqualTo(KnowledgeScope.of("t", "c"));
        assertThat(KnowledgeScope.of("t", "c")).isNotEqualTo(KnowledgeScope.of("t", "d"));
    }

    @Test
    void rejectsBlankTenantId() {
        assertThatThrownBy(() -> KnowledgeScope.of(" ", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId required");
    }

    @Test
    void rejectsBlankCollectionId() {
        assertThatThrownBy(() -> KnowledgeScope.of("t", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionId required");
    }

    @Test
    void allEntityTypes_areValid() {
        for (var type : EntityType.values()) {
            assertThat(EntityType.valueOf(type.name())).isEqualTo(type);
        }
    }
}
