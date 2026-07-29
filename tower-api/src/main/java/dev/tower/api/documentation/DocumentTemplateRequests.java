package dev.tower.api.documentation;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.service.InvalidRequestException;

/** Request and response bodies for /api/document-templates (OQ-010, ADR-013). */
public final class DocumentTemplateRequests {

    private DocumentTemplateRequests() {
    }

    /**
     * {@code sections} carries section names, in the order they should render.
     *
     * <p>A list of names rather than a set of booleans, because the order is
     * part of the template and a "which ones" shape could not express it.
     */
    public record DocumentTemplateRequest(
            @NotBlank(message = "name is required") String name,
            @NotEmpty(message = "sections is required") List<String> sections) {

        /**
         * Rejects an unknown section name rather than ignoring it.
         *
         * <p>Silently dropping one would store a template that renders less than
         * the caller asked for, and answer 200 while doing it.
         */
        public List<DocumentSection> parsedSections() {
            List<DocumentSection> parsed = new ArrayList<>();
            for (String name : sections) {
                parsed.add(DocumentSection.parse(name).orElseThrow(() -> new InvalidRequestException(
                        "\"" + name + "\" is not a document section. Ask GET /api/document-templates/sections "
                                + "for the ones this version of Tower offers.")));
            }
            return parsed;
        }
    }

    /**
     * @param builtIn whether this is the complete document Tower defines itself,
     *                which cannot be changed or deleted
     */
    public record DocumentTemplateResponse(
            String id, String name, List<String> sections, List<String> omitted, boolean builtIn) {

        public static DocumentTemplateResponse from(DocumentTemplate template) {
            return new DocumentTemplateResponse(
                    template.id().value().toString(),
                    template.name(),
                    template.sections().stream().map(Enum::name).toList(),
                    template.omitted().stream().map(Enum::name).toList(),
                    template.isBuiltIn());
        }
    }

    /** One section a template may choose, described well enough to choose it. */
    public record DocumentSectionResponse(String name, String heading, String description) {

        public static DocumentSectionResponse from(DocumentSection section) {
            return new DocumentSectionResponse(section.name(), section.heading(), section.description());
        }
    }
}
