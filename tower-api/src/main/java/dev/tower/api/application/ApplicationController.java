package dev.tower.api.application;

import java.util.List;

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
import dev.tower.application.port.in.ApplicationUseCases.RegisterApplication;
import dev.tower.application.port.in.ApplicationUseCases.UpdateApplication;
import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;

/** Issue #20: REST API for the Application registry (gap G2). */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationUseCases applicationUseCases;

    public ApplicationController(ApplicationUseCases applicationUseCases) {
        this.applicationUseCases = applicationUseCases;
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return applicationUseCases.list().stream().map(ApplicationResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody ApplicationRequest request) {
        Application created = applicationUseCases.register(
                new RegisterApplication(request.name(), request.description()));
        return ApplicationResponse.from(created);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable("id") String id) {
        return ApplicationResponse.from(applicationUseCases.get(ApplicationId.of(id)));
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable("id") String id, @Valid @RequestBody ApplicationRequest request) {
        Application updated = applicationUseCases.update(
                new UpdateApplication(ApplicationId.of(id), request.name(), request.description()));
        return ApplicationResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        applicationUseCases.delete(ApplicationId.of(id));
    }

    @GetMapping("/{id}/versions")
    public List<ApplicationVersionResponse> versions(@PathVariable("id") String id) {
        return applicationUseCases.listVersionsOf(ApplicationId.of(id)).stream()
                .map(ApplicationVersionResponse::from)
                .toList();
    }
}
