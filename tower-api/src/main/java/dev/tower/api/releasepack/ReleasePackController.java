package dev.tower.api.releasepack;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.ApplicationUseCases;
import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.PromotionPathUseCases;
import dev.tower.application.port.in.ReleasePackUseCases;
import dev.tower.application.port.in.ReleasePackUseCases.AssignPromotionPath;
import dev.tower.application.port.in.ReleasePackUseCases.CreateReleasePack;
import dev.tower.application.port.in.ReleasePackUseCases.StartIteration;
import dev.tower.application.port.in.ReleasePackUseCases.UpdateReleasePack;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.handover.HandoverRevision;
import dev.tower.domain.iteration.IterationId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;

/** Issue #20: REST API for Release Pack management (issues #15 to #19, the central business concept of ADR-004). */
@RestController
@RequestMapping("/api/release-packs")
public class ReleasePackController {

    private final ReleasePackUseCases releasePackUseCases;
    private final ApplicationUseCases applicationUseCases;
    private final PromotionPathUseCases promotionPathUseCases;
    private final EnvironmentUseCases environmentUseCases;

    public ReleasePackController(ReleasePackUseCases releasePackUseCases, ApplicationUseCases applicationUseCases,
                                 PromotionPathUseCases promotionPathUseCases, EnvironmentUseCases environmentUseCases) {
        this.releasePackUseCases = releasePackUseCases;
        this.applicationUseCases = applicationUseCases;
        this.promotionPathUseCases = promotionPathUseCases;
        this.environmentUseCases = environmentUseCases;
    }

