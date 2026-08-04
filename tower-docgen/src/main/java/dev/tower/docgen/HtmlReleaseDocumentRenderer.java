package dev.tower.docgen;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Renders a {@link ReleaseDocument} as a self-contained HTML page (Milestone 3,
 * OQ-009).
 *
 * <p>Sibling of {@link MarkdownReleaseDocumentRenderer}, deliberately built the
 * same way: string assembly from the document, no template engine, no
 * dependency. That is not laziness. NFR-025 requires regeneration of an
 * unchanged Release Pack to be byte-identical, and a template engine is the
 * usual way that guarantee dies quietly — one that stamps a generation date, or
 * iterates a map in hash order, turns every regeneration into a spurious diff
 * that nobody notices for months.
 *
 * <p>So there is no generation timestamp here either, for the same reason there
 * is none in the Markdown renderer.
 *
 * <p>The styles are inlined rather than linked. A release document is something
 * a team attaches to a ticket, mails, or opens from a file share long after the
 * fact; one that renders unstyled because a stylesheet is missing has failed at
 * the moment it mattered. Self-contained also means it carries no script and
 * makes no network request when opened.
 *
 * <p>Everything the model supplies is escaped. Handover text is written by
 * people and routinely contains shell commands, XML fragments and SQL — a
 * {@code &} or {@code <} in a rollback procedure must appear in the page, not
 * silently truncate it. This is the one hazard HTML has that Markdown does not.
 */
public class HtmlReleaseDocumentRenderer {

    private static final String NOT_PREPARED = "Not prepared.";

    /**
     * Deliberately plain. This is a document to read and print, not a screen —
     * the Viewer is where Tower has an interface.
     */
    private static final String STYLES = """
            :root { color-scheme: light dark; }
            body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
                   line-height: 1.5; max-width: 60rem; margin: 2rem auto; padding: 0 1.5rem; }
            h1 { margin-bottom: 0.25rem; }
            h2 { margin-top: 2rem; border-bottom: 1px solid currentColor; padding-bottom: 0.25rem; }
            h3 { margin-top: 1.25rem; font-size: 1rem; }
            table { border-collapse: collapse; width: 100%; margin: 0.5rem 0 1rem; }
            th, td { border: 1px solid currentColor; padding: 0.4rem 0.6rem; text-align: left;
                     vertical-align: top; font-size: 0.95rem; }
            th { font-weight: 600; }
            blockquote { margin: 1rem 0; padding: 0.5rem 1rem; border-left: 3px solid currentColor; }
            pre { white-space: pre-wrap; word-break: break-word; margin: 0.25rem 0 1rem; }
            .absent { font-style: italic; }
            .path { font-size: 1.05rem; margin: 0.5rem 0; }
            footer { margin-top: 2.5rem; padding-top: 1rem; border-top: 1px solid currentColor;
                     font-style: italic; font-size: 0.9rem; }
            @media print { body { max-width: none; margin: 0; } }
            """;

    /** The complete document, which is what Tower produces when nobody has chosen. */
    public String render(ReleaseDocument document) {
        return render(document, DocumentTemplate.complete());
    }

    public String render(ReleaseDocument document, DocumentTemplate template) {
        StringBuilder out = new StringBuilder();

        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Release Pack: ").append(escape(document.packName())).append("</title>\n")
                .append("<style>\n").append(STYLES).append("</style>\n")
                .append("</head>\n<body>\n");

        out.append("<h1>Release Pack: ").append(escape(document.packName())).append("</h1>\n");

        if (!document.description().isBlank()) {
            out.append("<p>").append(escape(document.description())).append("</p>\n");
        }

        // Iterated in the template's own order (OQ-010, ADR-013). The template
        // chooses sections; every byte below is still written here.
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

        // Escaped like everything else: a template name is user-supplied text
        // and reaches the page here.
        out.append("<footer>").append(escape(DocumentProvenance.note(template, " "))).append("</footer>\n")
                .append("</body>\n</html>\n");

        return out.toString();
    }

