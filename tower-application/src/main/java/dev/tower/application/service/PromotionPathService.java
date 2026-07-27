package dev.tower.application.service;

import dev.tower.application.port.in.PromotionPathUseCases;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;
import dev.tower.domain.promotionpath.PromotionPathId;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Promotion Path use cases (issues #10, #11).
 *
 * <p>Carries no framework annotation. Spring wiring lives in tower-api.
 */
public class PromotionPathService implements PromotionPathUseCases {

    private final PromotionPathRepository promotionPaths;
    private final EnvironmentRepository environments;
    private final Clock clock;

    public PromotionPathService(PromotionPathRepository promotionPaths,
                                EnvironmentRepository environments,
                                Clock clock) {
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
        this.environments = Objects.requireNonNull(environments);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PromotionPath create(CreatePromotionPath command) {
        if (promotionPaths.existsByNameIgnoringCase(command.name() == null ? "" : command.name().trim())) {
            throw new ApplicationException("A Promotion Path named '" + command.name() + "' already exists.");
        }
        requireEnvironmentsExist(command.environments());
        return promotionPaths.save(
                PromotionPath.create(command.name(), command.environments(), clock.instant()));
    }

    @Override
    public PromotionPath publishVersion(PublishVersion command) {
        PromotionPath path = get(command.id());
        requireEnvironmentsExist(command.environments());

        // The domain refuses a new version on an archived path and enforces
        // uniqueness within the sequence; this layer only supplies the clock.
        return promotionPaths.save(path.withNewVersion(command.environments(), clock.instant()));
    }

    @Override
    public PromotionPath rename(RenamePromotionPath command) {
        PromotionPath path = get(command.id());
        String newName = command.name() == null ? "" : command.name().trim();
        if (!newName.equalsIgnoreCase(path.name()) && promotionPaths.existsByNameIgnoringCase(newName)) {
            throw new ApplicationException("A Promotion Path named '" + newName + "' already exists.");
        }
        return promotionPaths.save(path.rename(command.name()));
    }

    @Override
    public PromotionPath archive(PromotionPathId id) {
        return promotionPaths.save(get(id).archive());
    }

    @Override
    public PromotionPath restore(PromotionPathId id) {
        return promotionPaths.save(get(id).restore());
    }

    @Override
    public void delete(PromotionPathId id) {
        PromotionPath path = get(id);

        // ADR-007 permits outright deletion only while nothing references the
        // path. Release Packs arrive in Epic 2; until then the only history a
        // path can carry is its own published versions, so a path that has been
        // edited or archived is kept rather than dropped.
        if (path.isArchived()) {
            throw new ApplicationException("Promotion Path '" + path.name()
                    + "' is archived and is kept for historical reference. It cannot be deleted.");
        }
        if (path.versions().size() > 1) {
            throw new ApplicationException("Promotion Path '" + path.name() + "' has "
                    + path.versions().size() + " published versions. Archive it instead of deleting it,"
                    + " so the topology earlier versions describe is preserved.");
        }
        promotionPaths.deleteById(id);
    }

    @Override
    public List<PromotionPath> list() {
        return promotionPaths.findAll();
    }

    @Override
    public PromotionPath get(PromotionPathId id) {
        return promotionPaths.findById(id)
                .orElseThrow(() -> new NotFoundException("Promotion Path " + id + " does not exist."));
    }

    /**
     * A Promotion Path references Environments it does not own (ADR-005), so the
     * references must be checked here rather than in the domain, which cannot
     * reach storage.
     */
    private void requireEnvironmentsExist(List<EnvironmentId> requested) {
        if (requested == null || requested.isEmpty()) {
            return; // The domain reports the empty case with a clearer message.
        }
        Set<EnvironmentId> found = environments.findAllById(requested).stream()
                .map(Environment::id)
                .collect(Collectors.toSet());
        List<EnvironmentId> missing = requested.stream()
                .filter(id -> id != null && !found.contains(id))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new ApplicationException("Promotion Path references unknown Environment(s): "
                    + missing.stream().map(EnvironmentId::toString).collect(Collectors.joining(", ")));
        }
    }
}
