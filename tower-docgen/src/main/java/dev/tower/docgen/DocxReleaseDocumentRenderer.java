package dev.tower.docgen;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Renders a {@link ReleaseDocument} as a Word document (Milestone 3, OQ-009,
 * ADR-015).
 *
 * <p>The third sibling of {@link MarkdownReleaseDocumentRenderer} and
 * {@link HtmlReleaseDocumentRenderer}, and built the same way: assembled by this
 * class, with no library. ADR-015 records why that matters more here than
 * anywhere else. A DOCX is a ZIP of XML, and both halves are hostile to NFR-025 —
 * ZIP entries carry modification times, and word-processing libraries stamp
 * {@code docProps/core.xml} with created and modified dates. A DOCX produced the
 * obvious way differs on every generation, in bytes nobody would think to check.
 *
 * <p>So every ZIP entry carries a fixed timestamp, and {@code docProps/core.xml}
 * is not written at all — the part that does not exist cannot acquire a date
 * later.
 *
 * <p>DOCX rather than PDF because of what happens to the document afterwards.
 * Confluence imports a Word document as an editable page and cannot do that with
 * a PDF, while DOCX converts to PDF trivially. The relationship is one-way, so
 * this is the format that keeps both options.
 *
 * <p>Headings use real Word heading styles rather than bold paragraphs that look
 * like headings. Confluence's import maps Word heading styles to Confluence
 * headings; a document formatted to merely resemble one imports as a flat wall of
 * text, losing the structure the format was chosen for.
 */
public class DocxReleaseDocumentRenderer {

    private static final String NOT_PREPARED = "Not prepared.";

    /**
     * A fixed point for every ZIP entry.
     *
     * <p>Any constant would do; this is the MS-DOS epoch that ZIP itself uses as
     * its floor, so no tool has to represent a date it considers invalid.
     */
    private static final long FIXED_TIME = 315532800000L; // 1980-01-01T00:00:00Z

    public byte[] render(ReleaseDocument document) {
        return render(document, DocumentTemplate.complete());
    }

