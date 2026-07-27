package dev.tower.application.port.out;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;

import java.util.List;
import java.util.Optional;

/** Outbound port for Application storage. */
public interface ApplicationRepository {

    Application save(Application application);

    Optional<Application> findById(ApplicationId id);

    List<Application> findAll();

    boolean existsByNameIgnoringCase(String name);

    void deleteById(ApplicationId id);
}
