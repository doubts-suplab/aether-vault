package com.suplab.aether.vault.api.freshness;

import com.suplab.aether.vault.ports.StaleReingestionPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class StaleReingestionSchedulerTest {

    /** Minimal ObjectProvider that yields a fixed value (or none). */
    private static final class FixedProvider<T> implements ObjectProvider<T> {
        private final T value;

        FixedProvider(T value) {
            this.value = value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getIfAvailable(Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }
    }

    /** Scripted port returning a single reingestion result. */
    private static final class ScriptedReingestionPort implements StaleReingestionPort {
        private final ReingestionResult result;

        ScriptedReingestionPort(ReingestionResult result) {
            this.result = result;
        }

        @Override
        public ReingestionResult reingestStale(int maxScopes, int maxPerScope) {
            return result;
        }
    }

    @Test
    void sweep_recordsReingestedAndFailedCounters() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedReingestionPort(new StaleReingestionPort.ReingestionResult(3, 5, 1));
        var scheduler = new StaleReingestionScheduler(new FixedProvider<>(port), registry, 100, 50);

        scheduler.runScheduledReingest();

        assertThat(registry.get("aether.vault.reingest.documents").counter().count()).isEqualTo(5.0);
        assertThat(registry.get("aether.vault.reingest.failures").counter().count()).isEqualTo(1.0);
    }

    @Test
    void sweep_isNoOpWhenNoConnectorEnabled() {
        var registry = new SimpleMeterRegistry();
        var scheduler = new StaleReingestionScheduler(new FixedProvider<>(null), registry, 100, 50);

        scheduler.runScheduledReingest();

        assertThat(registry.get("aether.vault.reingest.documents").counter().count()).isZero();
        assertThat(registry.get("aether.vault.reingest.failures").counter().count()).isZero();
    }
}