    public byte[] render(ReleaseDocument document, DocumentTemplate template) {
        String body = documentXml(document, template);

        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            // Order is fixed, because the order entries are written in is part of
            // the bytes. A map iteration here would be the same class of mistake
            // ADR-013 refused for templates.
            write(zip, "[Content_Types].xml", CONTENT_TYPES);
            write(zip, "_rels/.rels", ROOT_RELS);
            write(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS);
            write(zip, "word/styles.xml", STYLES);
            write(zip, "word/document.xml", body);
        } catch (IOException e) {
            // In-memory: this cannot fail for a reason a caller could act on.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private void write(ZipOutputStream zip, String name, String content) throws IOException {
        var entry = new ZipEntry(name);
        // setTime only. Setting creation or access time as well makes the writer
        // emit an extended-timestamp extra field, which is more bytes that could
        // vary and buys nothing.
        entry.setTime(FIXED_TIME);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // --- the document ------------------------------------------------------

    private String documentXml(ReleaseDocument document, DocumentTemplate template) {
        StringBuilder out = new StringBuilder();
        out.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                """);

        heading(out, 1, "Release Pack: " + document.packName());

        if (!document.description().isBlank()) {
            paragraph(out, document.description(), null);
        }

        // The template's own order, exactly as the other two renderers use it.
        for (DocumentSection section : template.sections()) {
            switch (section) {
                case STATUS -> renderStatus(out, document);
                case PROMOTION_PATH -> renderPromotionPath(out, document.promotionPath());
                case CONTENTS -> renderContents(out, document.contents());
                case ARTIFACTS -> renderArtifacts(out, document.artifacts());
                case WORK_ITEMS -> renderWorkItems(out, document.workItems());
                case HANDOVER -> renderHandover(out, document.handover());
                case ITERATIONS -> renderIterations(out, document.iterations());
                case SIGHTINGS -> renderSightings(out, document.sightings());
            }
        }

        paragraph(out, DocumentProvenance.note(template, " "), "Quote");

        // sectPr closes the body and sets the page. Without it Word applies a
        // default that differs between versions, which would be a difference in
        // what the reader sees rather than in the bytes.
        out.append("""
                <w:sectPr>
                <w:pgSz w:w="11906" w:h="16838"/>
                <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/>
                </w:sectPr>
                </w:body>
                </w:document>
                """);
        return out.toString();
    }

    private void renderStatus(StringBuilder out, ReleaseDocument document) {
        table(out,
                List.of("", ""),
                List.of(
                        List.of("Observed state", document.state().name()),
                        List.of("Lifecycle", document.archived() ? "Archived" : "Active")),
                false);

        // ADR-008 keeps these two independent, so the document does too.
        paragraph(out, "Observed state is derived from Observations and records where this release "
                + "has been seen. Lifecycle is a decision by the team and says nothing about deployment.",
                "Quote");
    }

    private void renderPromotionPath(StringBuilder out, Optional<ReleaseDocument.PromotionPathSection> section) {
        heading(out, 2, "Promotion Path");
        if (section.isEmpty()) {
            paragraph(out, "No Promotion Path has been assigned to this Release Pack.", "Quote");
            return;
        }
        ReleaseDocument.PromotionPathSection path = section.get();
        paragraph(out, path.name() + " — version " + path.versionNumber(), null);
        paragraph(out, String.join(" → ", path.environments()), null);
        paragraph(out, "This release follows version " + path.versionNumber()
                + " of the path. Later versions may define a different sequence; this is the "
                + "topology the release was planned against (ADR-007).", "Quote");
    }

    private void renderContents(StringBuilder out, List<ReleaseDocument.ContentEntry> contents) {
        heading(out, 2, "Contents");
        if (contents.isEmpty()) {
            paragraph(out, "No Application Versions have been added to this Release Pack.", "Quote");
            return;
        }
        var rows = new java.util.ArrayList<List<String>>();
        for (ReleaseDocument.ContentEntry entry : contents) {
            rows.add(List.of(entry.applicationName(), entry.version(), orDash(entry.branch()),
                    orDash(entry.tag()), orDash(entry.commit()), orDash(entry.buildIdentifier())));
        }
        table(out, List.of("Application", "Version", "Branch", "Tag", "Commit", "Build"), rows, true);
    }

    /** What the release delivers, in the words the team accepted (ADR-018). */
    /** The digests somebody accepted, never what the repository says now (FR-086). */
    private void renderArtifacts(StringBuilder out, List<ReleaseDocument.ArtifactEntry> artifacts) {
        heading(out, 2, "Artifacts");
        if (artifacts.isEmpty()) {
            paragraph(out, ReleaseDocument.NO_ARTIFACTS_ACCEPTED, "Quote");
            return;
        }
        var rows = new java.util.ArrayList<List<String>>();
        for (ReleaseDocument.ArtifactEntry entry : artifacts) {
            rows.add(List.of(entry.applicationName(), entry.version(), entry.kind(),
                    entry.coordinate(), entry.digest()));
        }
        table(out, List.of("Application", "Version", "Kind", "Coordinate", "Digest"), rows, true);
    }

    private void renderWorkItems(StringBuilder out, List<ReleaseDocument.WorkItemEntry> workItems) {
        heading(out, 2, "Work Items");
        if (workItems.isEmpty()) {
            paragraph(out, "No work items have been linked to this Release Pack.", "Quote");
            return;
        }
        var rows = new java.util.ArrayList<List<String>>();
        for (ReleaseDocument.WorkItemEntry entry : workItems) {
            rows.add(List.of(entry.identifier(),
                    entry.title().isBlank() ? "no title accepted" : entry.title()));
        }
        table(out, List.of("Item", "Title"), rows, true);
    }

    private void renderHandover(StringBuilder out, ReleaseDocument.HandoverSection handover) {
        heading(out, 2, "Handover");
        if (!handover.prepared()) {
            paragraph(out, "No Handover information has been prepared for this Release Pack.", "Quote");
            return;
        }
        // Tower assists in preparing this information and never executes it
        // (ADR-001). The commands below describe what someone else will run.
        section(out, "Deployment instructions", handover.deploymentInstructions());
        section(out, "Shell commands", handover.shellCommands());
        section(out, "Database migrations", handover.databaseMigrations());
        section(out, "Rollback procedure", handover.rollbackProcedure());
        section(out, "Validation notes", handover.validationNotes());
        section(out, "Operational notes", handover.operationalNotes());
    }

    /**
     * Handover bodies keep their line breaks and are set monospaced.
     *
     * <p>These are shell commands and migration steps, where a wrapped or merged
     * line is a different instruction from the one that was written.
     */
    private void section(StringBuilder out, String headingText, String body) {
        heading(out, 3, headingText);
        if (body.isBlank()) {
            paragraph(out, NOT_PREPARED, "Quote");
            return;
        }
        paragraph(out, body, "Code");
    }

    private void renderIterations(StringBuilder out, List<ReleaseDocument.IterationEntry> iterations) {
        heading(out, 2, "Validation Iterations");
        if (iterations.isEmpty()) {
            paragraph(out, "No validation Iterations have been recorded against this Release Pack.", "Quote");
            return;
        }
        var rows = new java.util.ArrayList<List<String>>();
        for (ReleaseDocument.IterationEntry iteration : iterations) {
            rows.add(List.of(iteration.name(), iso(iteration.startedAt()),
                    iteration.isComplete() ? iso(iteration.completedAt()) : "in progress",
                    orDash(iteration.notes())));
        }
        table(out, List.of("Iteration", "Started", "Completed", "Notes"), rows, true);
    }

    private void renderSightings(StringBuilder out, List<ReleaseDocument.SightingEntry> sightings) {
        heading(out, 2, "Where this release has been observed");
        if (sightings.isEmpty()) {
            paragraph(out, "This release has not been observed in any Environment.", "Quote");
            return;
        }
        // FR-031, FR-032, NFR-011: every claim cites the Observation behind it.
        var rows = new java.util.ArrayList<List<String>>();
        for (ReleaseDocument.SightingEntry sighting : sightings) {
            rows.add(List.of(sighting.environmentName(), sighting.applicationName(), sighting.version(),
                    iso(sighting.observedAt()), describeSource(sighting), orDash(sighting.observationId())));
        }
        table(out, List.of("Environment", "Application", "Version", "Observed", "Source", "Observation"),
                rows, true);
    }

    // --- WordprocessingML --------------------------------------------------

    private void heading(StringBuilder out, int level, String text) {
        out.append("<w:p><w:pPr><w:pStyle w:val=\"Heading").append(level).append("\"/></w:pPr>")
                .append(runs(text))
                .append("</w:p>\n");
    }

    private void paragraph(StringBuilder out, String text, String style) {
        out.append("<w:p>");
        if (style != null) {
            out.append("<w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr>");
        }
        out.append(runs(text)).append("</w:p>\n");
    }

    /**
     * Text as runs, with line breaks preserved.
     *
     * <p>A newline in Word is {@code <w:br/>} between runs, not a character. Left
     * as a character it would be swallowed and two shell commands would become
     * one line — the failure the monospaced Handover blocks exist to prevent.
     */
    private String runs(String text) {
        String safe = escape(text);
        StringBuilder out = new StringBuilder("<w:r>");
        String[] lines = safe.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append("<w:br/>");
            }
            out.append("<w:t xml:space=\"preserve\">").append(lines[i]).append("</w:t>");
        }
        return out.append("</w:r>").toString();
    }

    private void table(StringBuilder out, List<String> headers, List<List<String>> rows, boolean headed) {
        out.append("""
                <w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/>
                <w:tblW w:w="0" w:type="auto"/>
                <w:tblBorders>
                <w:top w:val="single" w:sz="4" w:color="999999"/>
                <w:left w:val="single" w:sz="4" w:color="999999"/>
                <w:bottom w:val="single" w:sz="4" w:color="999999"/>
                <w:right w:val="single" w:sz="4" w:color="999999"/>
                <w:insideH w:val="single" w:sz="4" w:color="999999"/>
                <w:insideV w:val="single" w:sz="4" w:color="999999"/>
                </w:tblBorders></w:tblPr>
                """);
        // Required by the schema, and its absence is not forgiving: Word and
        // LibreOffice both refuse to open the file rather than laying the table
        // out themselves. Found by opening the output, not by validating the XML
        // — every part was well-formed.
        out.append("<w:tblGrid>");
        for (int i = 0; i < headers.size(); i++) {
            out.append("<w:gridCol w:w=\"").append(9638 / headers.size()).append("\"/>");
        }
        out.append("</w:tblGrid>\n");

        if (headed) {
            row(out, headers, true);
        }
        for (List<String> cells : rows) {
            row(out, cells, false);
        }
        out.append("</w:tbl>\n");
        // Word merges two tables that touch. An empty paragraph after each one
        // keeps Contents and Iterations from becoming a single table.
        out.append("<w:p/>\n");
    }

    private void row(StringBuilder out, List<String> cells, boolean header) {
        out.append("<w:tr>");
        for (String cell : cells) {
            out.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>");
            out.append("<w:p>");
            if (header) {
                out.append("<w:pPr><w:rPr><w:b/></w:rPr></w:pPr>");
            }
            out.append("<w:r>");
            if (header) {
                out.append("<w:rPr><w:b/></w:rPr>");
            }
            out.append("<w:t xml:space=\"preserve\">").append(escape(cell)).append("</w:t>");
            out.append("</w:r></w:p></w:tc>");
        }
        out.append("</w:tr>\n");
    }

    private String describeSource(ReleaseDocument.SightingEntry sighting) {
        if (sighting.sourceCollector() == null) {
            return "—";
        }
        return sighting.sourceActor() == null
                ? sighting.sourceCollector()
                : sighting.sourceCollector() + " (" + sighting.sourceActor() + ")";
    }

    private String iso(Instant instant) {
        return instant == null ? "—" : instant.toString();
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /**
     * Escapes for XML, and drops what XML cannot carry.
     *
     * <p>The escaping is the same hazard the HTML renderer has. The dropping is
     * one this format adds: Handover text is written by people and can contain a
     * control character pasted from a terminal, and XML 1.0 has no representation
     * for most of them. Left in place, Word refuses to open the file at all —
     * which loses the whole document rather than one character, and gives the
     * reader no clue why.
     *
     * <p>Tab, newline and carriage return are kept, because those are the ones
     * that mean something in a shell command.
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                case '\r' -> { /* normalised away; the \n beside it carries the break */ }
                default -> {
                    if (c == '\t' || c == '\n' || c >= 0x20) {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    // --- the fixed parts ---------------------------------------------------

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>
            """;

    private static final String ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """;

    private static final String DOCUMENT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
            """;

    /**
     * Deliberately plain, and deliberately real heading styles.
     *
     * <p>{@code Heading1} to {@code Heading3} carry the {@code w:styleId} Word and
     * Confluence look for. Formatting a paragraph to merely look like a heading
     * would render identically and import as body text.
     */
    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:docDefaults><w:rPrDefault><w:rPr>
            <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/>
            </w:rPr></w:rPrDefault></w:docDefaults>
            <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
            <w:name w:val="Normal"/>
            </w:style>
            <!-- CT_PPr is a sequence, not a choice: w:spacing precedes w:outlineLvl.
                 Order is part of validity here, not a matter of taste. -->
            <w:style w:type="paragraph" w:styleId="Heading1">
            <w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>
            <w:pPr><w:spacing w:before="240" w:after="120"/><w:outlineLvl w:val="0"/></w:pPr>
            <w:rPr><w:b/><w:sz w:val="36"/></w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Heading2">
            <w:name w:val="heading 2"/><w:basedOn w:val="Normal"/>
            <w:pPr><w:spacing w:before="240" w:after="120"/><w:outlineLvl w:val="1"/></w:pPr>
            <w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Heading3">
            <w:name w:val="heading 3"/><w:basedOn w:val="Normal"/>
            <w:pPr><w:spacing w:before="180" w:after="80"/><w:outlineLvl w:val="2"/></w:pPr>
            <w:rPr><w:b/><w:sz w:val="24"/></w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Quote">
            <w:name w:val="Quote"/><w:basedOn w:val="Normal"/>
            <w:rPr><w:i/></w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Code">
            <w:name w:val="HTML Preformatted"/><w:basedOn w:val="Normal"/>
            <w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/><w:sz w:val="20"/></w:rPr>
            </w:style>
            <w:style w:type="table" w:styleId="TableGrid">
            <w:name w:val="Table Grid"/>
            </w:style>
            </w:styles>
            """;
}
