package dev.tower.api.binding;

import jakarta.validation.constraints.NotBlank;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.ArtifactCoordinateBinding;
import dev.tower.application.binding.BuildJobBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.PipelineJobBinding;
import dev.tower.application.binding.RepositoryBinding;

/** Request and response bodies for /api/bindings (issue #47). */
public final class BindingRequests {

    private BindingRequests() {
    }

    public record EnvironmentBindingRequest(
            @NotBlank(message = "environmentId is required") String environmentId,
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "target is required") String target,
            @NotBlank(message = "scope is required") String scope) {
    }

    /**
     * {@code versionPattern} is optional: omitting it means the whole tag is the
     * Application Version, which is what most teams want and what
     * {@link ApplicationBinding#WHOLE_TAG} expresses.
     */
    public record ApplicationBindingRequest(
            @NotBlank(message = "applicationId is required") String applicationId,
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "image is required") String image,
            String versionPattern) {
    }

    public record EnvironmentBindingResponse(
            String environmentId, String connectorId, String target, String scope) {

        public static EnvironmentBindingResponse from(EnvironmentBinding binding) {
            return new EnvironmentBindingResponse(
                    binding.environmentId().value().toString(),
                    binding.connectorId(), binding.target(), binding.scope());
        }
    }

    public record ApplicationBindingResponse(
            String applicationId, String connectorId, String image, String versionPattern) {

        public static ApplicationBindingResponse from(ApplicationBinding binding) {
            return new ApplicationBindingResponse(
                    binding.applicationId().value().toString(),
                    binding.connectorId(), binding.image(), binding.versionPattern());
        }
    }

    /**
     * {@code refSelection} and {@code versionPattern} are both optional: tags
     * only, and the whole ref name as the version, are what most teams want and
     * what {@link RepositoryBinding} defaults to.
     */
    public record RepositoryBindingRequest(
            @NotBlank(message = "applicationId is required") String applicationId,
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "repositoryUrl is required") String repositoryUrl,
            String refSelection,
            String versionPattern) {
    }

    public record RepositoryBindingResponse(
            String applicationId, String connectorId, String repositoryUrl,
            String refSelection, String versionPattern) {

        public static RepositoryBindingResponse from(RepositoryBinding binding) {
            return new RepositoryBindingResponse(
                    binding.applicationId().value().toString(),
                    binding.connectorId(), binding.repositoryUrl(),
                    binding.refSelection().name(), binding.versionPattern());
        }
    }

    /**
     * Where the team's work items live (ADR-018).
     *
     * <p>Names no Tower concept, unlike every request above it: a work item
     * belongs to a release rather than to one Application, and a team has one
     * tracker, so the tracker is named once per Connector.
     *
     * <p>{@code locator} is whatever that Connector expects —
     * {@code owner/repository} for GitHub Issues. Not validated for shape here:
     * only the Connector knows what shape is right, and a rule guessed at this
     * layer would reject a form some future tracker uses.
     */
    public record IssueTrackerBindingRequest(
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "locator is required") String locator) {
    }

    public record IssueTrackerBindingResponse(String connectorId, String locator) {

        public static IssueTrackerBindingResponse from(IssueTrackerBinding binding) {
            return new IssueTrackerBindingResponse(binding.connectorId(), binding.locator());
        }
    }

    /**
     * Which job's runs say an Application reached an Environment (ADR-020).
     *
     * <p>{@code versionSource}, {@code versionKey} and {@code versionPattern} are
     * all optional, and default to reading a build parameter whole. That is the
     * arrangement a CI system used well has; the others exist because most are
     * not used well, and Tower cannot infer which.
     */
    public record PipelineJobBindingRequest(
            @NotBlank(message = "environmentId is required") String environmentId,
            @NotBlank(message = "applicationId is required") String applicationId,
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "system is required") String system,
            @NotBlank(message = "job is required") String job,
            String versionSource,
            String versionKey,
            String versionPattern) {
    }

    /**
     * Which job's runs build an Application (ADR-020).
     *
     * <p>Names no Environment, and that absence is the design. A build says what
     * was produced, not where it went, so what a run of this yields is a
     * candidate Application Version rather than an Observation.
     *
     * <p>{@code job} is part of the key rather than merely a field: two build
     * jobs for one Application are ordinary, where two deployment jobs for one
     * Environment and Application are refused.
     */
    public record BuildJobBindingRequest(
            @NotBlank(message = "applicationId is required") String applicationId,
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "system is required") String system,
            @NotBlank(message = "job is required") String job,
            String versionSource,
            String versionKey,
            String versionPattern) {
    }

    public record BuildJobBindingResponse(
            String applicationId, String connectorId, String system, String job,
            String versionSource, String versionKey, String versionPattern) {

        public static BuildJobBindingResponse from(BuildJobBinding binding) {
            return new BuildJobBindingResponse(
                    binding.applicationId().value().toString(),
                    binding.connectorId(), binding.system(), binding.job(),
                    binding.versionSource().name(), binding.versionKey(),
                    binding.versionPattern());
        }
    }

    /**
     * How to address one kind of artifact an Application Version produced
     * (ADR-021, FR-083).
     *
     * <p>{@code kind} is the team's own word and is never interpreted — "image",
     * "chart", "installer". {@code shortCommitLength} may be omitted, and 0 means
     * the default of seven.
     *
     * <p>{@code coordinateTemplate} composes where every other binding's
     * {@code versionPattern} extracts. That is deliberate and is the one thing
     * about this request worth reading twice: a pattern turns a vendor's string
     * into a version, a template turns a version into a vendor's string.
     */
    public record ArtifactCoordinateBindingRequest(
            @NotBlank(message = "applicationId is required") String applicationId,
            @NotBlank(message = "connectorId is required") String connectorId,
            @NotBlank(message = "kind is required") String kind,
            @NotBlank(message = "system is required") String system,
            @NotBlank(message = "coordinateTemplate is required") String coordinateTemplate,
            int shortCommitLength) {
    }

    public record ArtifactCoordinateBindingResponse(
            String applicationId, String connectorId, String kind, String system,
            String coordinateTemplate, int shortCommitLength) {

        public static ArtifactCoordinateBindingResponse from(ArtifactCoordinateBinding binding) {
            return new ArtifactCoordinateBindingResponse(
                    binding.applicationId().value().toString(),
                    binding.connectorId(), binding.kind(), binding.system(),
                    binding.coordinateTemplate(), binding.shortCommitLength());
        }
    }

    public record PipelineJobBindingResponse(
            String environmentId, String applicationId, String connectorId,
            String system, String job, String versionSource, String versionKey,
            String versionPattern) {

        public static PipelineJobBindingResponse from(PipelineJobBinding binding) {
            return new PipelineJobBindingResponse(
                    binding.environmentId().value().toString(),
                    binding.applicationId().value().toString(),
                    binding.connectorId(), binding.system(), binding.job(),
                    binding.versionSource().name(), binding.versionKey(),
                    binding.versionPattern());
        }
    }
}
