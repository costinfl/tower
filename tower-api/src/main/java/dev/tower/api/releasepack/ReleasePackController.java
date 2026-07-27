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

    @PutMapping("/{id}/handover")
    public ReleasePackView updateHandover(@PathVariable("id") String id, @RequestBody HandoverRequest request) {
        Handover handover = new Handover(request.deploymentInstructions(), request.shellCommands(),
                request.databaseMigrations(), request.rollbackProcedure(), request.validationNotes(),
                request.operationalNotes());
        return toView(releasePackUseCases.updateHandover(ReleasePackId.of(id), handover));
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
