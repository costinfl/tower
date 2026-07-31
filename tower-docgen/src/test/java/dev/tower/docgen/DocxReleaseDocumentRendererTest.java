package dev.tower.docgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.domain.releasepack.ReleasePackState;

/**
 * The DOCX renderer (Milestone 3, OQ-009, ADR-015).
 *
 * <p>What these tests can and cannot prove is worth stating, because the gap is
 * real. They prove the package is a well-formed OOXML container: every part
 * present, every part valid XML, every part declared in
 * {@code [Content_Types].xml}, relationships resolving to parts that exist, and
 * the structural rules this renderer relies on. They also prove the bytes are
 * reproducible, which is the requirement ADR-015 exists to protect.
 *
 * <p>They do not prove Word opens it. Only opening it does that, and it should
 * be opened once by hand before anyone relies on the format.
 */
@DisplayName("The DOCX release document renderer")
class DocxReleaseDocumentRendererTest {

    private static final Instant MONDAY = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant FRIDAY = Instant.parse("2026-08-07T17:00:00Z");

    private final DocxReleaseDocumentRenderer renderer = new DocxReleaseDocumentRenderer();

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
                        "V37__add_index.sql", "", "Smoke test checkout.", "Expect brief latency.", true),
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
    @DisplayName("is reproducible — NFR-025, and the whole reason it is hand-written")
    class Determinism {

        @Test
        void rendering_the_same_document_twice_produces_identical_bytes() {
            assertThat(renderer.render(fullDocument())).isEqualTo(renderer.render(fullDocument()));
        }

        @Test
        void every_zip_entry_carries_a_fixed_timestamp() {
            // The failure a DOCX invites and the others cannot: a modification
            // time per entry, differing on every generation.
            for (Map.Entry<String, Long> entry : entryTimes(renderer.render(fullDocument())).entrySet()) {
                assertThat(entry.getValue())
                        .describedAs("timestamp of " + entry.getKey())
                        .isEqualTo(entryTimes(renderer.render(fullDocument())).get(entry.getKey()));
            }
        }

        @Test
        void no_part_records_a_date_or_an_identifier() {
            // docProps/core.xml is absent rather than empty: the part that does
            // not exist cannot acquire a created date in a later change.
            Map<String, String> parts = parts(renderer.render(fullDocument()));

            assertThat(parts).doesNotContainKey("docProps/core.xml");
            String today = Instant.now().toString().substring(0, 10);
            for (Map.Entry<String, String> part : parts.entrySet()) {
                assertThat(part.getValue())
                        .describedAs(part.getKey())
                        .doesNotContain(today)
                        .doesNotContain("dcterms:created")
                        .doesNotContain("w:rsid");
            }
        }
    }

    @Nested
    @DisplayName("is a well-formed OOXML package")
    class Package {

        @Test
        void contains_exactly_the_parts_it_declares() {
            Map<String, String> parts = parts(renderer.render(fullDocument()));

            assertThat(parts).containsOnlyKeys(
                    "[Content_Types].xml", "_rels/.rels",
                    "word/_rels/document.xml.rels", "word/styles.xml", "word/document.xml");
        }

        @Test
        void every_part_is_valid_xml() {
            parts(renderer.render(fullDocument())).forEach((name, content) ->
                    assertThat(wellFormed(content)).describedAs(name).isTrue());
        }

        @Test
        void every_xml_part_is_covered_by_a_content_type() {
            // An Override for a part that is not there, or a part with no
            // declared type, is how a package opens as "corrupt" with no
            // indication which part is at fault.
            Map<String, String> parts = parts(renderer.render(fullDocument()));
            String contentTypes = parts.get("[Content_Types].xml");

            assertThat(contentTypes).contains("PartName=\"/word/document.xml\"");
            assertThat(contentTypes).contains("PartName=\"/word/styles.xml\"");
            assertThat(contentTypes).contains("Extension=\"rels\"");
        }

        @Test
        void relationships_point_at_parts_that_exist() {
            Map<String, String> parts = parts(renderer.render(fullDocument()));

            assertThat(parts.get("_rels/.rels")).contains("Target=\"word/document.xml\"");
            assertThat(parts).containsKey("word/document.xml");
            assertThat(parts.get("word/_rels/document.xml.rels")).contains("Target=\"styles.xml\"");
            assertThat(parts).containsKey("word/styles.xml");
        }

        @Test
        void every_table_declares_its_grid() {
            // Required by the schema, and unforgiving: without it the file does
            // not open at all. Found by opening the output, not by validating
            // the XML — every part was well-formed either way.
            String body = parts(renderer.render(fullDocument())).get("word/document.xml");

            assertThat(count(body, "<w:tbl>")).isPositive();
            assertThat(count(body, "<w:tblGrid>")).isEqualTo(count(body, "<w:tbl>"));
        }

        @Test
        void paragraph_properties_are_in_schema_order() {
            // CT_PPr is a sequence, not a choice: w:spacing precedes
            // w:outlineLvl. Getting it backwards is a validity error that a
            // well-formedness check cannot see.
            String styles = parts(renderer.render(fullDocument())).get("word/styles.xml");

            int spacing = styles.indexOf("<w:spacing");
            int outline = styles.indexOf("<w:outlineLvl");
            assertThat(spacing).isNotNegative();
            assertThat(outline).isGreaterThan(spacing);
        }
    }

    @Nested
    @DisplayName("carries the structure Confluence imports")
    class Structure {

        @Test
        void uses_real_heading_styles_rather_than_bold_paragraphs() {
            // The reason the format was chosen. Confluence maps Word heading
            // styles to Confluence headings; a document that merely looks like it
            // has headings imports as one flat wall of text.
            Map<String, String> parts = parts(renderer.render(fullDocument()));

            assertThat(parts.get("word/styles.xml"))
                    .contains("w:styleId=\"Heading1\"")
                    .contains("w:styleId=\"Heading2\"")
                    .contains("<w:name w:val=\"heading 1\"/>");
            assertThat(parts.get("word/document.xml"))
                    .contains("<w:pStyle w:val=\"Heading1\"/>")
                    .contains("<w:pStyle w:val=\"Heading2\"/>");
        }

        @Test
        void states_every_absence_rather_than_omitting_sections() {
            String body = parts(renderer.render(emptyDocument())).get("word/document.xml");

            assertThat(body)
                    .contains("No Promotion Path has been assigned")
                    .contains("No Application Versions have been added")
                    .contains("No Handover information has been prepared")
                    .contains("No validation Iterations have been recorded")
                    .contains("has not been observed in any Environment");
        }

        @Test
        void keeps_line_breaks_inside_handover_text() {
            // A wrapped or merged line is a different shell command from the one
            // that was written.
            var document = new ReleaseDocument("R", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                new ReleaseDocument.HandoverSection(
                            "first line\nsecond line", "", "", "", "", "", true),
                    List.of(), List.of());

            assertThat(parts(renderer.render(document)).get("word/document.xml"))
                    .contains("<w:br/>");
        }

        @Test
        void says_it_is_a_view_of_the_model_rather_than_a_source_of_truth() {
            assertThat(parts(renderer.render(fullDocument())).get("word/document.xml"))
                    .contains("not a source of truth (BR-07)");
        }
    }

    @Nested
    @DisplayName("handles text XML cannot carry")
    class Escaping {

        @Test
        void escapes_markup_in_text_taken_from_the_model() {
            var document = new ReleaseDocument("<script>", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                new ReleaseDocument.HandoverSection(
                            "cat a.xml | grep \"<host>\" && echo 'done'", "", "", "", "", "", true),
                    List.of(), List.of());

            String body = parts(renderer.render(document)).get("word/document.xml");

            assertThat(wellFormed(body)).isTrue();
            assertThat(body).contains("&lt;script&gt;").contains("&lt;host&gt;").contains("&amp;&amp;");
        }

        @Test
        void drops_control_characters_xml_cannot_represent() {
            // Handover text is pasted from terminals. A control character left in
            // place makes Word refuse the whole file, which loses the document
            // rather than one character and tells the reader nothing.
            var document = new ReleaseDocument("R", "", ReleasePackState.PLANNED, false,
                    Optional.empty(), List.of(),
                    List.of(),
                new ReleaseDocument.HandoverSection(
                            "before after", "", "", "", "", "", true),
                    List.of(), List.of());

            String body = parts(renderer.render(document)).get("word/document.xml");

            assertThat(wellFormed(body)).isTrue();
            assertThat(body).contains("beforeafter");
        }

        @Test
        void a_template_name_reaching_the_document_is_escaped_too() {
            var hostile = new DocumentTemplate(
                    DocumentTemplateId.newId(), "<b>bold</b>", List.of(DocumentSection.CONTENTS));

            String body = parts(renderer.render(fullDocument(), hostile)).get("word/document.xml");

            assertThat(wellFormed(body)).isTrue();
            assertThat(body).contains("&lt;b&gt;bold&lt;/b&gt;");
        }
    }

    @Nested
    @DisplayName("honours a Document Template like its siblings")
    class Templates {

        @Test
        void renders_only_the_chosen_sections_in_the_chosen_order() {
            var reversed = new DocumentTemplate(DocumentTemplateId.newId(), "Handover first",
                    List.of(DocumentSection.HANDOVER, DocumentSection.CONTENTS));

            String body = parts(renderer.render(fullDocument(), reversed)).get("word/document.xml");

            // Counted as headings rather than searched as text: "Promotion Path"
            // legitimately appears in the closing note, which names what the
            // template leaves out. Asserting on the text alone would have made
            // that correct behaviour look like a bug.
            assertThat(count(body, "<w:pStyle w:val=\"Heading2\"/>")).isEqualTo(2);
            assertThat(body).doesNotContain("No Promotion Path has been assigned");
            assertThat(body.indexOf("Handover")).isLessThan(body.indexOf("Contents"));
        }

        @Test
        void names_the_template_and_what_it_leaves_out() {
            var handoverOnly = new DocumentTemplate(
                    DocumentTemplateId.newId(), "Handover only", List.of(DocumentSection.HANDOVER));

            assertThat(parts(renderer.render(fullDocument(), handoverOnly)).get("word/document.xml"))
                    .contains("using the &quot;Handover only&quot; template")
                    .contains("which does not include");
        }

        @Test
        void a_templated_document_is_reproducible_too() {
            var chosen = new DocumentTemplate(
                    DocumentTemplateId.newId(), "Handover only", List.of(DocumentSection.HANDOVER));

            assertThat(renderer.render(fullDocument(), chosen))
                    .isEqualTo(renderer.render(fullDocument(), chosen));
        }
    }

    // --- helpers -----------------------------------------------------------

    private static Map<String, String> parts(byte[] docx) {
        var parts = new LinkedHashMap<String, String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parts;
    }

    private static Map<String, Long> entryTimes(byte[] docx) {
        var times = new LinkedHashMap<String, Long>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                times.put(entry.getName(), entry.getTime());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return times;
    }

    private static boolean wellFormed(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = haystack.indexOf(needle);
        List<Integer> ignored = new ArrayList<>();
        while (at >= 0) {
            found++;
            ignored.add(at);
            at = haystack.indexOf(needle, at + needle.length());
        }
        return found;
    }
}
