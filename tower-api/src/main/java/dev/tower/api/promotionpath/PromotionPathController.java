package dev.tower.api.promotionpath;

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

import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.in.PromotionPathUseCases;
import dev.tower.application.port.in.PromotionPathUseCases.CreatePromotionPath;
import dev.tower.application.port.in.PromotionPathUseCases.PublishVersion;
import dev.tower.application.port.in.PromotionPathUseCases.RenamePromotionPath;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;

/** Issue #12: REST API for Promotion Path management. */
@RestController
@RequestMapping("/api/promotion-paths")
public class PromotionPathController {

    private final PromotionPathUseCases promotionPathUseCases;
    private final EnvironmentUseCases environmentUseCases;

    public PromotionPathController(PromotionPathUseCases promotionPathUseCases, EnvironmentUseCases environmentUseCases) {
        this.promotionPathUseCases = promotionPathUseCases;
        this.environmentUseCases = environmentUseCases;
    }

    @GetMapping
    public List<PathView> list() {
        Function<EnvironmentId, Environment> resolver = environmentResolver();
        return promotionPathUseCases.list().stream().map(path -> PathView.from(path, resolver)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PathView create(@Valid @RequestBody CreatePathRequest request) {
        PromotionPath created = promotionPathUseCases.create(
                new CreatePromotionPath(request.name(), toEnvironmentIds(request.environmentIds())));
        return toPathView(created);
    }

    @GetMapping("/{id}")
    public PathView get(@PathVariable("id") String id) {
        return toPathView(promotionPathUseCases.get(PromotionPathId.of(id)));
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PathView publishVersion(@PathVariable("id") String id, @Valid @RequestBody VersionRequest request) {
        PromotionPath updated = promotionPathUseCases.publishVersion(
                new PublishVersion(PromotionPathId.of(id), toEnvironmentIds(request.environmentIds())));
        return toPathView(updated);
    }

    @PutMapping("/{id}/name")
    public PathView rename(@PathVariable("id") String id, @Valid @RequestBody RenameRequest request) {
        PromotionPath updated = promotionPathUseCases.rename(
                new RenamePromotionPath(PromotionPathId.of(id), request.name()));
        return toPathView(updated);
    }

    @PostMapping("/{id}/archive")
    public PathView archive(@PathVariable("id") String id) {
        return toPathView(promotionPathUseCases.archive(PromotionPathId.of(id)));
    }

    @PostMapping("/{id}/restore")
    public PathView restore(@PathVariable("id") String id) {
        return toPathView(promotionPathUseCases.restore(PromotionPathId.of(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        promotionPathUseCases.delete(PromotionPathId.of(id));
    }

    private PathView toPathView(PromotionPath path) {
        return PathView.from(path, environmentResolver());
    }

    /**
     * Resolves the full set of Environments once per request rather than round-tripping per
     * reference: a Promotion Path version typically references several Environments, and every
     * one of them must be fully resolved (id, name, stage) for the Lane visualisation.
     */
    private Function<EnvironmentId, Environment> environmentResolver() {
        Map<EnvironmentId, Environment> byId = environmentUseCases.list().stream()
                .collect(Collectors.toMap(Environment::id, Function.identity()));
        return byId::get;
    }

    private static List<EnvironmentId> toEnvironmentIds(List<String> ids) {
        return ids.stream().map(EnvironmentId::of).toList();
    }
}
