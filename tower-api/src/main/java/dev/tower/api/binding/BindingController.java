package dev.tower.api.binding;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.api.binding.BindingRequests.ApplicationBindingRequest;
import dev.tower.api.binding.BindingRequests.ApplicationBindingResponse;
import dev.tower.api.binding.BindingRequests.ArtifactCoordinateBindingRequest;
import dev.tower.api.binding.BindingRequests.ArtifactCoordinateBindingResponse;
import dev.tower.api.binding.BindingRequests.EnvironmentBindingRequest;
import dev.tower.api.binding.BindingRequests.EnvironmentBindingResponse;
import dev.tower.api.binding.BindingRequests.IssueTrackerBindingRequest;
import dev.tower.api.binding.BindingRequests.IssueTrackerBindingResponse;
import dev.tower.api.binding.BindingRequests.PipelineJobBindingRequest;
import dev.tower.api.binding.BindingRequests.PipelineJobBindingResponse;
import dev.tower.api.binding.BindingRequests.RepositoryBindingRequest;
import dev.tower.api.binding.BindingRequests.RepositoryBindingResponse;
import dev.tower.application.port.in.ExternalBindingUseCases;
import dev.tower.application.port.in.ExternalBindingUseCases.BindApplication;
import dev.tower.application.port.in.ExternalBindingUseCases.BindArtifactCoordinate;
import dev.tower.application.port.in.ExternalBindingUseCases.CoordinatePreview;
import dev.tower.application.port.in.ExternalBindingUseCases.BindEnvironment;
import dev.tower.application.port.in.ExternalBindingUseCases.BindIssueTracker;
import dev.tower.application.port.in.ExternalBindingUseCases.BindPipelineJob;
import dev.tower.application.port.in.ExternalBindingUseCases.BindRepository;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.application.port.in.ExternalBindingUseCases.VersionPreview;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;

/**
 * Issue #47: REST API for External Bindings (ADR-012, FR-055, FR-056).
 *
 * <p>Binding uses {@code PUT} rather than {@code POST} because a binding is
 * identified by the pair it describes rather than by an id Tower generates.
 * Re-pointing an Environment at a different namespace replaces the binding, and
 * repeating the same call changes nothing further.
 */
@RestController
@RequestMapping("/api/bindings")
public class BindingController {

    private final ExternalBindingUseCases bindings;

    public BindingController(ExternalBindingUseCases bindings) {
        this.bindings = bindings;
    }

    @GetMapping("/environments")
    public List<EnvironmentBindingResponse> listEnvironmentBindings() {
        return bindings.listEnvironmentBindings().stream().map(EnvironmentBindingResponse::from).toList();
    }

    @PutMapping("/environments")
    public EnvironmentBindingResponse bindEnvironment(@Valid @RequestBody EnvironmentBindingRequest request) {
        return EnvironmentBindingResponse.from(bindings.bindEnvironment(new BindEnvironment(
                EnvironmentId.of(request.environmentId()), request.connectorId(),
                request.target(), request.scope())));
    }

    @DeleteMapping("/environments/{environmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindEnvironment(
            @PathVariable("environmentId") String environmentId,
            @RequestParam("connectorId") String connectorId) {
        bindings.unbindEnvironment(EnvironmentId.of(environmentId), connectorId);
    }

    /**
     * Where an Application's code lives (issue #3, ADR-014).
     *
     * <p>Separate from the Application binding above rather than a field on it:
     * one says which running image is this Application, the other says where its
     * source is, and a team may configure either without the other.
     */
    @GetMapping("/repositories")
    public List<RepositoryBindingResponse> listRepositoryBindings() {
        return bindings.listRepositoryBindings().stream().map(RepositoryBindingResponse::from).toList();
    }

    @PutMapping("/repositories")
    public RepositoryBindingResponse bindRepository(@Valid @RequestBody RepositoryBindingRequest request) {
        return RepositoryBindingResponse.from(bindings.bindRepository(new BindRepository(
                ApplicationId.of(request.applicationId()), request.connectorId(), request.repositoryUrl(),
                RepositoryBinding.RefSelection.parse(request.refSelection()), request.versionPattern())));
    }

    @DeleteMapping("/repositories/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindRepository(
            @PathVariable("applicationId") String applicationId,
            @RequestParam("connectorId") String connectorId) {
        bindings.unbindRepository(ApplicationId.of(applicationId), connectorId);
    }

    /**
     * Where the team's work items live (ADR-018).
     *
     * <p>Keyed by Connector alone, so the path carries a connector id where the
     * others carry a Tower id. That asymmetry is the design rather than an
     * oversight — ADR-018 records why a tracker is not bound per Application.
     */
    @GetMapping("/issue-trackers")
    public List<IssueTrackerBindingResponse> listIssueTrackerBindings() {
        return bindings.listIssueTrackerBindings().stream().map(IssueTrackerBindingResponse::from).toList();
    }

    @PutMapping("/issue-trackers")
    public IssueTrackerBindingResponse bindIssueTracker(
            @Valid @RequestBody IssueTrackerBindingRequest request) {
        return IssueTrackerBindingResponse.from(bindings.bindIssueTracker(
                new BindIssueTracker(request.connectorId(), request.locator())));
    }

