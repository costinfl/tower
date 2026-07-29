package dev.tower.application.port.in;

import java.util.List;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;

/**
 * Inbound port for Document Templates (OQ-010, ADR-013, FR-062 to FR-065).
 *
 * <p>Templates are configuration, so these use cases carry only the rules that
 * keep a template usable: it must name itself distinguishably, and it must
 * select at least one section and no section twice.
 */
public interface DocumentTemplateUseCases {

    DocumentTemplate create(DefineTemplate command);

    /** Replaces name and sections wholesale; the identity is what persists. */
    DocumentTemplate update(DocumentTemplateId id, DefineTemplate command);

    /**
     * Stored templates, plus the built-in complete document at the head.
     *
     * <p>One list rather than "the default, and then the others", so a picker in
     * the Viewer has nothing to special-case and the complete document is
     * visibly a choice rather than a hidden fallback.
     */
    List<DocumentTemplate> list();

    /**
     * The template to render with.
     *
     * <p>Resolves the built-in id without a database lookup, and fails loudly
     * for an id that names nothing. Falling back to the complete document there
     * would hand someone a document with sections they had deliberately removed,
     * and nothing on the page would say so.
     */
    DocumentTemplate require(DocumentTemplateId id);

    void delete(DocumentTemplateId id);

    /** The sections a template may choose from, so no client hardcodes the list. */
    List<DocumentSection> availableSections();

    record DefineTemplate(String name, List<DocumentSection> sections) {}
}