    private void renderStatus(StringBuilder out, ReleaseDocument document) {
        out.append("<table>\n")
                .append("<tr><th>Observed state</th><td>").append(escape(document.state().name())).append("</td></tr>\n")
                .append("<tr><th>Lifecycle</th><td>").append(document.archived() ? "Archived" : "Active")
                .append("</td></tr>\n</table>\n");

        // ADR-008 keeps these two independent, so the document does too.
        out.append("<blockquote>Observed state is derived from Observations and records where this release ")
                .append("has been seen. Lifecycle is a decision by the team and says nothing about deployment.")
                .append("</blockquote>\n");
    }

    private void renderPromotionPath(StringBuilder out, Optional<ReleaseDocument.PromotionPathSection> section) {
        out.append("<h2>Promotion Path</h2>\n");
        if (section.isEmpty()) {
            absent(out, "No Promotion Path has been assigned to this Release Pack.");
            return;
        }
        ReleaseDocument.PromotionPathSection path = section.get();
        out.append("<p class=\"path\"><strong>").append(escape(path.name()))
                .append("</strong> — version ").append(path.versionNumber()).append("</p>\n");
        out.append("<p class=\"path\">")
                .append(escape(String.join(" → ", path.environments())))
                .append("</p>\n");
        out.append("<p class=\"absent\">This release follows version ").append(path.versionNumber())
                .append(" of the path. Later versions may define a different sequence; this is the ")
                .append("topology the release was planned against (ADR-007).</p>\n");
    }

    private void renderContents(StringBuilder out, List<ReleaseDocument.ContentEntry> contents) {
        out.append("<h2>Contents</h2>\n");
        if (contents.isEmpty()) {
            absent(out, "No Application Versions have been added to this Release Pack.");
            return;
        }
        out.append("<table>\n<tr><th>Application</th><th>Version</th><th>Branch</th>")
                .append("<th>Tag</th><th>Commit</th><th>Build</th></tr>\n");
        for (ReleaseDocument.ContentEntry entry : contents) {
            out.append("<tr><td>").append(escape(entry.applicationName()))
                    .append("</td><td>").append(escape(entry.version()))
                    .append("</td><td>").append(orDash(entry.branch()))
                    .append("</td><td>").append(orDash(entry.tag()))
                    .append("</td><td>").append(orDash(entry.commit()))
                    .append("</td><td>").append(orDash(entry.buildIdentifier()))
                    .append("</td></tr>\n");
        }
        out.append("</table>\n");
    }

    /** What the release delivers, in the words the team accepted (ADR-018). */
    /** The digests somebody accepted, never what the repository says now (FR-086). */
    private void renderArtifacts(StringBuilder out, List<ReleaseDocument.ArtifactEntry> artifacts) {
        out.append("<h2>Artifacts</h2>\n");
        if (artifacts.isEmpty()) {
            absent(out, ReleaseDocument.NO_ARTIFACTS_ACCEPTED);
            return;
        }
        out.append("<table>\n<tr><th>Application</th><th>Version</th><th>Kind</th>")
                .append("<th>Coordinate</th><th>Digest</th></tr>\n");
        for (ReleaseDocument.ArtifactEntry entry : artifacts) {
            out.append("<tr><td>").append(escape(entry.applicationName()))
                    .append("</td><td>").append(escape(entry.version()))
                    .append("</td><td>").append(escape(entry.kind()))
                    .append("</td><td><code>").append(escape(entry.coordinate()))
                    .append("</code></td><td><code>").append(escape(entry.digest()))
                    .append("</code></td></tr>\n");
        }
        out.append("</table>\n");
    }

    private void renderWorkItems(StringBuilder out, List<ReleaseDocument.WorkItemEntry> workItems) {
        out.append("<h2>Work Items</h2>\n");
        if (workItems.isEmpty()) {
            absent(out, "No work items have been linked to this Release Pack.");
            return;
        }
        out.append("<table>\n<tr><th>Item</th><th>Title</th></tr>\n");
        for (ReleaseDocument.WorkItemEntry entry : workItems) {
            out.append("<tr><td>").append(escape(entry.identifier()))
                    .append("</td><td>")
                    // Said rather than left blank: an empty cell reads as an
                    // oversight, and this is a deliberate state.
                    .append(entry.title().isBlank()
                            ? "<em>no title accepted</em>" : escape(entry.title()))
                    .append("</td></tr>\n");
        }
        out.append("</table>\n");
    }

