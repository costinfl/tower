package dev.tower.persistence.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.service.ApplicationException;
import dev.tower.persistence.support.PersistenceTestSupport;

@DisplayName("The Document Template repository")
class DocumentTemplateJdbcRepositoryTest {

    @TempDir
    Path tempDir;

    private JdbcClient jdbcClient;
    private DocumentTemplateJdbcRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        jdbcClient = PersistenceTestSupport.migratedJdbcClient(tempDir);
        repository = new DocumentTemplateJdbcRepository(jdbcClient);
    }

    private static DocumentTemplate template(String name, DocumentSection... sections) {
        return new DocumentTemplate(DocumentTemplateId.newId(), name, List.of(sections));
    }

    @Nested
    @DisplayName("keeps the order the user chose")
    class Ordering {

        @Test
        void reloads_the_sections_in_the_order_they_were_saved() {
            var saved = repository.save(template("House style",
                    DocumentSection.HANDOVER, DocumentSection.CONTENTS, DocumentSection.SIGHTINGS));

            var reloaded = repository.findById(saved.id()).orElseThrow();

            assertThat(reloaded.sections()).containsExactly(
                    DocumentSection.HANDOVER, DocumentSection.CONTENTS, DocumentSection.SIGHTINGS);
        }

        @Test
        void reloads_the_reverse_order_just_as_faithfully() {
            // The pair that catches an ORDER BY on the section name, or on the
            // enum's own order, either of which would pass the test above.
            var saved = repository.save(template("Reversed",
                    DocumentSection.SIGHTINGS, DocumentSection.CONTENTS, DocumentSection.HANDOVER));

            assertThat(repository.findById(saved.id()).orElseThrow().sections()).containsExactly(
                    DocumentSection.SIGHTINGS, DocumentSection.CONTENTS, DocumentSection.HANDOVER);
        }

        @Test
        void stores_section_names_rather_than_ordinals() {
            // An ordinal would silently re-point every stored row the moment a
            // section is inserted into the middle of the enum.
            repository.save(template("Named", DocumentSection.HANDOVER));

            assertThat(jdbcClient.sql("SELECT section FROM document_template_section")
                    .query(String.class).single()).isEqualTo("HANDOVER");
        }
    }

    @Nested
    @DisplayName("replaces rather than accumulates")
    class Saving {

        @Test
        void a_second_save_leaves_no_trace_of_the_first_section_list() {
            var saved = repository.save(template("Evolving",
                    DocumentSection.STATUS, DocumentSection.CONTENTS, DocumentSection.HANDOVER));

            repository.save(new DocumentTemplate(saved.id(), "Evolving", List.of(DocumentSection.HANDOVER)));

            assertThat(repository.findById(saved.id()).orElseThrow().sections())
                    .containsExactly(DocumentSection.HANDOVER);
            assertThat(jdbcClient.sql("SELECT COUNT(*) FROM document_template_section")
                    .query(Integer.class).single()).isEqualTo(1);
        }

        @Test
        void a_rename_keeps_the_identity_and_the_sections() {
            var saved = repository.save(template("Before", DocumentSection.CONTENTS));

            repository.save(new DocumentTemplate(saved.id(), "After", List.of(DocumentSection.CONTENTS)));

            assertThat(repository.findAll()).hasSize(1);
            assertThat(repository.findById(saved.id()).orElseThrow().name()).isEqualTo("After");
        }

        @Test
        void the_database_refuses_two_templates_with_the_same_name() {
            // The service checks this first; the constraint is what holds if two
            // requests ever race past it.
            repository.save(template("Handover only", DocumentSection.HANDOVER));

            assertThatThrownBy(() -> repository.save(template("Handover only", DocumentSection.CONTENTS)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("finds and forgets")
    class Lookup {

        @Test
        void finds_by_name_whatever_the_case() {
            repository.save(template("Handover Only", DocumentSection.HANDOVER));

            assertThat(repository.findByName("handover only")).isPresent();
            assertThat(repository.findByName("  Handover Only  ")).isPresent();
            assertThat(repository.findByName("something else")).isEmpty();
        }

        @Test
        void lists_templates_by_name_so_a_picker_reads_the_same_everywhere() {
            repository.save(template("Zebra", DocumentSection.CONTENTS));
            repository.save(template("apple", DocumentSection.CONTENTS));
            repository.save(template("Mango", DocumentSection.CONTENTS));

            assertThat(repository.findAll()).extracting(DocumentTemplate::name)
                    .containsExactly("apple", "Mango", "Zebra");
        }

        @Test
        void deleting_a_template_takes_its_sections_with_it() {
            var saved = repository.save(template("Doomed",
                    DocumentSection.CONTENTS, DocumentSection.HANDOVER));

            repository.deleteById(saved.id());

            assertThat(repository.findById(saved.id())).isEmpty();
            assertThat(jdbcClient.sql("SELECT COUNT(*) FROM document_template_section")
                    .query(Integer.class).single()).isZero();
        }
    }

    @Test
    @DisplayName("reports a section written by a later version of Tower rather than dropping it")
    void refuses_to_silently_render_less_than_was_saved() {
        var saved = repository.save(template("Future", DocumentSection.CONTENTS));
        jdbcClient.sql("""
                        INSERT INTO document_template_section (document_template_id, position, section)
                        VALUES (:id, 1, 'APPENDIX')
                        """)
                .param("id", saved.id().value())
                .update();

        assertThatThrownBy(() -> repository.findById(saved.id()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("APPENDIX");
    }
}
