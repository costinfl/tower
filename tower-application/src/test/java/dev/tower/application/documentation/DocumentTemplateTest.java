package dev.tower.application.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.service.InvalidRequestException;

@DisplayName("A Document Template")
class DocumentTemplateTest {

    private static DocumentTemplate template(DocumentSection... sections) {
        return new DocumentTemplate(DocumentTemplateId.newId(), "Handover only", List.of(sections));
    }

    @Nested
    @DisplayName("keeps the complete document intact")
    class Complete {

        @Test
        void includes_every_section_in_the_order_they_are_declared() {
            assertThat(DocumentTemplate.complete().sections())
                    .containsExactly(DocumentSection.values());
        }

        @Test
        void omits_nothing() {
            assertThat(DocumentTemplate.complete().omitted()).isEmpty();
            assertThat(DocumentTemplate.complete().isComplete()).isTrue();
        }

        @Test
        void is_recognisable_as_the_built_in_one() {
            assertThat(DocumentTemplate.complete().isBuiltIn()).isTrue();
            assertThat(template(DocumentSection.HANDOVER).isBuiltIn()).isFalse();
        }

        @Test
        void grows_with_the_document_rather_than_needing_a_list_kept_in_step() {
            // The guard on a section being added to DocumentSection and quietly
            // left out of the complete document, which would make Tower's
            // fullest output silently no longer full.
            assertThat(DocumentTemplate.complete().sections())
                    .hasSize(DocumentSection.values().length);
        }
    }

    @Nested
    @DisplayName("names what it leaves out")
    class Omissions {

        @Test
        void reports_the_missing_sections_in_declaration_order() {
            var handoverOnly = template(DocumentSection.HANDOVER);

            assertThat(handoverOnly.omitted()).containsExactly(
                    DocumentSection.STATUS,
                    DocumentSection.PROMOTION_PATH,
                    DocumentSection.CONTENTS,
                    DocumentSection.ARTIFACTS,
                    DocumentSection.WORK_ITEMS,
                    DocumentSection.ITERATIONS,
                    DocumentSection.SIGHTINGS);
        }

        @Test
        void reports_the_same_omissions_whatever_order_the_sections_were_chosen_in() {
            var forwards = template(DocumentSection.CONTENTS, DocumentSection.HANDOVER);
            var backwards = template(DocumentSection.HANDOVER, DocumentSection.CONTENTS);

            assertThat(forwards.omitted()).isEqualTo(backwards.omitted());
            assertThat(forwards.sections()).isNotEqualTo(backwards.sections());
        }
    }

    @Nested
    @DisplayName("refuses a shape that cannot render a usable document")
    class Validation {

        @Test
        void rejects_a_template_with_no_sections() {
            assertThatThrownBy(() -> new DocumentTemplate(DocumentTemplateId.newId(), "Empty", List.of()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("at least one section");
        }

        @Test
        void rejects_the_same_section_twice() {
            assertThatThrownBy(() -> template(DocumentSection.CONTENTS, DocumentSection.CONTENTS))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("at most once");
        }

        @Test
        void rejects_a_blank_name() {
            assertThatThrownBy(() -> new DocumentTemplate(
                    DocumentTemplateId.newId(), "  ", List.of(DocumentSection.CONTENTS)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("must have a name");
        }

        @Test
        void rejects_a_name_longer_than_the_column_that_stores_it() {
            String tooLong = "x".repeat(DocumentTemplate.MAX_NAME_LENGTH + 1);

            assertThatThrownBy(() -> new DocumentTemplate(
                    DocumentTemplateId.newId(), tooLong, List.of(DocumentSection.CONTENTS)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("at most");
        }

        @Test
        void trims_the_name_so_two_that_look_alike_cannot_both_exist() {
            assertThat(template(DocumentSection.HANDOVER).name()).isEqualTo("Handover only");
            assertThat(new DocumentTemplate(DocumentTemplateId.newId(), "  Spaced  ",
                    List.of(DocumentSection.CONTENTS)).name()).isEqualTo("Spaced");
        }

        @Test
        void keeps_its_own_copy_of_the_section_list() {
            // Order is the template. A caller that could mutate the list after
            // construction could change what a document renders without saving
            // anything - the kind of drift NFR-025 has to be immune to.
            List<DocumentSection> mutable = new ArrayList<>(
                    List.of(DocumentSection.CONTENTS, DocumentSection.HANDOVER));
            var built = new DocumentTemplate(DocumentTemplateId.newId(), "Two", mutable);

            mutable.clear();

            assertThat(built.sections())
                    .containsExactly(DocumentSection.CONTENTS, DocumentSection.HANDOVER);
        }
    }

    @Nested
    @DisplayName("parses section names")
    class Parsing {

        @Test
        void accepts_a_known_name_whatever_its_case() {
            assertThat(DocumentSection.parse("handover")).contains(DocumentSection.HANDOVER);
            assertThat(DocumentSection.parse(" PROMOTION_PATH ")).contains(DocumentSection.PROMOTION_PATH);
        }

        @Test
        void returns_empty_for_a_name_it_does_not_know() {
            assertThat(DocumentSection.parse("APPENDIX")).isEmpty();
            assertThat(DocumentSection.parse(null)).isEmpty();
        }

        @Test
        void describes_every_section_well_enough_to_choose_it() {
            for (DocumentSection section : DocumentSection.values()) {
                assertThat(section.heading()).isNotBlank();
                assertThat(section.description()).isNotBlank();
            }
        }
    }
}
