package dev.tower.api.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/** OQ-010, ADR-013: the Document Template API. */
@DisplayName("The Document Template API")
class DocumentTemplateControllerTest {

    private InMemoryTemplates repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTemplates();
        mvc = MockMvcBuilders
                .standaloneSetup(new DocumentTemplateController(new DocumentTemplateService(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static String body(String name, String... sections) {
        String list = String.join(",", List.of(sections).stream().map(s -> "\"" + s + "\"").toList());
        return "{\"name\":\"%s\",\"sections\":[%s]}".formatted(name, list);
    }

    private String createHandoverOnly() throws Exception {
        String response = mvc.perform(post("/api/document-templates")
                        .contentType("application/json").content(body("Handover only", "HANDOVER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void creates_a_template_and_reports_what_it_leaves_out() throws Exception {
        mvc.perform(post("/api/document-templates")
                        .contentType("application/json").content(body("Handover only", "HANDOVER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Handover only"))
                .andExpect(jsonPath("$.sections[0]").value("HANDOVER"))
                .andExpect(jsonPath("$.omitted").isNotEmpty())
                .andExpect(jsonPath("$.builtIn").value(false));
    }

    @Test
    void keeps_the_order_the_request_supplied() throws Exception {
        mvc.perform(post("/api/document-templates")
                        .contentType("application/json")
                        .content(body("Reversed", "SIGHTINGS", "CONTENTS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sections[0]").value("SIGHTINGS"))
                .andExpect(jsonPath("$.sections[1]").value("CONTENTS"));
    }

    @Test
    void lists_the_complete_document_first() throws Exception {
        createHandoverOnly();

        mvc.perform(get("/api/document-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].builtIn").value(true))
                .andExpect(jsonPath("$[0].omitted").isEmpty())
                .andExpect(jsonPath("$[1].name").value("Handover only"));
    }

    @Test
    void serves_the_sections_a_template_may_choose_from() throws Exception {
        // So the Viewer does not hardcode a list that a new section would
        // silently make wrong.
        mvc.perform(get("/api/document-templates/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(DocumentSection.values().length))
                .andExpect(jsonPath("$[0].name").value(DocumentSection.values()[0].name()))
                .andExpect(jsonPath("$[0].heading").isNotEmpty())
                .andExpect(jsonPath("$[0].description").isNotEmpty());
    }

    @Test
    void replaces_a_template_wholesale_on_update() throws Exception {
        String id = createHandoverOnly();

        mvc.perform(put("/api/document-templates/" + id)
                        .contentType("application/json")
                        .content(body("Handover and contents", "HANDOVER", "CONTENTS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sections.length()").value(2));
    }

    @Test
    void deletes_a_template() throws Exception {
        String id = createHandoverOnly();

        mvc.perform(delete("/api/document-templates/" + id)).andExpect(status().isNoContent());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void rejects_an_unknown_section_rather_than_quietly_dropping_it() throws Exception {
        // Storing a template that renders less than the caller asked for, and
        // answering 200 while doing it, is the failure this guards.
        mvc.perform(post("/api/document-templates")
                        .contentType("application/json").content(body("Odd", "HANDOVER", "APPENDIX")))
                .andExpect(status().isBadRequest());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void rejects_a_template_with_no_sections() throws Exception {
        mvc.perform(post("/api/document-templates")
                        .contentType("application/json").content("{\"name\":\"Empty\",\"sections\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_a_duplicate_name_as_a_conflict() throws Exception {
        createHandoverOnly();

        mvc.perform(post("/api/document-templates")
                        .contentType("application/json").content(body("Handover only", "CONTENTS")))
                .andExpect(status().isConflict());
    }

    @Test
    void refuses_to_delete_the_complete_document() throws Exception {
        mvc.perform(delete("/api/document-templates/" + DocumentTemplate.COMPLETE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answers_not_found_for_a_template_that_does_not_exist() throws Exception {
        mvc.perform(get("/api/document-templates/" + DocumentTemplateId.newId()))
                .andExpect(status().isNotFound());
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
            return stored.values().stream()
                    .filter(t -> t.name().equalsIgnoreCase(name == null ? "" : name.trim()))
                    .findFirst();
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
