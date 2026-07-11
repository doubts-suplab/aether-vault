package com.suplab.aether.vault.domain;

/**
 * Taxonomy of named entities extracted from indexed documents to populate the knowledge graph.
 *
 * <p>Entities are the <em>nodes</em> of the graph; {@link EntityRelation}s are the edges. The
 * taxonomy is deliberately small and domain-agnostic — richer, domain-specific typing is a
 * later-phase concern.</p>
 *
 * <ul>
 *   <li>PERSON       — an individual ("Ada Lovelace")</li>
 *   <li>ORGANISATION — a company, team, or institution ("Suplab")</li>
 *   <li>LOCATION     — a place ("Lisbon")</li>
 *   <li>CONCEPT      — an abstract idea or topic ("hexagonal architecture")</li>
 *   <li>PRODUCT      — a product, system, or artefact ("Aether Grid")</li>
 *   <li>EVENT        — a time-anchored occurrence ("the Q3 incident")</li>
 *   <li>OTHER        — anything not covered above</li>
 * </ul>
 */
public enum EntityType {
    PERSON,
    ORGANISATION,
    LOCATION,
    CONCEPT,
    PRODUCT,
    EVENT,
    OTHER
}
