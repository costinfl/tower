package dev.tower.api.documentation;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.api.documentation.DocumentTemplateRequests.DocumentSectionResponse;
import dev.tower.api.documentation.DocumentTemplateRequests.DocumentTemplateRequest;
import dev.tower.api.documentation.DocumentTemplateRequests.DocumentTemplateResponse;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.port.in.DocumentTemplateUseCases;
import dev.tower.application.port.in.DocumentTemplateUseCases.DefineTemplate;

/**
 * REST API for Document Templates (OQ-010, ADR-013, FR-062 to FR-065).
 *
 * <p>A template chooses which sections a release document contains and in what
 * order. It carries no markup, so nothing here accepts a document body — see
 * ADR-013 for why that is a decision rather than an omission.
 *
 * <p>{@code POST} then {@code PUT} on an id, unlike the binding API's bare
 * {@code PUT}: a template is identified by an id Tower generates rather than by
 * the thing it describes, so there is nothing for a caller to name it by.
 */
@RestController
@RequestMapping("/api/document-templates")
public class DocumentTemplateController {

    private final DocumentTemplateUseCases templates;

    public DocumentTemplateController(DocumentTemplateUseCases templates) {
        this.templates = templates;
    }

    /** Stored templates, with the built-in complete document at the head. */
    @GetMapping
    public List<DocumentTemplateResponse> list() {
        return templates.list().stream().map(DocumentTemplateResponse::from).toList();
    }

    /**
     * The sections a template may choose from.
     *
     * <p>Served rather than left for each client to hardcode, so adding a
     * section to a release document does not need the Viewer changed to know
     * about it.
     */
    @GetMapping("/sections")
    public List<DocumentSectionResponse> sections() {
        return templates.availableSections().stream().map(DocumentSectionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentTemplateResponse get(@PathVariable("id") String id) {
        return DocumentTemplateResponse.from(templates.require(DocumentTemplateId.of(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTemplateResponse create(@Valid @RequestBody DocumentTemplateRequest request) {
        return DocumentTemplateResponse.from(templates.create(
                new DefineTemplate(request.name(), request.parsedSections())));
    }

    @PutMapping("/{id}")
    public DocumentTemplateResponse update(
            @PathVariable("id") String id, @Valid @RequestBody DocumentTemplateRequest request) {
        return DocumentTemplateResponse.from(templates.update(
                DocumentTemplateId.of(id), new DefineTemplate(request.name(), request.parsedSections())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        templates.delete(DocumentTemplateId.of(id));
    }
}
