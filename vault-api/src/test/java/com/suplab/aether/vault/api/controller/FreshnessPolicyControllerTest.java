package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.FreshnessPolicy;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.FreshnessPolicyStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessPolicyControllerTest {

    private static final class FakeStore implements FreshnessPolicyStore {
        final Map<String, FreshnessPolicy> byKey = new HashMap<>();
        @Override public void save(FreshnessPolicy p) { byKey.put(p.tenantId() + '/' + p.collectionId(), p); }
        @Override public Optional<FreshnessPolicy> find(KnowledgeScope s) {
            return Optional.ofNullable(byKey.get(s.tenantId() + '/' + s.collectionId()));
        }
        @Override public List<KnowledgeScope> findAutoReingestScopes(int limit) { return List.of(); }
    }

    @Test
    void get_returnsDefaultWhenNoneStored() {
        var res = new FreshnessPolicyController(new FakeStore()).get("acme", "handbook");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("explicit", false)
                .containsEntry("reindexIntervalDays", FreshnessPolicy.DEFAULT_REINDEX_INTERVAL_DAYS)
                .containsEntry("autoReingest", false);
    }

    @Test
    void put_savesPolicyAndGetReturnsIt() {
        var store = new FakeStore();
        var controller = new FreshnessPolicyController(store);

        var put = controller.put("acme", "handbook", Map.of("reindexIntervalDays", 7, "autoReingest", true));
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put.getBody()).containsEntry("reindexIntervalDays", 7).containsEntry("autoReingest", true);

        var get = controller.get("acme", "handbook");
        assertThat(get.getBody()).containsEntry("explicit", true).containsEntry("reindexIntervalDays", 7);
    }

    @Test
    void put_rejectsIntervalBelowOne() {
        var res = new FreshnessPolicyController(new FakeStore())
                .put("acme", "handbook", Map.of("reindexIntervalDays", 0));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
