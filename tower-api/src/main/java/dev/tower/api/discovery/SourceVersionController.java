package dev.tower.api.discovery;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.discovery.DiscoveredVersion;
import dev.tower.application.discovery.VersionDiscovery;
import dev.tower.application.port.in.SourceControlUseCases;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationId;

/**
 * Candidate Application Versions discovered from source control (issue #3,
 * ADR-014).
 *
 * <p>Every method here is a {@code GET}, and that is the design rather than a
 * coincidence. Discovery reads a repository and stores nothing: BR-01 makes an
 * Application Version immutable, so registering one is a decision a user takes
 * through the existing {@code POST /api/application-versions}. Giving discovery
 * its own creation path would put that rule in two places.
 */
@RestController
@RequestMapping("/api")
public class SourceVersionController {

    private final SourceControlUseCases sourceControl;

    public SourceVersionController(SourceControlUseCases sourceControl) {
        this.sourceControl = sourceControl;
    }

    /** What the repository bound to this Application currently holds. */
    @GetMapping("/applications/{id}/source-versions")
    public DiscoveryResponse discover(@PathVariable("id") String id) {
        return DiscoveryResponse.from(sourceControl.discover(ApplicationId.of(id)));
    }

    /** The same across every Application with a repository bound. */
    @GetMapping("/source-versions")
    public List<DiscoveryResponse> discoverAll() {
        return sourceControl.discoverAll().stream().map(DiscoveryResponse::from).toList();
    }

    /**
     * Tests a repository without saving a binding, so a URL and a credential can
     * be checked before anything depends on them.
     */
    @GetMapping("/source-versions/connection-test")
    public ConnectionTest checkConnection(@RequestParam("repositoryUrl") String repositoryUrl) {
        return sourceControl.checkConnection(repositoryUrl);
    }

    /**
     * @param failure   why the repository could not be read, or null when it was.
     *                  Present rather than folded into an empty candidate list,
     *                  because "could not look" and "looked and found nothing"
     *                  lead to opposite next steps
     * @param unmatched ref names the version pattern did not recognise, so a user
     *                  whose pattern is wrong can see what it failed on
     */
    public record DiscoveryResponse(
            String applicationId,
            String repositoryUrl,
            List<CandidateResponse> candidates,
            List<String> unmatched,
            String failure) {

        static DiscoveryResponse from(VersionDiscovery discovery) {
            return new DiscoveryResponse(
                    discovery.applicationId().value().toString(),
                    discovery.repositoryUrl(),
                    discovery.candidates().stream().map(CandidateResponse::from).toList(),
                    discovery.unmatched(),
                    discovery.failure());
        }
    }

    /**
     * @param source            which Connector proposed this — a ref from source
     *                          control, or a run from a build job (ADR-020). The
     *                          two are the same kind of proposal and belong in one
     *                          list, but a reader deciding whether to accept one
     *                          needs to know which system said so
     * @param origin            where within that source it came from, in that
     *                          source's own words: a ref name, or a job and run
     * @param alreadyRegistered whether Tower already holds this version. Reported
     *                          rather than filtered out: on the second run most
     *                          candidates are already registered, and hiding them
     *                          would read as Tower having lost them
     */
    public record CandidateResponse(
            String version, String source, String origin, String branch, String tag,
            String commit, String buildIdentifier, boolean alreadyRegistered) {

        static CandidateResponse from(DiscoveredVersion candidate) {
            return new CandidateResponse(
                    candidate.version(), candidate.source(), candidate.origin(),
                    candidate.branch(), candidate.tag(), candidate.commit(),
                    candidate.buildIdentifier(), candidate.alreadyRegistered());
        }
    }
}
