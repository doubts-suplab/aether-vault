package com.suplab.aether.vault.api.freshness;

import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import com.suplab.aether.vault.ports.StaleReingestionPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables and wires the scheduled knowledge-freshness sweep.
 *
 * <p>Active by default; set {@code aether.vault.freshness.enabled=false} to opt out (for example
 * in environments where a separate batch job owns re-indexing). {@code @EnableScheduling} is
 * scoped to this config so the scheduler only activates when freshness is enabled.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aether.vault.freshness.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeFreshnessConfig {

    @Bean
    public KnowledgeFreshnessScheduler knowledgeFreshnessScheduler(KnowledgeFreshnessPort freshnessPort,
                                                                   MeterRegistry meterRegistry) {
        return new KnowledgeFreshnessScheduler(freshnessPort, meterRegistry);
    }

    /**
     * Creates the auto-reingestion scheduler. The {@link StaleReingestionPort} is injected via an
     * {@link ObjectProvider} so this stays a no-op when no source connector is enabled.
     *
     * @param maxScopes   maximum auto-reingest collections processed per sweep (default 100)
     * @param maxPerScope maximum stale documents re-ingested per collection per sweep (default 50)
     */
    @Bean
    public StaleReingestionScheduler staleReingestionScheduler(
            ObjectProvider<StaleReingestionPort> reingestionPort,
            MeterRegistry meterRegistry,
            @Value("${aether.vault.freshness.reingest-max-scopes:100}") int maxScopes,
            @Value("${aether.vault.freshness.reingest-max-per-scope:50}") int maxPerScope) {
        return new StaleReingestionScheduler(reingestionPort, meterRegistry, maxScopes, maxPerScope);
    }
}
