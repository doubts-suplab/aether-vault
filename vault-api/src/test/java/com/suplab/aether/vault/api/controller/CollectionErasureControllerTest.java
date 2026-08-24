package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.KnowledgeErasureResult;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.ports.KnowledgeErasurePort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionErasureControllerTest {

    private static final class FakeErasure implements KnowledgeErasurePort {
        KnowledgeScope lastScope;
        @Override public KnowledgeErasureResult eraseCollection(KnowledgeScope scope) {
            lastScope = scope;
            return new KnowledgeErasureResult(scope.tenantId(), scope.collectionId(), 3, 12, 7);
        }
    }

    @Test
    void erase_returns200WithCounts() {
        var erasure = new FakeErasure();
        var res = new CollectionErasureController(erasure).erase("acme", "handbook");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("documentsErased", 3)
                .containsEntry("chunksErased", 12).containsEntry("entitiesErased", 7);
        assertThat(erasure.lastScope).isEqualTo(new KnowledgeScope("acme", "handbook"));
    }
}