    @DeleteMapping("/issue-trackers/{connectorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindIssueTracker(@PathVariable("connectorId") String connectorId) {
        bindings.unbindIssueTracker(connectorId);
    }

    /**
     * Which job's runs say an Application reached an Environment (ADR-020).
     *
     * <p>The only binding whose path carries two Tower ids, because it is the
     * only one that maps two concepts at once.
     */
    @GetMapping("/pipeline-jobs")
    public List<PipelineJobBindingResponse> listPipelineJobBindings() {
        return bindings.listPipelineJobBindings().stream()
                .map(PipelineJobBindingResponse::from).toList();
    }

    @PutMapping("/pipeline-jobs")
    public PipelineJobBindingResponse bindPipelineJob(
            @Valid @RequestBody PipelineJobBindingRequest request) {
        return PipelineJobBindingResponse.from(bindings.bindPipelineJob(new BindPipelineJob(
                EnvironmentId.of(request.environmentId()),
                ApplicationId.of(request.applicationId()),
                request.connectorId(), request.system(), request.job(),
                request.versionSource(), request.versionKey(), request.versionPattern())));
    }

    @DeleteMapping("/pipeline-jobs/{environmentId}/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindPipelineJob(
            @PathVariable("environmentId") String environmentId,
            @PathVariable("applicationId") String applicationId,
            @RequestParam("connectorId") String connectorId) {
        bindings.unbindPipelineJob(EnvironmentId.of(environmentId),
                ApplicationId.of(applicationId), connectorId);
    }

    /**
     * How to address the artifacts an Application Version produced (ADR-021).
     *
     * <p>The only binding an Application may have several of for one Connector,
     * so the delete path carries a kind as well. A build publishes an image and a
     * chart, and nothing but the kind tells the two templates apart.
     */
    @GetMapping("/artifact-coordinates")
    public List<ArtifactCoordinateBindingResponse> listArtifactCoordinateBindings() {
        return bindings.listArtifactCoordinateBindings().stream()
                .map(ArtifactCoordinateBindingResponse::from).toList();
    }

    @PutMapping("/artifact-coordinates")
    public ArtifactCoordinateBindingResponse bindArtifactCoordinate(
            @Valid @RequestBody ArtifactCoordinateBindingRequest request) {
        return ArtifactCoordinateBindingResponse.from(bindings.bindArtifactCoordinate(
                new BindArtifactCoordinate(
                        ApplicationId.of(request.applicationId()), request.connectorId(),
                        request.kind(), request.system(), request.coordinateTemplate(),
                        request.shortCommitLength())));
    }

    @DeleteMapping("/artifact-coordinates/{applicationId}/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindArtifactCoordinate(
            @PathVariable("applicationId") String applicationId,
            @PathVariable("kind") String kind,
            @RequestParam("connectorId") String connectorId) {
        bindings.unbindArtifactCoordinate(ApplicationId.of(applicationId), connectorId, kind);
    }

    /**
     * Tries a coordinate template against a version and commit without saving
     * anything.
     *
     * <p>The counterpart of {@code /version-preview}, and useful for a milder
     * reason: a wrong template produces a false "not found" rather than wrong
     * Observations (ADR-021). This is how somebody tells false absence from real
     * absence before going to look in the repository for something that was never
     * addressed correctly.
     */
    @GetMapping("/coordinate-preview")
    public CoordinatePreview previewCoordinate(
            @RequestParam("coordinateTemplate") String coordinateTemplate,
            @RequestParam(name = "shortCommitLength", defaultValue = "0") int shortCommitLength,
            @RequestParam("version") String version,
            @RequestParam(name = "commit", required = false) String commit) {
        return bindings.previewCoordinate(coordinateTemplate, shortCommitLength, version, commit);
    }

    @GetMapping("/applications")
    public List<ApplicationBindingResponse> listApplicationBindings() {
        return bindings.listApplicationBindings().stream().map(ApplicationBindingResponse::from).toList();
    }

    @PutMapping("/applications")
    public ApplicationBindingResponse bindApplication(@Valid @RequestBody ApplicationBindingRequest request) {
        return ApplicationBindingResponse.from(bindings.bindApplication(new BindApplication(
                ApplicationId.of(request.applicationId()), request.connectorId(),
                request.image(), request.versionPattern())));
    }

    @DeleteMapping("/applications/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindApplication(
            @PathVariable("applicationId") String applicationId,
            @RequestParam("connectorId") String connectorId) {
        bindings.unbindApplication(ApplicationId.of(applicationId), connectorId);
    }

    /**
     * Tries a version pattern against a tag without saving anything.
     *
     * <p>ADR-012 records that a wrong pattern yields wrong Application Versions
     * and that, because Observations are immutable, those outlive the
     * correction. This endpoint exists so the mistake can be caught before it
     * becomes permanent.
     */
    @GetMapping("/version-preview")
    public VersionPreview previewVersion(
            @RequestParam(name = "versionPattern", required = false) String versionPattern,
            @RequestParam("imageTag") String imageTag) {
        return bindings.previewVersion(versionPattern, imageTag);
    }
}
