package dev.tower.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.ArtifactCoordinateBinding;
import dev.tower.application.port.in.ArtifactUseCases;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ArtifactCollector;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.AcceptedArtifactRepository;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * Confirming an Application Version's artifacts against the repositories
 * (ADR-021).
 *
 * <p>Reads and shows; stores nothing it read. No artifact table exists in the
 * schema and FR-084 says none is meant to. The single write here is
 * {@link #accept}, and what it writes is a digest a person accepted rather than
 * one Tower found — the distinction ADR-018 draws between a tracker's current
 * wording and a title somebody stood behind.
 *
 * <p>Which makes the comparison this service exists for possible: a document
 * prints the accepted digest, the screen shows what the repository says now, and
 * where they differ a tag was pushed over. That is a fact a team almost never
 * learns any other way.
 *
 * <p>Every failure here is contained, exactly as it is in {@link WorkItemService}.
 * A repository that is unreachable or refusing the credential produces a
 * confirmation that still lists every template the Application carries, marked
 * unread. A version does not become less true because a repository is down, and a
 * screen that showed nothing would say the opposite.
 *
 * <p>Carries no framework annotation; Spring wiring lives in tower-api.
 */
public class ArtifactService implements ArtifactUseCases {

    private final ApplicationVersionRepository versions;
    private final ExternalBindingRepository bindings;
    private final AcceptedArtifactRepository accepted;
    private final List<ArtifactCollector> collectors;
    private final Clock clock;

    public ArtifactService(ApplicationVersionRepository versions,
                           ExternalBindingRepository bindings,
                           AcceptedArtifactRepository accepted,
                           List<ArtifactCollector> collectors,
                           Clock clock) {
        this.versions = Objects.requireNonNull(versions);
        this.bindings = Objects.requireNonNull(bindings);
        this.accepted = Objects.requireNonNull(accepted);
        this.collectors = List.copyOf(collectors);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Confirmation confirm(ApplicationVersionId applicationVersionId) {
        ApplicationVersion version = versions.findById(applicationVersionId)
                .orElseThrow(() -> new NotFoundException(
                        "Application Version " + applicationVersionId + " does not exist."));

        List<ArtifactCoordinateBinding> templates =
                bindings.findArtifactCoordinateBindings(version.applicationId());

        // Composed first, then read. Grouping by Connector and repository means
        // three templates pointing at one Artifactory cost one read rather than
        // three, and it is also the only way a Connector gets to answer several
        // questions in whatever manner suits it.
        Map<String, List<Composed>> byRepository = new LinkedHashMap<>();
        List<Composed> composed = new ArrayList<>();
        for (ArtifactCoordinateBinding template : templates) {
            Composed entry = new Composed(template, template.compose(version).orElse(null));
            composed.add(entry);
            if (entry.coordinate() != null) {
                byRepository.computeIfAbsent(
                        repositoryKey(template.connectorId(), template.system()),
                        key -> new ArrayList<>()).add(entry);
            }
        }

        Map<String, Answer> answers = new LinkedHashMap<>();
        for (Map.Entry<String, List<Composed>> group : byRepository.entrySet()) {
            readGroup(group.getValue(), answers);
        }

        Map<String, AcceptedArtifact> acceptedByKind = new LinkedHashMap<>();
        for (AcceptedArtifact one : accepted.findAllFor(applicationVersionId)) {
            acceptedByKind.put(one.kind(), one);
        }

        List<ConfirmedArtifact> artifacts = new ArrayList<>();
        for (Composed entry : composed) {
            artifacts.add(describe(entry, answers.get(entry.coordinateKey()),
                    acceptedByKind.get(entry.template().kind())));
        }

        return new Confirmation(applicationVersionId.value().toString(),
                version.version(), version.commit(), artifacts);
    }

    /**
     * Reads one repository's worth of coordinates, recording an answer for each.
     *
     * <p>A repository that could not be read leaves every coordinate in the group
     * unread with the reason attached, rather than absent. FR-085 makes that
     * distinction the point of the whole method: "not there" and "could not ask"
     * are different facts, and presenting either as the other would send somebody
     * to look for a build that ran, or reassure them about one that did not.
     */
    private void readGroup(List<Composed> group, Map<String, Answer> answers) {
        ArtifactCoordinateBinding first = group.get(0).template();
        Optional<ArtifactCollector> collector = collectors.stream()
                .filter(c -> c.connectorId().equals(first.connectorId()))
                .findFirst();

        if (collector.isEmpty()) {
            for (Composed entry : group) {
                answers.put(entry.coordinateKey(), Answer.unread(
                        "No Connector named '" + first.connectorId() + "' is installed."));
            }
            return;
        }

        List<String> coordinates = group.stream().map(Composed::coordinate).toList();
        try {
            Map<String, ArtifactCollector.ConfirmedArtifact> found = new LinkedHashMap<>();
            for (ArtifactCollector.ConfirmedArtifact artifact
                    : collector.get().read(first.system(), coordinates)) {
                found.put(artifact.coordinate(), artifact);
            }
            for (Composed entry : group) {
                ArtifactCollector.ConfirmedArtifact artifact = found.get(entry.coordinate());
                answers.put(entry.coordinateKey(),
                        artifact == null ? Answer.absent() : Answer.present(artifact));
            }
        } catch (RuntimeException e) {
            for (Composed entry : group) {
                answers.put(entry.coordinateKey(),
                        Answer.unread("The repository could not be read: " + describe(e)));
            }
        }
    }

    private ConfirmedArtifact describe(Composed entry, Answer answer, AcceptedArtifact accepted) {
        ArtifactCoordinateBinding template = entry.template();
        String acceptedDigest = accepted == null ? null : accepted.digest();

        if (entry.coordinate() == null) {
            // The template wanted something this version does not carry. Only
            // the commit can be missing: a version string is required for an
            // Application Version to exist at all.
            return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                    null, null, acceptedDigest, null, 0, null, State.NOT_ADDRESSABLE,
                    "This version carries no commit, and the template needs one: "
                            + template.coordinateTemplate());
        }
        if (answer == null || answer.state() == State.UNREAD) {
            return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                    entry.coordinate(), null, acceptedDigest, null, 0, null, State.UNREAD,
                    answer == null ? "The repository was not read." : answer.detail());
        }
        if (answer.state() == State.ABSENT) {
            return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                    entry.coordinate(), null, acceptedDigest, null, 0, null, State.ABSENT,
                    "The repository has nothing at this coordinate.");
        }

        ArtifactCollector.ConfirmedArtifact found = answer.artifact();
        String digest = found.digest() == null || found.digest().isBlank() ? null : found.digest();

        // Divergence is only ever claimed against something a person accepted.
        // An artifact nobody has accepted a digest for has nothing to have
        // drifted from, exactly as a work item reference with no accepted title
        // cannot diverge from the tracker's.
        boolean diverged = accepted != null && accepted.differsFrom(digest);

        return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                entry.coordinate(), digest, acceptedDigest,
                found.storedAt(), found.sizeBytes(),
                found.url() == null || found.url().isBlank() ? null : found.url(),
                diverged ? State.DIVERGED : State.PRESENT,
                diverged ? "The repository reports different bytes under this name than the ones"
                        + " that were accepted. The tag was pushed over." : null);
    }

    @Override
    public AcceptedArtifact accept(AcceptDigest command) {
        InvalidRequestException.require(command != null, "Say what is being accepted.");
        if (versions.findById(command.applicationVersionId()).isEmpty()) {
            throw new NotFoundException(
                    "Application Version " + command.applicationVersionId() + " does not exist.");
        }
        // Not checked against the repository, deliberately. A person is accepting
        // what they looked at; re-reading first would accept whatever the
        // repository says at this instant, which is the very thing that can have
        // changed underneath them.
        return accepted.save(new AcceptedArtifact(command.applicationVersionId(),
                command.kind(), command.coordinate(), command.digest(), Instant.now(clock)));
    }

    @Override
    public void withdrawAcceptance(ApplicationVersionId applicationVersionId, String kind) {
        accepted.delete(applicationVersionId, kind);
    }

    @Override
    public List<AcceptedArtifact> acceptedArtifactsFor(List<ApplicationVersionId> applicationVersionIds) {
        return accepted.findAllFor(applicationVersionIds);
    }

    @Override
    public ConnectionTest testConnection(String connectorId, String system) {
        InvalidRequestException.require(system != null && !system.isBlank(),
                "Name the repository to test.");
        Optional<ArtifactCollector> collector = collectors.stream()
                .filter(c -> c.connectorId().equals(connectorId))
                .findFirst();
        if (collector.isEmpty()) {
            return ConnectionTest.unreachable(connectorId, system, null,
                    "No Connector named '" + connectorId + "' is installed.");
        }
        return collector.get().checkConnection(system.trim());
    }

    /**
     * The failure in words, without the exception's class name.
     *
     * <p>A message naming an internal type tells a reader nothing they can act
     * on, and NFR-028 keeps implementation detail out of what a caller sees.
     */
    private String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "no reason was given." : message;
    }

    private static String repositoryKey(String connectorId, String system) {
        return connectorId + "|" + system;
    }

    /** One template and the coordinate it composed, or null when it could not. */
    private record Composed(ArtifactCoordinateBinding template, String coordinate) {

        /**
         * Keyed by repository as well as coordinate, so two templates that
         * compose the same string against different repositories do not share
         * one answer.
         */
        String coordinateKey() {
            return repositoryKey(template.connectorId(), template.system()) + "|" + coordinate;
        }
    }

    /** What one read established about one coordinate. */
    private record Answer(State state, ArtifactCollector.ConfirmedArtifact artifact, String detail) {

        static Answer present(ArtifactCollector.ConfirmedArtifact artifact) {
            return new Answer(State.PRESENT, artifact, null);
        }

        static Answer absent() {
            return new Answer(State.ABSENT, null, null);
        }

        static Answer unread(String detail) {
            return new Answer(State.UNREAD, null, detail);
        }
    }
}
