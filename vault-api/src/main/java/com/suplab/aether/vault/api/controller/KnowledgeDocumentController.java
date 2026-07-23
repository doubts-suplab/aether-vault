package com.suplab.aether.vault.api.controller;

import com.suplab.aether.vault.domain.ContentChecksum;
import com.suplab.aether.vault.domain.KnowledgeDocument;
import com.suplab.aether.vault.domain.KnowledgeScope;
import com.suplab.aether.vault.domain.SourceFetchException;
import com.suplab.aether.vault.ports.DocumentChunkStore;
import com.suplab.aether.vault.ports.DocumentIngestionPort;
import com.suplab.aether.vault.ports.KnowledgeDocumentStore;
import com.suplab.aether.vault.ports.SourceIngestionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Document indexing and management for a knowledge collection.
 *
 * <p>Every path is scoped by {@code tenantId} + {@code collectionId} — the multi-tenancy boundary
 * of Aether Vault. Ingestion computes a content checksum (for freshness), registers the document,
 * then chunks and embeds it via the {@link DocumentIngestionPort}. Re-posting the same
 * {@code documentId} re-indexes it in place, replacing its chunks.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/collections/{collectionId}/documents")
public class KnowledgeDocumentController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentController.class);

    private final DocumentIngestionPort ingestionPort;
    private final KnowledgeDocumentStore documentStore;
    private final DocumentChunkStore chunkStore;
    private final Optional<SourceIngestionPort> sourceIngestionPort;

    public KnowledgeDocumentController(DocumentIngestionPort ingestionPort,
                                       KnowledgeDocumentStore documentStore,
                                       DocumentChunkStore chunkStore,
                                       Optional<SourceIngestionPort> sourceIngestionPort) {
        this.ingestionPort = ingestionPort;
        this.documentStore = documentStore;
        this.chunkStore = chunkStore;
        this.sourceIngestionPort = sourceIngestionPort;
    }

    /**
     * Ingests a document: registers it and indexes its text into embedded chunks.
     *
     * <p>Request body: {@code {"sourceUri": "...", "title": "...", "contentType": "text/markdown",
     * "text": "..."}}. {@code title} defaults to {@code sourceUri}; {@code contentType} defaults to
     * {@code text/plain}.</p>
     *
     * @return 201 Created with {@code documentId}, {@code chunkCount}, {@code status}; 400 on bad input
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @RequestBody Map<String, String> body) {

        var text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        var sourceUri = body.get("sourceUri");
        if (sourceUri == null || sourceUri.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceUri is required"));
        }
        var title = body.getOrDefault("title", sourceUri);
        var contentType = body.getOrDefault("contentType", "text/plain");

        var scope = new KnowledgeScope(tenantId, collectionId);
        var document = KnowledgeDocument.create(scope, sourceUri, title, contentType, ContentChecksum.sha256(text));
        documentStore.save(document);
        var result = ingestionPort.ingest(document, text);

        log.info("Ingested documentId={} tenantId={} collectionId={} status={} chunks={}",
                result.documentId(), tenantId, collectionId, result.status(), result.chunkCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", result.documentId().toString(),
                "chunkCount", result.chunkCount(),
                "status", result.status().name()));
    }

    /**
     * Ingests a document from a source URI via a registered connector, rather than from inline text.
     *
     * <p>Request body: {@code {"sourceUri": "file:notes/handbook.md"}} or
     * {@code {"sourceUri": "https://example.com/policy.txt"}}. The connector registry is
     * default-deny — a URI no enabled connector supports is rejected. Re-posting the same
     * {@code sourceUri} is freshness-aware: an unchanged source is not re-embedded, a changed one is
     * re-indexed in place under the same document ID.</p>
     *
     * @return 201 with {@code documentId}, {@code chunkCount}, {@code status}, {@code outcome}
     *         (INDEXED / UNCHANGED / FAILED); 400 on a bad or unsupported source; 503 when no
     *         source connector is enabled
     */
    @PostMapping("/from-source")
    public ResponseEntity<Map<String, Object>> ingestFromSource(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @RequestBody Map<String, String> body) {

        if (sourceIngestionPort.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "no source connector is enabled"));
        }
        var sourceUri = body.get("sourceUri");
        if (sourceUri == null || sourceUri.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceUri is required"));
        }

        var scope = new KnowledgeScope(tenantId, collectionId);
        try {
            var result = sourceIngestionPort.get().ingestFromSource(scope, sourceUri);
            log.info("Ingested from source tenantId={} collectionId={} sourceUri={} documentId={} outcome={}",
                    tenantId, collectionId, sourceUri, result.documentId(), result.outcome());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "documentId", result.documentId().toString(),
                    "chunkCount", result.chunkCount(),
                    "status", result.status().name(),
                    "outcome", result.outcome().name()));
        } catch (SourceFetchException e) {
            log.warn("Source fetch failed tenantId={} collectionId={} sourceUri={} reason={}",
                    tenantId, collectionId, sourceUri, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lists documents in the collection, most recently updated first.
     *
     * @param limit maximum number of documents (default 20)
     * @return 200 OK with the list of document views
     */
    @GetMapping
    public ResponseEntity<Object> list(
            @PathVariable String tenantId,
            @PathVariable String collectionId,
            @RequestParam(defaultValue = "20") int limit) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        var body = documentStore.findByCollection(scope, limit).stream()
                .map(KnowledgeDocumentController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Returns the document and chunk counts for the collection.
     *
     * @return 200 OK with {@code tenantId}, {@code collectionId}, {@code documentCount}, {@code chunkCount}
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(@PathVariable String tenantId,
                                                     @PathVariable String collectionId) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "collectionId", collectionId,
                "documentCount", documentStore.countByCollection(scope),
                "chunkCount", chunkStore.countByCollection(scope)));
    }

    /**
     * Returns a single document by ID.
     *
     * @return 200 OK with the document view; 404 if not found in this collection
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<Object> get(@PathVariable String tenantId,
                                      @PathVariable String collectionId,
                                      @PathVariable UUID documentId) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        return documentStore.findById(scope, documentId)
                .<ResponseEntity<Object>>map(doc -> ResponseEntity.ok(toView(doc)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "document not found")));
    }

    /**
     * Deletes a document and its chunks. The scoping prevents cross-collection deletion.
     *
     * @return 204 No Content
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String tenantId,
                                       @PathVariable String collectionId,
                                       @PathVariable UUID documentId) {
        var scope = new KnowledgeScope(tenantId, collectionId);
        chunkStore.deleteByDocument(scope, documentId);
        documentStore.delete(scope, documentId);
        log.info("Deleted documentId={} tenantId={} collectionId={}", documentId, tenantId, collectionId);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> toView(KnowledgeDocument document) {
        var view = new java.util.HashMap<String, Object>();
        view.put("documentId", document.id().toString());
        view.put("sourceUri", document.sourceUri());
        view.put("title", document.title());
        view.put("contentType", document.contentType());
        view.put("status", document.status().name());
        view.put("chunkCount", document.chunkCount());
        view.put("createdAt", document.createdAt().toString());
        view.put("indexedAt", document.indexedAt() != null ? document.indexedAt().toString() : null);
        view.put("updatedAt", document.updatedAt().toString());
        return view;
    }
}
