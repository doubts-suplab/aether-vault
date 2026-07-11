package com.suplab.aether.vault.api.freshness;

import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeFreshnessSchedulerTest {

    /** Stub port returning a scripted sequence of results, one per sweep. */
    private static final class ScriptedFreshnessPort implements KnowledgeFreshnessPort {
        private final Deque<FreshnessResult> results;

        ScriptedFreshnessPort(FreshnessResult... scripted) {
            this.results = new ArrayDeque<>(List.of(scripted));
        }

        @Override
        public FreshnessResult sweep() {
            return results.poll();
        }
    }

    @Test
    void sweep_recordsCounterAndGauge() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedFreshnessPort(new KnowledgeFreshnessPort.FreshnessResult(50, 4, 46));
        var scheduler = new KnowledgeFreshnessScheduler(port, registry);

        scheduler.runScheduledSweep();

        assertThat(registry.get("aether.vault.freshness.stale").counter().count()).isEqualTo(4.0);
        assertThat(registry.get("aether.vault.documents.indexed").gauge().value()).isEqualTo(46.0);
    }

    @Test
    void sweep_counterAccumulatesButGaugeReflectsLatest() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedFreshnessPort(
                new KnowledgeFreshnessPort.FreshnessResult(50, 4, 46),
                new KnowledgeFreshnessPort.FreshnessResult(46, 2, 44));
        var scheduler = new KnowledgeFreshnessScheduler(port, registry);

        scheduler.runScheduledSweep();
        scheduler.runScheduledSweep();

        assertThat(registry.get("aether.vault.freshness.stale").counter().count()).isEqualTo(6.0);
        assertThat(registry.get("aether.vault.documents.indexed").gauge().value()).isEqualTo(44.0);
    }
}