    private void renderHandover(StringBuilder out, ReleaseDocument.HandoverSection handover) {
        out.append("<h2>Handover</h2>\n");
        if (!handover.prepared()) {
            absent(out, "No Handover information has been prepared for this Release Pack.");
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
     * Handover bodies keep their line breaks in a {@code <pre>}: these are shell
     * commands and migration steps, where a wrapped or collapsed line is a
     * different instruction from the one that was written.
     */
    private void section(StringBuilder out, String heading, String body) {
        out.append("<h3>").append(escape(heading)).append("</h3>\n");
        if (body.isBlank()) {
            absent(out, NOT_PREPARED);
            return;
        }
        out.append("<pre>").append(escape(body)).append("</pre>\n");
    }

    private void renderIterations(StringBuilder out, List<ReleaseDocument.IterationEntry> iterations) {
        out.append("<h2>Validation Iterations</h2>\n");
        if (iterations.isEmpty()) {
            absent(out, "No validation Iterations have been recorded against this Release Pack.");
            return;
        }
        out.append("<table>\n<tr><th>Iteration</th><th>Started</th><th>Completed</th><th>Notes</th></tr>\n");
        for (ReleaseDocument.IterationEntry iteration : iterations) {
            out.append("<tr><td>").append(escape(iteration.name()))
                    .append("</td><td>").append(iso(iteration.startedAt()))
                    .append("</td><td>").append(iteration.isComplete()
                            ? iso(iteration.completedAt())
                            : "<span class=\"absent\">in progress</span>")
                    .append("</td><td>").append(orDash(iteration.notes()))
                    .append("</td></tr>\n");
        }
        out.append("</table>\n");
    }

    private void renderSightings(StringBuilder out, List<ReleaseDocument.SightingEntry> sightings) {
        out.append("<h2>Where this release has been observed</h2>\n");
        if (sightings.isEmpty()) {
            absent(out, "This release has not been observed in any Environment.");
            return;
        }
        // FR-031, FR-032, NFR-011: every claim cites the Observation behind it,
        // so a reader can check the document rather than take it on trust.
        out.append("<table>\n<tr><th>Environment</th><th>Application</th><th>Version</th>")
                .append("<th>Observed</th><th>Source</th><th>Observation</th></tr>\n");
        for (ReleaseDocument.SightingEntry sighting : sightings) {
            out.append("<tr><td>").append(escape(sighting.environmentName()))
                    .append("</td><td>").append(escape(sighting.applicationName()))
                    .append("</td><td>").append(escape(sighting.version()))
                    .append("</td><td>").append(iso(sighting.observedAt()))
                    .append("</td><td>").append(escape(describeSource(sighting)))
                    .append("</td><td>").append(orDash(sighting.observationId()))
                    .append("</td></tr>\n");
        }
        out.append("</table>\n");
    }

    private String describeSource(ReleaseDocument.SightingEntry sighting) {
        if (sighting.sourceCollector() == null) {
            return "—";
        }
        return sighting.sourceActor() == null
                ? sighting.sourceCollector()
                : sighting.sourceCollector() + " (" + sighting.sourceActor() + ")";
    }

    private void absent(StringBuilder out, String message) {
        out.append("<p class=\"absent\">").append(escape(message)).append("</p>\n");
    }

    private String iso(Instant instant) {
        return instant == null ? "—" : instant.toString();
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : escape(value);
    }

    /**
     * The one hazard HTML has that Markdown does not.
     *
     * <p>Handover text is written by people and routinely holds shell commands,
     * XML and SQL. Left unescaped, a {@code <} in a rollback procedure does not
     * merely look wrong — it swallows the rest of the document into a tag that
     * never closes, and the reader sees a page that is missing information
     * without any sign that something was lost.
     *
     * <p>Quotes are escaped too, so the same helper is safe if a value ever
     * reaches an attribute.
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
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
