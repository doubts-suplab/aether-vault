package com.suplab.aether.vault.api.freshness;

import com.suplab.aether.vault.ports.StaleReingestionPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the auto-reingestion sweep on a schedule and records metrics.
 *
 * <p>Refreshes {@code STALE} documents in collections that opted into {@code autoReingest} by
 * re-fetching them through their source connector. The {@link StaleReingestionPort} is injected via an
 * {@link ObjectProvider} because it only exists when a source connector is enabled — with none, this
 * scheduler is a no-op, so Vault runs unchanged without connectors. Publishes:</p>
 * <ul>
 *   <li>{@code aether.vault.reingest.documents} — counter, stale documents re-ingested (accumulates)</li>
 *   <li>{@code aether.vault.reingest.failures} — counter, stale documents whose re-fetch failed</li>
 * </ul>
 */
public class StaleReingestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleReingestionScheduler.class);

    private final ObjectProvider<StaleReingestionPort> reingestionPort;
    private final Counter reingestedCounter;
    private final Counter failedCounter;
    private final int maxScopes;
    private final int maxPerScope;

    public StaleReingestionScheduler(ObjectProvider<StaleReingestionPort> reingestionPort,
                                     MeterRegistry meterRegistry, int maxScopes, int maxPerScope) {
        this.reingestionPort = reingestionPort;
        this.maxScopes = maxScopes;
        this.maxPerScope = maxPerScope;
        this.reingestedCounter = Counter.builder("aether.vault.reingest.documents")
                .description("Total stale documents re-ingested across auto-reingestion sweeps")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aether.vault.reingest.failures")
                .description("Total stale documents whose auto-reingest re-fetch failed")
                .register(meterRegistry);
    }

    /**
     * Executes one auto-reingestion sweep if a source connector is enabled. Cron is configurable via
     * {@code aether.vault.freshness.reingest-cron} (default 04:30 daily, just after the freshness sweep).
     */
    @Scheduled(cron = "${aether.vault.freshness.reingest-cron:0 30 4 * * *}")
    public void runScheduledReingest() {
        var port = reingestionPort.getIfAvailable();
        if (port == null) {
            log.debug("Auto-reingestion skipped — no source connector enabled");
            return;
        }
        var result = port.reingestStale(maxScopes, maxPerScope);
        reingestedCounter.increment(result.documentsReingested());
        failedCounter.increment(result.documentsFailed());
        log.info("Scheduled auto-reingestion sweep: scopes={} reingested={} failed={}",
                result.scopesScanned(), result.documentsReingested(), result.documentsFailed());
    }
}
