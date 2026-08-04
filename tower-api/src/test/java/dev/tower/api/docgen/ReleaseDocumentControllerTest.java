package dev.tower.api.docgen;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.tower.api.error.ApiExceptionHandler;
import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.port.out.DocumentTemplateRepository;
import dev.tower.application.service.DocumentTemplateService;
import dev.tower.docgen.DocxReleaseDocumentRenderer;
import dev.tower.docgen.HtmlReleaseDocumentRenderer;
import dev.tower.docgen.MarkdownReleaseDocumentRenderer;
import dev.tower.docgen.ReleaseDocument;
import dev.tower.docgen.ReleaseDocumentAssembler;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * OQ-010: choosing a template on the documentation endpoints.
 *
 * <p>The assembly of the document is covered in tower-docgen; what is under test
 * here is the wiring — that the template reaches the renderer, that an unknown
 * one is refused rather than substituted, and that two templates of the same
 * release do not download over each other.
 */
@DisplayName("The release documentation API")
class ReleaseDocumentControllerTest {

    private static final String PACK = "11111111-1111-1111-1111-111111111111";

    private InMemoryTemplates repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ReleaseDocumentAssembler assembler = mock(ReleaseDocumentAssembler.class);
        when(assembler.assemble(any(ReleasePackId.class))).thenReturn(document());

        repository = new InMemoryTemplates();
        mvc = MockMvcBuilders.standaloneSetup(new ReleaseDocumentController(
                        assembler, new MarkdownReleaseDocumentRenderer(), new HtmlReleaseDocumentRenderer(),
                        new DocxReleaseDocumentRenderer(), new DocumentTemplateService(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static ReleaseDocument document() {
        return new ReleaseDocument("Release 2026.08", "", ReleasePackState.PLANNED, false,
                Optional.empty(), List.of(),
                List.of(),
                List.of(),
                new ReleaseDocument.HandoverSection("Deploy it.", "", "", "", "", "", true),
                List.of(), List.of());
    }

    private DocumentTemplate storeHandoverOnly() {
        return repository.save(new DocumentTemplate(
                DocumentTemplateId.newId(), "Handover only", List.of(DocumentSection.HANDOVER)));
    }

    @Test
    void renders_the_complete_document_when_no_template_is_asked_for() throws Exception {
        mvc.perform(get("/api/release-packs/{id}/documentation/markdown", PACK))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("## Contents")));
    }

    @Test
    void renders_only_the_chosen_sections_when_a_template_is() throws Exception {
        var template = storeHandoverOnly();

        String body = mvc.perform(get("/api/release-packs/{id}/documentation/markdown", PACK)
                        .param("template", template.id().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .contains("## Handover")
                .doesNotContain("## Contents")
                .contains("using the \"Handover only\" template");
    }

    @Test
    void applies_the_template_to_html_as_well() throws Exception {
        var template = storeHandoverOnly();

        String body = mvc.perform(get("/api/release-packs/{id}/documentation/html", PACK)
                        .param("template", template.id().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .contains("<h2>Handover</h2>")
                .doesNotContain("<h2>Contents</h2>");
    }

    @Test
    void names_the_download_after_the_template_so_two_do_not_collide() throws Exception {
        var template = storeHandoverOnly();

        mvc.perform(get("/api/release-packs/{id}/documentation/markdown/download", PACK))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"release-2026-08.md\""));

        mvc.perform(get("/api/release-packs/{id}/documentation/html/download", PACK)
                        .param("template", template.id().toString()))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"release-2026-08-handover-only.html\""));
    }

    @Test
    void serves_a_word_document_as_a_download_with_its_own_extension() throws Exception {
        // No inline counterpart: a browser cannot render a DOCX, so serving one
        // inline would be a download with a misleading disposition.
        var response = mvc.perform(get("/api/release-packs/{id}/documentation/docx/download", PACK))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"release-2026-08.docx\""))
                .andReturn().getResponse();

        byte[] body = response.getContentAsByteArray();
        // "PK" — it is a ZIP, which is what a DOCX is.
        org.assertj.core.api.Assertions.assertThat(body).startsWith((byte) 0x50, (byte) 0x4B);
        org.assertj.core.api.Assertions.assertThat(response.getContentType())
                .contains("wordprocessingml.document");
    }

    @Test
    void refuses_an_unknown_template_rather_than_falling_back_to_the_complete_document() throws Exception {
        // A fallback would hand someone the sections they had deliberately
        // removed, with nothing on the page to say so.
        mvc.perform(get("/api/release-packs/{id}/documentation/markdown", PACK)
                        .param("template", DocumentTemplateId.newId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void treats_a_blank_template_parameter_as_no_choice() throws Exception {
        mvc.perform(get("/api/release-packs/{id}/documentation/markdown", PACK).param("template", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("## Contents")));
    }

    private static final class InMemoryTemplates implements DocumentTemplateRepository {

        private final Map<DocumentTemplateId, DocumentTemplate> stored = new LinkedHashMap<>();

        @Override
        public DocumentTemplate save(DocumentTemplate template) {
            stored.put(template.id(), template);
            return template;
        }

        @Override
        public Optional<DocumentTemplate> findById(DocumentTemplateId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<DocumentTemplate> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public List<DocumentTemplate> findAll() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public void deleteById(DocumentTemplateId id) {
            stored.remove(id);
        }
    }
}
