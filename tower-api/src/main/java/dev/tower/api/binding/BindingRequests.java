package dev.tower.api.binding;

import jakarta.validation.constraints.NotBlank;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
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
}
