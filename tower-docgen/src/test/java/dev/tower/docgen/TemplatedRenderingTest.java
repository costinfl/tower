package dev.tower.docgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * Rendering under a Document Template (OQ-010, ADR-013).
 *
 * <p>Both renderers are exercised here rather than in their own files, because
 * what matters is that they agree: a team that previews the Markdown and hands
 * over the HTML must be looking at the same document.
 */
@DisplayName("Rendering with a Document Template")
class TemplatedRenderingTest {

    private static final Instant MONDAY = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant FRIDAY = Instant.parse("2026-08-07T17:00:00Z");

    private final MarkdownReleaseDocumentRenderer markdown = new MarkdownReleaseDocumentRenderer();
    private final HtmlReleaseDocumentRenderer html = new HtmlReleaseDocumentRenderer();

    private ReleaseDocument document() {
        return new ReleaseDocument(
                "Release 2026.08",
                "August business features",
                ReleasePackState.VALIDATION,
                false,
                Optional.of(new ReleaseDocument.PromotionPathSection(
                        "Regular", 1, List.of("Dev1", "SIT1", "UAT", "Production"))),
                List.of(new ReleaseDocument.ContentEntry(
                        "Customer API", "2.5.0", "release/2.5", "v2.5.0", "abc1234", "build-991")),
                new ReleaseDocument.HandoverSection(
                        "Deploy customer-api first.", "kubectl rollout status deploy/customer-api",
                        "V37__add_index.sql", "Roll back orders-api first.",
                        "Smoke test checkout.", "Expect brief latency.", true),
                List.of(new ReleaseDocument.IterationEntry("SIT Iteration 1", MONDAY, FRIDAY, "signed off")),
                List.of(new ReleaseDocument.SightingEntry(
                        "UAT", "Customer API", "2.5.0", FRIDAY, "manual", "costin", "obs-1")));
    }

    private static DocumentTemplate template(String name, DocumentSection... sections) {
        return new DocumentTemplate(DocumentTemplateId.newId(), name, List.of(sections));
    }

    @Nested
    @DisplayName("leaves out what the template does not select")
    class Selection {

        @Test
        void markdown_carries_only_the_chosen_sections() {
            String out = markdown.render(document(),
                    template("Handover only", DocumentSection.HANDOVER));

            assertThat(out).contains("## Handover").contains("Deploy customer-api first.");
            assertThat(out)
                    .doesNotContain("## Contents")
                    .doesNotContain("## Promotion Path")
                    .doesNotContain("## Validation Iterations")
                    .doesNotContain("## Where this release has been observed")
                    .doesNotContain("Observed state is derived from Observations");
        }

        @Test
        void html_carries_only_the_chosen_sections() {
            String out = html.render(document(),
                    template("Handover only", DocumentSection.HANDOVER));

            assertThat(out).contains("<h2>Handover</h2>").contains("Deploy customer-api first.");
            assertThat(out)
                    .doesNotContain("<h2>Contents</h2>")
                    .doesNotContain("<h2>Promotion Path</h2>")
                    .doesNotContain("<h2>Validation Iterations</h2>")
                    .doesNotContain("Observed state is derived from Observations");
        }

        @Test
        void the_title_survives_every_template() {
            // Not a section, so not a choice. A page that does not say which
            // release it describes is not a release document.
            var minimal = template("Contents only", DocumentSection.CONTENTS);

            assertThat(markdown.render(document(), minimal)).contains("# Release Pack: Release 2026.08");
            assertThat(html.render(document(), minimal)).contains("<h1>Release Pack: Release 2026.08</h1>");
        }

        @Test
        void the_provenance_note_survives_every_template() {
            // BR-07 is not a preference either.
            var minimal = template("Contents only", DocumentSection.CONTENTS);

            assertThat(markdown.render(document(), minimal)).contains("not a source of truth (BR-07)");
            assertThat(html.render(document(), minimal)).contains("not a source of truth (BR-07)");
        }
    }

    @Nested
    @DisplayName("renders in the order the template holds")
    class Ordering {

        @Test
        void markdown_follows_the_template_rather_than_the_declaration_order() {
            var reversed = template("Handover first",
                    DocumentSection.HANDOVER, DocumentSection.CONTENTS);

            String out = markdown.render(document(), reversed);

            assertThat(out.indexOf("## Handover")).isLessThan(out.indexOf("## Contents"));
        }