    @GetMapping
    public List<ReleasePackView> list() {
        return toViews(releasePackUseCases.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleasePackView create(@Valid @RequestBody ReleasePackRequest request) {
        ReleasePack created = releasePackUseCases.create(new CreateReleasePack(request.name(), request.description()));
        return toView(created);
    }

    @GetMapping("/{id}")
    public ReleasePackView get(@PathVariable("id") String id) {
        return toView(releasePackUseCases.get(ReleasePackId.of(id)));
    }

    @PutMapping("/{id}")
    public ReleasePackView update(@PathVariable("id") String id, @Valid @RequestBody ReleasePackRequest request) {
        ReleasePack updated = releasePackUseCases.updateMetadata(
                new UpdateReleasePack(ReleasePackId.of(id), request.name(), request.description()));
        return toView(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        releasePackUseCases.delete(ReleasePackId.of(id));
    }

    @PutMapping("/{id}/promotion-path")
    public ReleasePackView assignPromotionPath(
            @PathVariable("id") String id, @Valid @RequestBody AssignPromotionPathRequest request) {
        ReleasePack updated = releasePackUseCases.assignPromotionPath(new AssignPromotionPath(
                ReleasePackId.of(id), PromotionPathId.of(request.pathId()), request.versionNumber()));
        return toView(updated);
    }

    @DeleteMapping("/{id}/promotion-path")
    public ReleasePackView clearPromotionPath(@PathVariable("id") String id) {
        return toView(releasePackUseCases.clearPromotionPath(ReleasePackId.of(id)));
    }

    @PostMapping("/{id}/versions/{versionId}")
    public ReleasePackView addApplicationVersion(
            @PathVariable("id") String id, @PathVariable("versionId") String versionId) {
        ReleasePack updated = releasePackUseCases.addApplicationVersion(
                ReleasePackId.of(id), ApplicationVersionId.of(versionId));
        return toView(updated);
    }

    @DeleteMapping("/{id}/versions/{versionId}")
    public ReleasePackView removeApplicationVersion(
            @PathVariable("id") String id, @PathVariable("versionId") String versionId) {
        ReleasePack updated = releasePackUseCases.removeApplicationVersion(
                ReleasePackId.of(id), ApplicationVersionId.of(versionId));
        return toView(updated);
    }

    /**
     * States that this release delivers a work item (ADR-018).
     *
     * <p>The identifier travels in the body rather than the path: a tracker key
     * may contain a slash or a hash, and a path variable would have to be
     * escaped by every caller and unescaped here for no benefit.
     */
    @PostMapping("/{id}/work-items")
    public ReleasePackView linkWorkItem(
            @PathVariable("id") String id, @Valid @RequestBody WorkItemRequest request) {
        return toView(releasePackUseCases.linkWorkItem(
                ReleasePackId.of(id), request.identifier(), request.title()));
    }

    /**
     * Takes the tracker's current title as Tower's own.
     *
     * <p>An explicit act, never automatic. Nothing refreshes a title on its own,
     * which is what keeps a generated document reproducible (NFR-025).
     */
    @PutMapping("/{id}/work-items/title")
    public ReleasePackView acceptWorkItemTitle(
            @PathVariable("id") String id, @Valid @RequestBody WorkItemRequest request) {
        return toView(releasePackUseCases.acceptWorkItemTitle(
                ReleasePackId.of(id), request.identifier(), request.title()));
    }

    /** Withdraws the claim that this release delivers a work item. */
    @DeleteMapping("/{id}/work-items")
    public ReleasePackView unlinkWorkItem(
            @PathVariable("id") String id, @RequestParam("identifier") String identifier) {
        return toView(releasePackUseCases.unlinkWorkItem(ReleasePackId.of(id), identifier));
    }

    /**
     * @param title may be empty: a reference can be linked before any Connector is
     *              configured, or to a tracker Tower cannot reach
     */
    public record WorkItemRequest(
            @jakarta.validation.constraints.NotBlank(message = "identifier is required")
            String identifier,
            String title) {
    }

    @PutMapping("/{id}/handover")
    public ReleasePackView updateHandover(@PathVariable("id") String id, @RequestBody HandoverRequest request) {
        Handover handover = new Handover(request.deploymentInstructions(), request.shellCommands(),
                request.databaseMigrations(), request.rollbackProcedure(), request.validationNotes(),
                request.operationalNotes());
        return toView(releasePackUseCases.updateHandover(ReleasePackId.of(id), handover));
    }

    /**
     * Every version this Release Pack's Handover has had, newest first (ADR-016).
     *
     * <p>A {@code GET} and nothing else. There is deliberately no endpoint that
     * restores a revision: putting an old Handover back is an edit like any
     * other, made through {@code PUT /handover}, and it appends a new revision
     * rather than rewriting history.
     */
    @GetMapping("/{id}/handover/history")
    public List<HandoverRevisionView> handoverHistory(@PathVariable("id") String id) {
        List<HandoverRevision> revisions = releasePackUseCases.handoverHistory(ReleasePackId.of(id));

        // Newest first, so the first row is what the Release Pack holds now.
        // Marked rather than left for the reader to infer from the ordering: a
        // list where "current" is a position rather than a fact is one a client
        // can sort and quietly get wrong.
        List<HandoverRevisionView> views = new java.util.ArrayList<>(revisions.size());
        for (int i = 0; i < revisions.size(); i++) {
            views.add(HandoverRevisionView.from(revisions.get(i), i == 0));
        }
        return List.copyOf(views);
    }

    /**
     * @param revisionNumber how a revision is referred to, rising from 1
     * @param current        whether this is what the Release Pack holds now, so a
     *                       reader does not have to infer it from the ordering
     */
    public record HandoverRevisionView(
            String id, int revisionNumber, String recordedAt, boolean current, boolean empty,
            String deploymentInstructions, String shellCommands, String databaseMigrations,
            String rollbackProcedure, String validationNotes, String operationalNotes) {

        static HandoverRevisionView from(HandoverRevision revision, boolean current) {
            Handover handover = revision.handover();
            return new HandoverRevisionView(
                    revision.id().toString(), revision.revisionNumber(),
                    revision.recordedAt().toString(), current, revision.isEmpty(),
                    handover.deploymentInstructions(), handover.shellCommands(),
                    handover.databaseMigrations(), handover.rollbackProcedure(),
                    handover.validationNotes(), handover.operationalNotes());
        }
    }

    @PostMapping("/{id}/iterations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReleasePackView startIteration(@PathVariable("id") String id, @Valid @RequestBody StartIterationRequest request) {
        ReleasePack updated = releasePackUseCases.startIteration(
                new StartIteration(ReleasePackId.of(id), request.name(), request.startedAt(), request.notes()));
        return toView(updated);
    }

    @PostMapping("/{id}/iterations/{iterationId}/complete")
    public ReleasePackView completeIteration(
            @PathVariable("id") String id, @PathVariable("iterationId") String iterationId,
            @RequestBody(required = false) CompleteIterationRequest request) {
        var completedAt = request == null ? null : request.completedAt();
        ReleasePack updated = releasePackUseCases.completeIteration(
                ReleasePackId.of(id), IterationId.of(iterationId), completedAt);
        return toView(updated);
    }

    @PostMapping("/{id}/iterations/{iterationId}/reopen")
    public ReleasePackView reopenIteration(
            @PathVariable("id") String id, @PathVariable("iterationId") String iterationId) {
        ReleasePack updated = releasePackUseCases.reopenIteration(ReleasePackId.of(id), IterationId.of(iterationId));
        return toView(updated);
    }

    @PutMapping("/{id}/iterations/{iterationId}/notes")
    public ReleasePackView updateIterationNotes(
            @PathVariable("id") String id, @PathVariable("iterationId") String iterationId,
            @RequestBody IterationNotesRequest request) {
        ReleasePack updated = releasePackUseCases.updateIterationNotes(
                ReleasePackId.of(id), IterationId.of(iterationId), request.notes());
        return toView(updated);
    }

    @DeleteMapping("/{id}/iterations/{iterationId}")
    public ReleasePackView removeIteration(
            @PathVariable("id") String id, @PathVariable("iterationId") String iterationId) {
        ReleasePack updated = releasePackUseCases.removeIteration(ReleasePackId.of(id), IterationId.of(iterationId));
        return toView(updated);
    }

    @PostMapping("/{id}/archive")
    public ReleasePackView archive(@PathVariable("id") String id) {
        return toView(releasePackUseCases.archive(ReleasePackId.of(id)));
    }

    @PostMapping("/{id}/restore")
    public ReleasePackView restore(@PathVariable("id") String id) {
        return toView(releasePackUseCases.restore(ReleasePackId.of(id)));
    }

    private ReleasePackView toView(ReleasePack pack) {
        return ReleasePackView.from(pack, applicationResolver(), versionResolver(), promotionPathResolver(),
                environmentResolver());
    }

    private List<ReleasePackView> toViews(List<ReleasePack> packs) {
        // Resolvers are built once per request rather than once per pack: every pack in the list
        // typically draws from the same small pools of Applications, Versions, Promotion Paths
        // and Environments, so this avoids re-querying those use cases per row.
        Function<ApplicationId, Application> applications = applicationResolver();
        Function<ApplicationVersionId, ApplicationVersion> versions = versionResolver();
        Function<PromotionPathId, PromotionPath> promotionPaths = promotionPathResolver();
        Function<EnvironmentId, Environment> environments = environmentResolver();
        return packs.stream()
                .map(pack -> ReleasePackView.from(pack, applications, versions, promotionPaths, environments))
                .toList();
    }

    private Function<ApplicationId, Application> applicationResolver() {
        Map<ApplicationId, Application> byId = applicationUseCases.list().stream()
                .collect(Collectors.toMap(Application::id, Function.identity()));
        return byId::get;
    }

    private Function<ApplicationVersionId, ApplicationVersion> versionResolver() {
        Map<ApplicationVersionId, ApplicationVersion> byId = applicationUseCases.listVersions().stream()
                .collect(Collectors.toMap(ApplicationVersion::id, Function.identity()));
        return byId::get;
    }

    private Function<PromotionPathId, PromotionPath> promotionPathResolver() {
        Map<PromotionPathId, PromotionPath> byId = promotionPathUseCases.list().stream()
                .collect(Collectors.toMap(PromotionPath::id, Function.identity()));
        return byId::get;
    }

    private Function<EnvironmentId, Environment> environmentResolver() {
        Map<EnvironmentId, Environment> byId = environmentUseCases.list().stream()
                .collect(Collectors.toMap(Environment::id, Function.identity()));
        return byId::get;
    }
}
