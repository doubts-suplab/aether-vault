package com.suplab.aether.vault.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreshnessPolicyTest {

    private static final KnowledgeScope SCOPE = new KnowledgeScope("acme", "handbook");

    @Test
    void of_buildsPolicyWithGivenIntervalAndFlag() {
        var policy = FreshnessPolicy.of(SCOPE, 7, true);

        assertThat(policy.tenantId()).isEqualTo("acme");
        assertThat(policy.collectionId()).isEqualTo("handbook");
        assertThat(policy.reindexIntervalDays()).isEqualTo(7);
        assertThat(policy.autoReingest()).isTrue();
        assertThat(policy.updatedAt()).isNotNull();
        assertThat(policy.scope()).isEqualTo(SCOPE);
    }

    @Test
    void defaultFor_usesGlobalIntervalAndNoAutoReingest() {
        var policy = FreshnessPolicy.defaultFor(SCOPE);

        assertThat(policy.reindexIntervalDays()).isEqualTo(FreshnessPolicy.DEFAULT_REINDEX_INTERVAL_DAYS);
        assertThat(policy.autoReingest()).isFalse();
    }

    @Test
    void rejectsIntervalBelowOne() {
        assertThatThrownBy(() -> FreshnessPolicy.of(SCOPE, 0, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reindexIntervalDays");
    }

    @Test
    void rejectsBlankScope() {
        assertThatThrownBy(() -> new FreshnessPolicy("", "c", 5, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreshnessPolicy("t", " ", 5, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
