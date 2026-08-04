package dev.tower.docgen;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Renders a {@link ReleaseDocument} as Markdown (issue #28, FR-024 to FR-030).
 *
 * <p>Markdown is the Milestone 1 format. OQ-009 leaves HTML, PDF and DOCX open,
 * which is why rendering is separated from assembly rather than fused into it.
 *
 * <p>The renderer is a pure function of the document and the template: same
 * inputs, same bytes. That is what makes generated documentation disposable
 * (IA-03) and reproducible, and it is why no generation timestamp appears
 * anywhere below. Whoever regenerates this file tomorrow gets a clean diff, not
 * a spurious one.
 *
 * <p>A {@link DocumentTemplate} chooses which sections appear and in what order
 * (OQ-010, ADR-013). It supplies no markup, so everything written here is still
 * written by this class — which is what keeps the guarantee above true.
 *
 * <p>Absent information is stated, never omitted and never filled in. A release
 * document that quietly leaves out an empty Handover reads as though none was
 * needed; one that says nothing has been prepared tells the truth. That
 * distinction is the difference between a document a team can rely on and one
 * that misleads under pressure. A template extends the same obligation to
 * itself: sections it leaves out are named in the closing note rather than
 * simply not being there.
 */
public class MarkdownReleaseDocumentRenderer {

    private static final String NOT_PREPARED = "_Not prepared._";

    /** The complete document, which is what Tower produces when nobody has chosen. */
    public String render(ReleaseDocument document) {
        return render(document, DocumentTemplate.complete());
    }

    public String render(ReleaseDocument document, DocumentTemplate template) {
        StringBuilder out = new StringBuilder();

        out.append("# Release Pack: ").append(document.packName()).append("\n\n");

        if (!document.description().isBlank()) {
            out.append(document.description()).append("\n\n");
        }

        // Iterated in the template's own order, which is a List for exactly this
        // reason: a collection with unspecified iteration order would make the
        // same template render differently on different runs (NFR-025).
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

        out.append("---\n\n")
                .append("_").append(DocumentProvenance.note(template, "\n")).append("_\n");

        return out.toString();
    }

    private void renderStatus(StringBuilder out, ReleaseDocument document) {
        out.append("| | |\n|---|---|\n");
        out.append("| Observed state | ").append(document.state().name()).append(" |\n");
        out.append("| Lifecycle | ").append(document.archived() ? "Archived" : "Active").append(" |\n\n");

        // ADR-008 keeps these two independent, so the document does too. A pack
        // can be archived and still have reached Production; collapsing them
        // into one line would lose a fact rather than tidy the page.
        out.append("> Observed state is derived from Observations and records where this release has been\n")
                .append("> seen. Lifecycle is a decision by the team and says nothing about deployment.\n\n");
    }

    private void renderPromotionPath(StringBuilder out, Optional<ReleaseDocument.PromotionPathSection> section) {
        out.append("## Promotion Path\n\n");
        if (section.isEmpty()) {
            out.append("_No Promotion Path has been assigned to this Release Pack._\n\n");
            return;
        }
        ReleaseDocument.PromotionPathSection path = section.get();
        out.append("**").append(path.name()).append("** — version ").append(path.versionNumber()).append("\n\n");
        out.append(String.join(" → ", path.environments())).append("\n\n");
        out.append("_This release follows version ").append(path.versionNumber())
                .append(" of the path. Later versions may define a different sequence; this is the\n")
                .append("topology the release was planned against (ADR-007)._\n\n");
    }

    private void renderContents(StringBuilder out, List<ReleaseDocument.ContentEntry> contents) {
        out.append("## Contents\n\n");
        if (contents.isEmpty()) {
            out.append("_No Application Versions have been added to this Release Pack._\n\n");
            return;
        }
        out.append("| Application | Version | Branch | Tag | Commit | Build |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (ReleaseDocument.ContentEntry entry : contents) {
            out.append("| ").append(entry.applicationName())
                    .append(" | ").append(entry.version())
                    .append(" | ").append(orDash(entry.branch()))
                    .append(" | ").append(orDash(entry.tag()))
                    .append(" | ").append(orDash(entry.commit()))
                    .append(" | ").append(orDash(entry.buildIdentifier()))
                    .append(" |\n");
        }
        out.append("\n");
    }

    /**
     * What the release delivers, in the words the team accepted (ADR-018).
     *
     * <p>Identifier and title only. No status: the tracker owns that, and a
     * status printed here would be stale before the page was read.
     */
    /**
     * The digests somebody accepted, and nothing the repository said this
     * morning (ADR-021, FR-086).
     *
     * <p>The empty state is a sentence rather than an empty table, for the same
     * reason every other section here says so: a heading followed by nothing
     * reads as a rendering fault.
     */
    private void renderArtifacts(StringBuilder out, List<ReleaseDocument.ArtifactEntry> artifacts) {
        out.append("## Artifacts\n\n");
        if (artifacts.isEmpty()) {
            out.append("_%s_\n\n".formatted(ReleaseDocument.NO_ARTIFACTS_ACCEPTED));
            return;
        }
        out.append("| Application | Version | Kind | Coordinate | Digest |\n");
        out.append("|---|---|---|---|---|\n");
        for (ReleaseDocument.ArtifactEntry entry : artifacts) {
            out.append("| ").append(entry.applicationName())
                    .append(" | ").append(entry.version())
                    .append(" | ").append(entry.kind())
                    .append(" | `").append(entry.coordinate())
                    .append("` | `").append(entry.digest())
                    .append("` |\n");
        }
        out.append("\n");
    }

    private void renderWorkItems(StringBuilder out, List<ReleaseDocument.WorkItemEntry> workItems) {
        out.append("## Work Items\n\n");
        if (workItems.isEmpty()) {
            out.append("_No work items have been linked to this Release Pack._\n\n");
            return;
        }
        out.append("| Item | Title |\n");
        out.append("|---|---|\n");
        for (ReleaseDocument.WorkItemEntry entry : workItems) {
            out.append("| ").append(entry.identifier())
                    // An identifier with no accepted title says so rather than
                    // leaving a blank cell a reader would take for an oversight.
                    .append(" | ").append(entry.title().isBlank() ? "_no title accepted_" : entry.title())
                    .append(" |\n");
        }
        out.append("\n");
    }

    private void renderHandover(StringBuilder out, ReleaseDocument.HandoverSection handover) {
        out.append("## Handover\n\n");
        if (!handover.prepared()) {
            out.append("_No Handover information has been prepared for this Release Pack._\n\n");
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

    private void section(StringBuilder out, String heading, String body) {
        out.append("### ").append(heading).append("\n\n");
        out.append(body.isBlank() ? NOT_PREPARED : body).append("\n\n");
    }

    private void renderIterations(StringBuilder out, List<ReleaseDocument.IterationEntry> iterations) {
        out.append("## Validation Iterations\n\n");
        if (iterations.isEmpty()) {
            out.append("_No validation Iterations have been recorded against this Release Pack._\n\n");
            return;
        }
        out.append("| Iteration | Started | Completed | Notes |\n|---|---|---|---|\n");
        for (ReleaseDocument.IterationEntry iteration : iterations) {
            out.append("| ").append(iteration.name())
                    .append(" | ").append(iso(iteration.startedAt()))
                    .append(" | ").append(iteration.isComplete() ? iso(iteration.completedAt()) : "_in progress_")
                    .append(" | ").append(oneLine(iteration.notes()))
                    .append(" |\n");
        }
        out.append("\n");
    }

    private void renderSightings(StringBuilder out, List<ReleaseDocument.SightingEntry> sightings) {
        out.append("## Where this release has been observed\n\n");
        if (sightings.isEmpty()) {
            out.append("_This release has not been observed in any Environment._\n\n");
            return;
        }
        // FR-031, FR-032, NFR-011: every claim cites the Observation behind it,
        // so a reader can check the document rather than take it on trust.
        out.append("| Environment | Application | Version | Observed | Source | Observation |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (ReleaseDocument.SightingEntry sighting : sightings) {
            out.append("| ").append(sighting.environmentName())
                    .append(" | ").append(sighting.applicationName())
                    .append(" | ").append(sighting.version())
                    .append(" | ").append(iso(sighting.observedAt()))
                    .append(" | ").append(describeSource(sighting))
                    .append(" | ").append(orDash(sighting.observationId()))
                    .append(" |\n");
        }
        out.append("\n");
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

    /** Keeps free text from breaking the surrounding table. */
    private String oneLine(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.replace("|", "\\|").replaceAll("\\s*\\R\\s*", " ").trim();
    }
}
