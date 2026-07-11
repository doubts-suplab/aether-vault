package com.suplab.aether.vault.api.freshness;

import com.suplab.aether.vault.ports.KnowledgeFreshnessPort;
import io.micrometer.core.instrument.MeterRegistry;
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
}
