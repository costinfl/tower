package dev.tower.api.artifact;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.ArtifactUseCases;
import dev.tower.application.port.in.ArtifactUseCases.ConfirmedArtifact;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * Confirming an Application Version's artifacts (ADR-021, FR-082).
 *
 * <p>Two reads and nothing else, as {@code WorkItemController} is. There is no
 * write here and there is nothing to write: FR-084 says nothing this reads is
 * stored, and no table exists for it.
 *
 * <p>A repository that could not be reached is answered with 200 and a stated
 * reason rather than an error status, for the same reason work item resolution
 * is. The request succeeded — Tower now knows, and can say, that it could not
 * reach the repository — and a 502 would make the Viewer show an error page where
 * it should show the version's own templates marked unread.
 */
@RestController
public class ArtifactController {

    private final ArtifactUseCases artifacts;

    public ArtifactController(ArtifactUseCases artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping("/api/application-versions/{id}/artifacts")
    public ConfirmationView confirm(@PathVariable("id") String id) {
        var confirmation = artifacts.confirm(ApplicationVersionId.of(id));
        return new ConfirmationView(
                confirmation.applicationVersionId(), confirmation.version(), confirmation.commit(),
                confirmation.artifacts().stream().map(ConfirmedArtifactView::from).toList());
    }

    @GetMapping("/api/artifacts/connection-test")
    public ConnectionTest testConnection(
            @RequestParam("connectorId") String connectorId,
            @RequestParam("system") String system) {
        return artifacts.testConnection(connectorId, system);
    }

    public record ConfirmationView(String applicationVersionId, String version, String commit,
                                   List<ConfirmedArtifactView> artifacts) {
    }

    /**
     * @param state PRESENT, ABSENT, NOT_ADDRESSABLE or UNREAD — named by the
     *              server, because the difference between "the repository does
     *              not have this" and "Tower could not ask" is exactly what a
     *              null coordinate would hide (FR-085)
     */
    public record ConfirmedArtifactView(String kind, String connectorId, String system,
                                        String coordinate, String digest, Instant storedAt,
                                        long sizeBytes, String url, String state, String detail) {

        static ConfirmedArtifactView from(ConfirmedArtifact artifact) {
            return new ConfirmedArtifactView(
                    artifact.kind(), artifact.connectorId(), artifact.system(),
                    artifact.coordinate(), artifact.digest(), artifact.storedAt(),
                    artifact.sizeBytes(), artifact.url(),
                    artifact.state().name(), artifact.detail());
        }
    }
}
