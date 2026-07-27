package dev.tower.application.port.out;

import dev.tower.domain.environment.Environment;
import dev.tower.domain.environment.EnvironmentId;

import java.util.List;
import java.util.Optional;

/** Outbound port for Environment storage. Implemented by an adapter module. */
public interface EnvironmentRepository {

    Environment save(Environment environment);

    Optional<Environment> findById(EnvironmentId id);

    List<Environment> findAll();

    List<Environment> findAllById(List<EnvironmentId> ids);

    boolean existsByNameIgnoringCase(String name);

    void deleteById(EnvironmentId id);
}
