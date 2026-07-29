package dev.tower.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;

/**
 * Outbound port for Document Templates (OQ-010, ADR-013).
 *
 * <p>Templates are user-owned configuration rather than business facts, so this
 * port offers ordinary save and delete semantics — unlike
 * {@link ObservationRepository}, which offers no update at all because the thing
 * it stores is a historical fact.
 *
 * <p>Deleting a template loses a preference, not a record. Documents already
 * generated with it are unaffected, because a generated document is disposable
 * (IA-03) and was never stored in the first place.
 */
public interface DocumentTemplateRepository {

    DocumentTemplate save(DocumentTemplate template);

    Optional<DocumentTemplate> findById(DocumentTemplateId id);

    /** Backs the uniqueness rule on names, which exists so a picker is unambiguous. */
    Optional<DocumentTemplate> findByName(String name);

    /** Ordered by name, so a picker reads the same way on every machine. */
    List<DocumentTemplate> findAll();

    void deleteById(DocumentTemplateId id);
}
