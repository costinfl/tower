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
import dev.tower.api.binding.BindingRequests.EnvironmentBindingRequest;
import dev.tower.api.binding.BindingRequests.EnvironmentBindingResponse;
import dev.tower.application.port.in.ExternalBindingUseCases;
import dev.tower.application.port.in.ExternalBindingUseCases.BindApplication;
import dev.tower.application.port.in.ExternalBindingUseCases.BindEnvironment;
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