        @Test
        void html_follows_the_template_rather_than_the_declaration_order() {
            var reversed = template("Handover first",
                    DocumentSection.HANDOVER, DocumentSection.CONTENTS);

            String out = html.render(document(), reversed);

            assertThat(out.indexOf("<h2>Handover</h2>")).isLessThan(out.indexOf("<h2>Contents</h2>"));
        }
    }

    @Nested
    @DisplayName("says what it left out — the honesty rule, applied to the template")
    class OmissionsAreStated {

        @Test
        void markdown_names_the_template_and_the_sections_it_does_not_include() {
            String out = markdown.render(document(),
                    template("Handover only", DocumentSection.HANDOVER));

            assertThat(out)
                    .contains("using the \"Handover only\" template")
                    .contains("which does not include Status, Promotion Path, Contents, "
                            + "Validation Iterations and Where this release has been observed");
        }

        @Test
        void html_names_the_template_and_the_sections_it_does_not_include() {
            String out = html.render(document(),
                    template("Handover only", DocumentSection.HANDOVER));

            assertThat(out)
                    .contains("using the &quot;Handover only&quot; template")
                    .contains("which does not include Status, Promotion Path, Contents, "
                            + "Validation Iterations and Where this release has been observed");
        }

        @Test
        void a_reordered_but_complete_template_is_named_without_claiming_an_omission() {
            var everything = new DocumentTemplate(DocumentTemplateId.newId(), "House style",
                    List.of(DocumentSection.HANDOVER, DocumentSection.CONTENTS, DocumentSection.STATUS,
                            DocumentSection.PROMOTION_PATH, DocumentSection.ITERATIONS,
                            DocumentSection.SIGHTINGS));

            String out = markdown.render(document(), everything);

            assertThat(out).contains("using the \"House style\" template.");
            assertThat(out).doesNotContain("does not include");
        }

        @Test
        void the_complete_document_says_nothing_about_templates_at_all() {
            // What Tower generated before templates existed, unchanged.
            assertThat(markdown.render(document(), DocumentTemplate.complete()))
                    .isEqualTo(markdown.render(document()))
                    .doesNotContain("template");
            assertThat(html.render(document(), DocumentTemplate.complete()))
                    .isEqualTo(html.render(document()))
                    .doesNotContain("template");
        }
    }

    @Nested
    @DisplayName("stays reproducible — NFR-025")
    class Determinism {

        @Test
        void the_same_template_and_document_render_identical_bytes() {
            var chosen = template("Handover only", DocumentSection.HANDOVER, DocumentSection.SIGHTINGS);

            assertThat(markdown.render(document(), chosen))
                    .isEqualTo(markdown.render(document(), chosen));
            assertThat(html.render(document(), chosen))
                    .isEqualTo(html.render(document(), chosen));
        }

        @Test
        void two_templates_built_the_same_way_render_identical_bytes() {
            // The failure a template engine introduces quietly: a template
            // reloaded from the database is a different object, and must not be
            // a different document.
            var first = new DocumentTemplate(DocumentTemplateId.newId(), "Same",
                    List.of(DocumentSection.CONTENTS, DocumentSection.HANDOVER));
            var second = new DocumentTemplate(DocumentTemplateId.newId(), "Same",
                    List.of(DocumentSection.CONTENTS, DocumentSection.HANDOVER));

            assertThat(markdown.render(document(), first)).isEqualTo(markdown.render(document(), second));
            assertThat(html.render(document(), first)).isEqualTo(html.render(document(), second));
        }

        @Test
        void a_templated_document_still_embeds_no_generation_timestamp() {
            var chosen = template("Contents only", DocumentSection.CONTENTS);

            assertThat(markdown.render(document(), chosen))
                    .doesNotContain(Instant.now().toString().substring(0, 13));
            assertThat(html.render(document(), chosen))
                    .doesNotContain(Instant.now().toString().substring(0, 13));
        }
    }

    @Nested
    @DisplayName("keeps the hazards HTML has that Markdown does not")
    class Escaping {

        @Test
        void a_template_name_reaching_the_page_is_escaped_like_anything_else() {
            var hostile = template("<script>alert(1)</script>", DocumentSection.CONTENTS);

            String out = html.render(document(), hostile);

            assertThat(out).doesNotContain("<script>");
            assertThat(out).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        }
    }
}
