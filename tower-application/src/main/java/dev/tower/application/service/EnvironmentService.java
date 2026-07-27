package dev.tower.application.service;

import dev.tower.application.port.in.EnvironmentUseCases;
import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.promotionpath.PromotionPath;

import java.util.List;
import java.util.Objects;

/**
 * Environment use cases (issue #9).
 *
 * <p>Carries no framework annotation. Spring wiring lives in tower-api, which
 * keeps the application layer free of any framework dependency.
 */
public class EnvironmentService implements EnvironmentUseCases {

    private final EnvironmentRepository environments;
    private final PromotionPathRepository promotionPaths;

    public EnvironmentService(EnvironmentRepository environments, PromotionPathRepository promotionPaths) {
        this.environments = Objects.requireNonNull(environments);
        this.promotionPaths = Objects.requireNonNull(promotionPaths);
    }

    @Override
    public Environment register(RegisterEnvironment command) {
        requireNameAvailable(command.name(), null);
        return environments.save(Environment.create(command.name(), command.stage()));
    }

    @Override
    public Environment update(UpdateEnvironment command) {
        Environment existing = get(command.id());
        requireNameAvailable(command.name(), existing.name());

        // Reclassifying an Environment changes how Release Pack state is derived
        // for every path that references it (ADR-008). That is intended and is
        // why Stage is editable, but it is not a silent change.
        Environment updated = existing.rename(command.name()).reclassify(command.stage());
        return environments.save(updated);
    }

    @Override
    public List<Environment> list() {
        return environments.findAll();
    }

    @Override
    public Environment get(EnvironmentId id) {
        return environments.findById(id)
                .orElseThrow(() -> new NotFoundException("Environment " + id + " does not exist."));
    }

    @Override
    public void delete(EnvironmentId id) {
        Environment environment = get(id);

        // ADR-005 allows many paths to reference one Environment, so this check
        // spans aggregates and belongs here rather than in the domain. Deleting a
        // referenced Environment would rewrite the topology that published
        // versions describe, which ADR-007 exists to prevent.
        List<PromotionPath> referencing = promotionPaths.findAllReferencing(id);
        if (!referencing.isEmpty()) {
            String names = referencing.stream().map(PromotionPath::name).sorted().reduce((a, b) -> a + ", " + b).orElse("");
            throw new ApplicationException("Environment '" + environment.name()
                    + "' is referenced by Promotion Path(s): " + names
                    + ". Remove it from those paths, or archive them, before deleting it.");
        }
        environments.deleteById(id);
    }

    private void requireNameAvailable(String name, String currentName) {
        if (name != null && !name.equalsIgnoreCase(currentName)
                && environments.existsByNameIgnoringCase(name.trim())) {
            throw new ApplicationException("An Environment named '" + name.trim() + "' already exists.");
        }
    }
}
