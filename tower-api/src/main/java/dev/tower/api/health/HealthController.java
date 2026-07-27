package dev.tower.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue #4: a health endpoint so a developer (or CI) can confirm the API is up. Deliberately
 * simple - no dependency on Actuator - so the loopback-only server has as small a surface as
 * possible, consistent with ADR-009 deferring authentication.
 */
@RestController
public class HealthController {

    private final String version;

    public HealthController(@Value("${tower.version}") String version) {
        this.version = version;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", version);
    }
}
