package com.suplab.aether.vault.engine.source;

import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.DocumentSourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Resolves the {@link DocumentSourceConnector} for a source URI, <em>default-deny</em>.
 *
 * <p>Connectors are tried in registration order; the first that {@link DocumentSourceConnector#supports
 * supports} the URI wins. A URI no connector supports is not fetched — it raises a
 * {@link SourceFetchException} rather than falling back to some permissive default. This keeps the
 * set of reachable source schemes explicit and auditable: adding a scheme means registering a
 * connector, never relaxing a wildcard.</p>
 */
public class SourceConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(SourceConnectorRegistry.class);

    private final List<DocumentSourceConnector> connectors;

    public SourceConnectorRegistry(List<DocumentSourceConnector> connectors) {
        if (connectors == null || connectors.isEmpty())
            throw new IllegalArgumentException("at least one connector required");
        this.connectors = List.copyOf(connectors);
        log.info("Source connector registry initialised with schemes={}",
                this.connectors.stream().map(DocumentSourceConnector::scheme).toList());
    }

    /**
     * Returns the first registered connector that supports {@code sourceUri}.
     *
     * @param sourceUri the URI to resolve
     * @return a connector that can fetch the URI
     * @throws SourceFetchException if the URI is null/blank or no connector supports it
     */
    public DocumentSourceConnector resolve(String sourceUri) {
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new SourceFetchException("sourceUri is required");
        }
        return connectors.stream()
                .filter(c -> c.supports(sourceUri))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No connector supports source uri={} (registered schemes={})",
                            sourceUri, connectors.stream().map(DocumentSourceConnector::scheme).toList());
                    return new SourceFetchException("No connector supports source URI: " + sourceUri);
                });
    }
}
