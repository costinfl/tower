package dev.tower.api.health;

/** Issue #4: health endpoint payload, reporting status and version. */
public record HealthResponse(String status, String version) {
}
