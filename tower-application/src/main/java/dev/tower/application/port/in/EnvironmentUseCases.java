package dev.tower.application.port.in;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;
import dev.tower.domain.environment.Stage;

import java.util.List;

/**
 * Inbound port for managing Environments (issue #9, gap G1).
 *
 * <p>The Functional Requirements presume Environments exist (FR-010 to FR-013)
 * without ever specifying their creation. G1 recorded that gap; this port fills
 * it.
 */
public interface EnvironmentUseCases {

    Environment register(RegisterEnvironment command);

    Environment update(UpdateEnvironment command);

    List<Environment> list();

    Environment get(EnvironmentId id);

    /**
     * Deletes an Environment.
     *
     * <p>Refused when any Promotion Path references it, in any version. Removing
     * it would silently rewrite the topology those versions describe, which
     * ADR-007 exists to prevent.
     */
    void delete(EnvironmentId id);

    record RegisterEnvironment(String name, Stage stage) {}

    record UpdateEnvironment(EnvironmentId id, String name, Stage stage) {}
}
