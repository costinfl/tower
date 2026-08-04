package dev.tower.docgen;

import dev.tower.domain.releasepack.ReleasePackState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReleaseDocumentRendererTest {

    private static final Instant MONDAY = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant FRIDAY = Instant.parse("2026-08-07T17:00:00Z");

    private final MarkdownReleaseDocumentRenderer renderer = new MarkdownReleaseDocumentRenderer();

    private ReleaseDocument fullDocument() {
        return new ReleaseDocument(
                "Release 2026.08",
                "August business features",
                ReleasePackState.VALIDATION,
                false,
                Optional.of(new ReleaseDocument.PromotionPathSection(
                        "Regular", 1, List.of("Dev1", "SIT1", "UAT", "Production"))),
                List.of(new ReleaseDocument.ContentEntry(
                        "Customer API", "2.5.0", "release/2.5", "v2.5.0", "abc1234", "build-991")),
                List.of(new ReleaseDocument.ArtifactEntry("Customer API", "2.5.0", "image",
                        "docker-local/acme/customer-api:2.5.0-abc1234", "sha256:9f86d0")),
                List.of(new ReleaseDocument.WorkItemEntry("PROJ-123", "Save basket"),
                        new ReleaseDocument.WorkItemEntry("PROJ-140", "")),
                new ReleaseDocument.HandoverSection(
                        "Deploy customer-api first.", "kubectl rollout status deploy/customer-api",
                        "V37__add_index.sql", "Roll back orders-api first.",
                        "Smoke test checkout.", "Expect brief latency.", true),
                List.of(new ReleaseDocument.IterationEntry("SIT Iteration 1", MONDAY, FRIDAY, "signed off")),
                List.of(new ReleaseDocument.SightingEntry(
                        "UAT", "Customer API", "2.5.0", FRIDAY, "manual", "costin", "obs-1")));
    }

    private ReleaseDocument emptyDocument() {
        return new ReleaseDocument("Release 2026.09", "", ReleasePackState.PLANNED, false,
                Optional.empty(), List.of(),
                List.of(),
                List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                List.of(), List.of());
    }

    @Nested
    @DisplayName("work items — what the release delivers (ADR-018)")
    class WorkItems {

        @Test
        void lists_what_the_release_delivers_in_the_order_they_were_linked() {
            String out = renderer.render(fullDocument());

            assertThat(out).contains("## Work Items");
            assertThat(out.indexOf("PROJ-123")).isLessThan(out.indexOf("PROJ-140"));
            assertThat(out).contains("| PROJ-123 | Save basket |");
        }

        @Test
        void an_item_with_no_accepted_title_says_so_rather_than_leaving_a_blank() {
            assertThat(renderer.render(fullDocument())).contains("| PROJ-140 | _no title accepted_ |");
        }

        @Test
        void prints_no_status_because_the_tracker_owns_it() {
            // ADR-018: a status printed into a document would be stale before
            // the document was read, and NFR-025 would break the moment a
            // ticket moved.
            String out = renderer.render(fullDocument());

            assertThat(out).doesNotContain("Status |").doesNotContain("In Progress").doesNotContain("Done");
        }

        @Test
        void a_release_with_no_work_items_says_so() {
            assertThat(renderer.render(emptyDocument()))
                    .contains("_No work items have been linked to this Release Pack._");
        }
    }

    @Nested
    class Determinism {

        /**
         * Scenario 8 and IA-03: generated documentation is disposable because it
         * can always be reproduced. A generation timestamp in the body would
         * break that quietly, turning every regeneration into a spurious diff.
         */
        @Test
        void rendering_the_same_document_twice_produces_identical_bytes() {
            assertThat(renderer.render(fullDocument())).isEqualTo(renderer.render(fullDocument()));
        }

        @Test
        void the_output_embeds_no_generation_timestamp() {
            String markdown = renderer.render(fullDocument());

            // Only timestamps the facts themselves carry may appear.
            assertThat(markdown).contains(MONDAY.toString(), FRIDAY.toString());
            assertThat(markdown).doesNotContain(Instant.now().toString().substring(0, 13));
        }
    }

    @Nested
    class AbsentInformationIsStated {

        /**
         * A document that silently omits an empty Handover reads as though none
         * was needed. One that says so tells the truth, which matters most
         * exactly when someone is relying on it under pressure.
         */
        @Test
        void an_empty_document_states_every_absence_rather_than_omitting_sections() {
            String markdown = renderer.render(emptyDocument());

            assertThat(markdown)
                    .contains("## Promotion Path")
                    .contains("No Promotion Path has been assigned")
                    .contains("## Contents")
                    .contains("No Application Versions have been added")
                    .contains("## Handover")
                    .contains("No Handover information has been prepared")
                    .contains("## Validation Iterations")
                    .contains("No validation Iterations have been recorded")
                    .contains("## Where this release has been observed")
                    .contains("has not been observed in any Environment");
        }

        @Test
        void absent_optional_version_attributes_render_as_a_dash_not_as_blank_cells() {
            ReleaseDocument document = new ReleaseDocument("R", "", ReleasePackState.PLANNED, false,
                    Optional.empty(),
                    List.of(new ReleaseDocument.ContentEntry("Orders API", "1.0.0", null, null, null, null)),
                    List.of(),
                List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(), List.of());

            assertThat(renderer.render(document)).contains("| Orders API | 1.0.0 | — | — | — | — |");
        }
    }

    @Nested
    class Traceability {

        /** FR-031, FR-032, NFR-011: each claim cites the fact behind it. */
        @Test
        void sightings_cite_their_observation_and_source() {
            String markdown = renderer.render(fullDocument());

            assertThat(markdown).contains("manual (costin)").contains("obs-1");
        }

        @Test
        void the_document_declares_it_is_a_view_rather_than_a_source_of_truth() {
            assertThat(renderer.render(fullDocument())).contains("not a source of truth");
        }
    }

    @Nested
    class StateAndLifecycleStaySeparate {

        /**
         * ADR-008 keeps observed state and the archived flag independent. A pack
         * can be archived and have reached Production; collapsing them into one
         * indicator would lose a fact rather than tidy the page.
         */
        @Test
        void an_archived_pack_still_reports_where_it_was_observed() {
            ReleaseDocument archived = new ReleaseDocument("R", "", ReleasePackState.PRODUCTION, true,
                    Optional.empty(), List.of(),
                    List.of(),
                List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(), List.of());

            String markdown = renderer.render(archived);

            assertThat(markdown).contains("| Observed state | PRODUCTION |");
            assertThat(markdown).contains("| Lifecycle | Archived |");
        }
    }

    @Nested
    class TableSafety {

        /** Free text must not break the table it sits in. */
        @Test
        void pipes_and_newlines_in_notes_are_neutralised() {
            ReleaseDocument document = new ReleaseDocument("R", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(new ReleaseDocument.IterationEntry(
                            "UAT", MONDAY, null, "passed | mostly\nsecond line")),
                    List.of());

            String row = renderer.render(document).lines()
                    .filter(line -> line.startsWith("| UAT "))
                    .findFirst().orElseThrow();

            assertThat(row).contains("passed \\| mostly second line");

            // Only UNESCAPED pipes delimit cells. The escaped one is still a pipe
            // character, so counting raw pipes would prove nothing about whether
            // the table survived.
            long cellDelimiters = row.replace("\\|", "").chars().filter(c -> c == '|').count();
            assertThat(cellDelimiters)
                    .as("four columns should still be delimited by five pipes")
                    .isEqualTo(5);
            assertThat(row).doesNotContain("\n");
        }

        @Test
        void an_open_iteration_is_marked_rather_than_left_blank() {
            ReleaseDocument document = new ReleaseDocument("R", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(new ReleaseDocument.IterationEntry("UAT", MONDAY, null, "")),
                    List.of());

            assertThat(renderer.render(document)).contains("_in progress_");
        }
    }
}
