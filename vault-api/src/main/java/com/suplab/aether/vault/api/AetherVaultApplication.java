package com.suplab.aether.vault.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aether Vault — organisational knowledge platform.
 *
 * <p>Runs on port 8084 (Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083, Vault=8084).
 * Provides document indexing, vector search, a Retrieval-Augmented Generation pipeline, a
 * knowledge graph, and scheduled knowledge-freshness sweeps — all scoped by tenant and
 * knowledge collection.</p>
 *
 * <p>{@code scanBasePackages} covers all sub-packages of {@code com.suplab.aether.vault} so beans
 * from {@code vault-engine} (embedding service, chunk store, document store, graph store,
 * ingestion, RAG, and freshness services) are discovered via the config class in
 * {@code vault-api}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.suplab.aether.vault")
public class AetherVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherVaultApplication.class, args);
    }
}
