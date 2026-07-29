package dev.tower.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.port.in.DocumentTemplateUseCases.DefineTemplate;
import dev.tower.application.port.out.DocumentTemplateRepository;

@DisplayName("Document Template use cases")
class DocumentTemplateServiceTest {

    private InMemoryTemplates templates;
    private DocumentTemplateService service;

    @BeforeEach
    void setUp() {
        templates = new InMemoryTemplates();
        service = new DocumentTemplateService(templates);
    }

    private static DefineTemplate handoverOnly() {
        return new DefineTemplate("Handover only", List.of(DocumentSection.HANDOVER));
    }

    @Nested
    @DisplayName("protect the complete document")
    class BuiltIn {

        @Test
        void resolve_it_without_going_near_the_repository() {
            var resolved = service.require(DocumentTemplate.COMPLETE_ID);

            assertThat(resolved.isBuiltIn()).isTrue();
            assertThat(resolved.sections()).containsExactly(DocumentSection.values());
            assertThat(templates.lookups).isZero();
        }

        @Test
        void refuse_to_change_it() {
            assertThatThrownBy(() -> service.update(DocumentTemplate.COMPLETE_ID, handoverOnly()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("cannot be changed");
        }

        @Test
        void refuse_to_delete_it() {
            assertThatThrownBy(() -> service.delete(DocumentTemplate.COMPLETE_ID))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("cannot be deleted");
        }

        @Test
        void refuse_to_let_another_template_take_its_name() {
            // A picker whose "Complete document" entry is not the complete
            // document would mislead at exactly the moment it is trusted.
            var impostor = new DefineTemplate("complete DOCUMENT", List.of(DocumentSection.HANDOVER));

            assertThatThrownBy(() -> service.create(impostor))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Choose another name");
        }

        @Test
        void list_it_first_so_it_is_a_visible_choice_rather_than_a_hidden_fallback() {
            service.create(handoverOnly());

            assertThat(service.list()).hasSize(2);
            assertThat(service.list().get(0).isBuiltIn()).isTrue();
        }
    }

    @Nested
    @DisplayName("keep names usable in a picker")
    class Naming {

        @Test
        void reject_a_second_template_with_the_same_name() {
            service.create(handoverOnly());

            assertThatThrownBy(() -> service.create(handoverOnly()))
                    .isInstanceOf(ApplicationException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void let_a_template_keep_its_own_name_while_being_edited() {
            var created = service.create(handoverOnly());

            var updated = service.update(created.id(), new DefineTemplate(
                    "Handover only", List.of(DocumentSection.HANDOVER, DocumentSection.CONTENTS)));

            assertThat(updated.sections())
                    .containsExactly(DocumentSection.HANDOVER, DocumentSection.CONTENTS);
        }
    }

    @Nested
    @DisplayName("fail loudly for a template that is not there")
    class Missing {

        @Test
        void requiring_an_unknown_template_is_not_found() {
            // Never a fallback to the complete document: that would hand someone
            // the sections they had deliberately removed, with nothing on the
            // page to say so.
            assertThatThrownBy(() -> service.require(DocumentTemplateId.newId()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void updating_an_unknown_template_is_not_found() {
            assertThatThrownBy(() -> service.update(DocumentTemplateId.newId(), handoverOnly()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    @DisplayName("offer every section a template may choose from")
    void expose_the_available_sections() {
        assertThat(service.availableSections()).containsExactly(DocumentSection.values());
    }

    /** Keyed insertion-ordered so findAll returns something stable to assert on. */
    private static final class InMemoryTemplates implements DocumentTemplateRepository {

        private final Map<DocumentTemplateId, DocumentTemplate> stored = new LinkedHashMap<>();
        private int lookups;

        @Override
        public DocumentTemplate save(DocumentTemplate template) {
            stored.put(template.id(), template);
            return template;
        }

        @Override
        public Optional<DocumentTemplate> findById(DocumentTemplateId id) {
            lookups++;
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
