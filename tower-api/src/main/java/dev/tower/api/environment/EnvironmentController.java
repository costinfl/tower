package dev.tower.api.environment;

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

import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.EnvironmentUseCases.RegisterEnvironment;
import dev.tower.application.port.in.EnvironmentUseCases.UpdateEnvironment;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;

/** Issue #12: REST API for Environment management. */
@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentUseCases environmentUseCases;

    public EnvironmentController(EnvironmentUseCases environmentUseCases) {
        this.environmentUseCases = environmentUseCases;
    }

    @GetMapping
    public List<EnvironmentResponse> list() {
        return environmentUseCases.list().stream().map(EnvironmentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(@Valid @RequestBody EnvironmentRequest request) {
        Environment created = environmentUseCases.register(new RegisterEnvironment(request.name(), request.stage()));
        return EnvironmentResponse.from(created);
    }

    @GetMapping("/{id}")
    public EnvironmentResponse get(@PathVariable("id") String id) {
        return EnvironmentResponse.from(environmentUseCases.get(EnvironmentId.of(id)));
    }

    @PutMapping("/{id}")
    public EnvironmentResponse update(@PathVariable("id") String id, @Valid @RequestBody EnvironmentRequest request) {
        Environment updated = environmentUseCases.update(
                new UpdateEnvironment(EnvironmentId.of(id), request.name(), request.stage()));
        return EnvironmentResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        environmentUseCases.delete(EnvironmentId.of(id));
    }
}
