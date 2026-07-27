package dev.tower.api.application;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.ApplicationUseCases;
import dev.tower.application.port.in.ApplicationUseCases.RegisterVersion;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;

/** Issue #20: REST API for the Application Version registry (gap G2, BR-01). */
@RestController
@RequestMapping("/api/application-versions")
public class ApplicationVersionController {

    private final ApplicationUseCases applicationUseCases;

    public ApplicationVersionController(ApplicationUseCases applicationUseCases) {
        this.applicationUseCases = applicationUseCases;
    }

    @GetMapping
    public List<ApplicationVersionResponse> list() {
        return applicationUseCases.listVersions().stream().map(ApplicationVersionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationVersionResponse create(@Valid @RequestBody ApplicationVersionRequest request) {
        ApplicationVersion created = applicationUseCases.registerVersion(new RegisterVersion(
                ApplicationId.of(request.applicationId()), request.version(), request.branch(), request.tag(),
                request.commit(), request.buildIdentifier()));
        return ApplicationVersionResponse.from(created);
    }

    @GetMapping("/{id}")
    public ApplicationVersionResponse get(@PathVariable("id") String id) {
        return ApplicationVersionResponse.from(applicationUseCases.getVersion(ApplicationVersionId.of(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        applicationUseCases.deleteVersion(ApplicationVersionId.of(id));
    }
}
