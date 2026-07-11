package com.suplab.aether.vault.api.freshness;

import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the knowledge-freshness sweep on a schedule and records metrics.
 *
 * <p>Delegates the work to {@link KnowledgeFreshnessPort} (set-based SQL) and publishes:</p>
 * <ul>
 *   <li>{@code aether.vault.freshness.stale} — counter, documents marked stale (accumulates)</li>
 *   <li>{@code aether.vault.documents.indexed} — gauge, indexed documents after the last run</li>
 * </ul>
 */
public class KnowledgeFreshnessScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFreshnessScheduler.class);

    private final KnowledgeFreshnessPort freshnessPort;
    private final Counter staleCounter;
    private final AtomicLong indexedGauge = new AtomicLong(0);

    public KnowledgeFreshnessScheduler(KnowledgeFreshnessPort freshnessPort, MeterRegistry meterRegistry) {
        this.freshnessPort = freshnessPort;
        this.staleCounter = Counter.builder("aether.vault.freshness.stale")
                .description("Total documents marked stale across freshness sweeps")
                .register(meterRegistry);
        meterRegistry.gauge("aether.vault.documents.indexed", indexedGauge);
    }

    /**
     * Executes one freshness sweep and updates metrics. Cron is configurable via
     * {@code aether.vault.freshness.cron} (default 04:00 daily).
     */
    @Scheduled(cron = "${aether.vault.freshness.cron:0 0 4 * * *}")
    public void runScheduledSweep() {
        var result = freshnessPort.sweep();
        staleCounter.increment(result.markedStaleCount());
        indexedGauge.set(result.totalIndexed());
        log.info("Scheduled knowledge freshness sweep: scanned={} markedStale={} totalIndexed={}",
                result.scannedCount(), result.markedStaleCount(), result.totalIndexed());
    }
}
