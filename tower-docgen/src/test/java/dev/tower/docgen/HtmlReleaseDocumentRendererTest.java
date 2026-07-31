package dev.tower.docgen;

import dev.tower.domain.releasepack.ReleasePackState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The HTML release document renderer")
class HtmlReleaseDocumentRendererTest {

    private static final Instant MONDAY = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant FRIDAY = Instant.parse("2026-08-07T17:00:00Z");

    private final HtmlReleaseDocumentRenderer renderer = new HtmlReleaseDocumentRenderer();

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
                List.of(),
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
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                List.of(), List.of());
    }

    @Nested
    @DisplayName("is reproducible — NFR-025")
    class Determinism {

        @Test
        void rendering_the_same_document_twice_produces_identical_bytes() {
            assertThat(renderer.render(fullDocument())).isEqualTo(renderer.render(fullDocument()));
        }

        @Test
        void carries_no_generation_timestamp_that_would_make_every_regeneration_a_diff() {
            // The failure mode a template engine introduces quietly. Only the
            // instants supplied by the document itself may appear.
            String html = renderer.render(fullDocument());

            assertThat(html).contains(FRIDAY.toString());
            assertThat(html).doesNotContain(Instant.now().toString().substring(0, 13));
        }
    }

    @Nested
    @DisplayName("escapes everything the model supplies")
    class Escaping {

        @Test
        void does_not_let_a_shell_redirect_in_handover_swallow_the_document() {
            // The hazard HTML has that Markdown does not. Unescaped, this closes
            // nothing and the reader silently loses the rest of the page.
            var document = new ReleaseDocument("Release", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                new ReleaseDocument.HandoverSection(
                            "run <script>alert(1)</script> & check", "cat a.txt > b.txt",
                            "", "", "", "", true),
                    List.of(), List.of());

            String html = renderer.render(document);

            assertThat(html).doesNotContain("<script>");
            assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
            assertThat(html).contains("cat a.txt &gt; b.txt");
            assertThat(html).contains("&amp; check");
            // The document still terminates properly rather than being truncated.
            assertThat(html).endsWith("</html>\n");
        }

        @Test
        void escapes_a_pack_name_in_both_the_title_and_the_heading() {
            var document = new ReleaseDocument("R&D <urgent>", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(), List.of());

            String html = renderer.render(document);

            assertThat(html).doesNotContain("<urgent>");
            assertThat(html).contains("<title>Release Pack: R&amp;D &lt;urgent&gt;</title>");
            assertThat(html).contains("<h1>Release Pack: R&amp;D &lt;urgent&gt;</h1>");
        }

        @Test
        void escapes_free_text_in_table_cells() {
            var document = new ReleaseDocument("Release", "", ReleasePackState.PLANNED, false,
                    Optional.empty(),
                    List.of(new ReleaseDocument.ContentEntry(
                            "A & B", "1.0", "feature/<x>", null, null, null)),
                    List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(), List.of());

            String html = renderer.render(document);

            assertThat(html).contains("A &amp; B").contains("feature/&lt;x&gt;");
        }
    }

    @Nested
    @DisplayName("states absent information rather than omitting it")
    class Absence {

        @Test
        void says_when_no_handover_has_been_prepared() {
            assertThat(renderer.render(emptyDocument()))
                    .contains("No Handover information has been prepared");
        }

        @Test
        void says_when_no_promotion_path_is_assigned() {
            assertThat(renderer.render(emptyDocument()))
                    .contains("No Promotion Path has been assigned");
        }

        @Test
        void says_when_the_release_has_been_observed_nowhere() {
            assertThat(renderer.render(emptyDocument()))
                    .contains("has not been observed in any Environment");
        }

        @Test
        void marks_an_iteration_still_running_rather_than_leaving_it_blank() {
            var document = new ReleaseDocument("Release", "", ReleasePackState.VALIDATION, false,
                    Optional.empty(), List.of(),
                    List.of(),
                new ReleaseDocument.HandoverSection("", "", "", "", "", "", false),
                    List.of(new ReleaseDocument.IterationEntry("SIT 1", MONDAY, null, "")),
                    List.of());

            assertThat(renderer.render(document)).contains("in progress");
        }
    }

    @Nested
    @DisplayName("is a self-contained page")
    class SelfContained {

        @Test
        void needs_no_network_request_to_render_correctly() {
            // A release document is opened from a file share or a mail
            // attachment long after the fact. One that renders unstyled because
            // a stylesheet is missing has failed at the moment it mattered.
            String html = renderer.render(fullDocument());

            assertThat(html).contains("<style>");
            assertThat(html).doesNotContain("<link");
            assertThat(html).doesNotContain("<script");
            assertThat(html).doesNotContain("http://").doesNotContain("https://");
        }

        @Test
        void is_a_complete_document() {
            String html = renderer.render(fullDocument());

            assertThat(html).startsWith("<!doctype html>");
            assertThat(html).endsWith("</html>\n");
        }
    }

    @Nested
    @DisplayName("says the same things the Markdown renderer says")
    class SameClaims {

        @Test
        void keeps_observed_state_and_lifecycle_distinct_per_ADR_008() {
            String html = renderer.render(fullDocument());

            assertThat(html).contains("Observed state").contains("Lifecycle").contains("Active");
            assertThat(html).contains("Lifecycle is a decision by the team");
        }

        @Test
        void records_the_promotion_path_version_the_release_was_planned_against() {
            assertThat(renderer.render(fullDocument()))
                    .contains("version 1")
                    .contains("Dev1 → SIT1 → UAT → Production");
        }

        @Test
        void cites_the_observation_behind_each_sighting() {
            assertThat(renderer.render(fullDocument())).contains("obs-1").contains("manual (costin)");
        }

        @Test
        void says_it_is_a_view_of_the_model_rather_than_a_source_of_truth() {
            assertThat(renderer.render(fullDocument())).contains("not a source of truth");
        }
    }
}
