package dev.tower.api.artifact;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.ArtifactUseCases;
import dev.tower.application.port.in.ArtifactUseCases.AcceptDigest;
import dev.tower.application.port.in.ArtifactUseCases.ConfirmedArtifact;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * Confirming an Application Version's artifacts (ADR-021, FR-082).
 *
 * <p>Two reads and one write. FR-084 says nothing this <em>reads</em> is stored,
 * and nothing is; the write accepts a digest a person looked at, which is theirs
 * rather than the repository's, and is what a release document prints (FR-086).
 *
 * <p>{@code PUT} rather than {@code POST} for that acceptance, matching the
 * bindings: it is identified by the version and the kind it concerns rather than
 * by an id Tower generates, and accepting the same digest twice changes nothing
 * further.
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

    /**
     * Accepts a digest somebody has looked at (FR-086).
     *
     * <p>The digest is in the body rather than taken from the repository at this
     * instant, deliberately. A person is accepting what they saw, and between
     * seeing it and pressing the button a tag can be pushed over — which is the
     * exact situation this whole arrangement exists to make visible.
     */
    @PutMapping("/api/application-versions/{id}/artifacts/{kind}/accepted-digest")
    public AcceptedDigestView accept(@PathVariable("id") String id,
                                     @PathVariable("kind") String kind,
                                     @Valid @RequestBody AcceptDigestRequest request) {
        return AcceptedDigestView.from(artifacts.accept(new AcceptDigest(
                ApplicationVersionId.of(id), kind, request.coordinate(), request.digest())));
    }

    @DeleteMapping("/api/application-versions/{id}/artifacts/{kind}/accepted-digest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable("id") String id, @PathVariable("kind") String kind) {
        artifacts.withdrawAcceptance(ApplicationVersionId.of(id), kind);
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
     * {@code coordinate} is required as well as the digest: a digest without an
     * address sends a reader nowhere, and a template corrected after acceptance
     * would otherwise leave the record unreadable.
     */
    public record AcceptDigestRequest(
            @NotBlank(message = "coordinate is required") String coordinate,
            @NotBlank(message = "digest is required") String digest) {
    }

    public record AcceptedDigestView(String applicationVersionId, String kind, String coordinate,
                                     String digest, java.time.Instant acceptedAt) {

        static AcceptedDigestView from(AcceptedArtifact accepted) {
            return new AcceptedDigestView(
                    accepted.applicationVersionId().value().toString(), accepted.kind(),
                    accepted.coordinate(), accepted.digest(), accepted.acceptedAt());
        }
    }

    /**
     * @param state PRESENT, DIVERGED, ABSENT, NOT_ADDRESSABLE or UNREAD — named by the
     *              server, because the difference between "the repository does
     *              not have this" and "Tower could not ask" is exactly what a
     *              null coordinate would hide (FR-085)
     */
    public record ConfirmedArtifactView(String kind, String connectorId, String system,
                                        String coordinate, String digest, String acceptedDigest,
                                        Instant storedAt, long sizeBytes, String url,
                                        String state, String detail, boolean diverged) {

        static ConfirmedArtifactView from(ConfirmedArtifact artifact) {
            return new ConfirmedArtifactView(
                    artifact.kind(), artifact.connectorId(), artifact.system(),
                    artifact.coordinate(), artifact.digest(), artifact.acceptedDigest(),
                    artifact.storedAt(), artifact.sizeBytes(), artifact.url(),
                    artifact.state().name(), artifact.detail(), artifact.hasDiverged());
        }
    }
}
