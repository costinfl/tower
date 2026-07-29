package dev.tower.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.port.in.DocumentTemplateUseCases;
import dev.tower.application.port.out.DocumentTemplateRepository;

/**
 * Document Template use cases (OQ-010, ADR-013).
 *
 * <p>Carries no framework annotation, like every service here; Spring wiring
 * lives in tower-api.
 *
 * <p>The rules are few because the thing is configuration. What the service
 * protects is the built-in complete document — it cannot be stored, renamed,
 * reshaped or deleted — so there is always one template that produces the whole
 * truth about a release, whatever anyone has configured.
 */
public class DocumentTemplateService implements DocumentTemplateUseCases {

    private final DocumentTemplateRepository templates;

    public DocumentTemplateService(DocumentTemplateRepository templates) {
        this.templates = Objects.requireNonNull(templates);
    }

    @Override
    public DocumentTemplate create(DefineTemplate command) {
        requireNameIsFree(command.name(), null);
        return templates.save(new DocumentTemplate(
                DocumentTemplateId.newId(), command.name(), command.sections()));
    }

    @Override
    public DocumentTemplate update(DocumentTemplateId id, DefineTemplate command) {
        requireNotBuiltIn(id, "changed");
        requireStored(id);
        requireNameIsFree(command.name(), id);
        return templates.save(new DocumentTemplate(id, command.name(), command.sections()));
    }

    @Override
    public List<DocumentTemplate> list() {
        List<DocumentTemplate> all = new ArrayList<>();
        all.add(DocumentTemplate.complete());
        all.addAll(templates.findAll());
        return List.copyOf(all);
    }

    @Override
    public DocumentTemplate require(DocumentTemplateId id) {
        if (DocumentTemplate.COMPLETE_ID.equals(id)) {
            return DocumentTemplate.complete();
        }
        return requireStored(id);
    }

    @Override
    public void delete(DocumentTemplateId id) {
        requireNotBuiltIn(id, "deleted");
        templates.deleteById(id);
    }

    @Override
    public List<DocumentSection> availableSections() {
        return Arrays.asList(DocumentSection.values());
    }

    private DocumentTemplate requireStored(DocumentTemplateId id) {
        return templates.findById(id)
                .orElseThrow(() -> new NotFoundException("Document Template " + id + " does not exist."));
    }

    private void requireNotBuiltIn(DocumentTemplateId id, String verb) {
        if (DocumentTemplate.COMPLETE_ID.equals(id)) {
            throw new InvalidRequestException(
                    "The complete document cannot be " + verb + ". It is what Tower generates when no "
                            + "template is chosen; define a template of your own instead.");
        }
    }

    /**
     * Names are unique among stored templates, and none may take the built-in's.
     *
     * <p>A picker showing two entries called "Handover only" is a picker nobody
     * can use correctly, and one where the entry called "Complete document" is
     * not the complete document is worse than that.
     */
    private void requireNameIsFree(String name, DocumentTemplateId beingUpdated) {
        String candidate = name == null ? "" : name.trim();
        InvalidRequestException.require(!candidate.equalsIgnoreCase(DocumentTemplate.COMPLETE_NAME),
                "\"" + DocumentTemplate.COMPLETE_NAME + "\" names the built-in document that includes "
                        + "every section. Choose another name.");

        Optional<DocumentTemplate> existing = templates.findByName(candidate);
        if (existing.isPresent() && !existing.get().id().equals(beingUpdated)) {
            throw new ApplicationException("A Document Template named \"" + candidate + "\" already exists.");
        }
    }
}
