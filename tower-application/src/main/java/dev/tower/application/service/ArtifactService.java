package dev.tower.application.service;

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
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;

/**
 * Confirming an Application Version's artifacts against the repositories
 * (ADR-021).
 *
 * <p>Reads and shows; stores nothing, and there is nowhere for it to store
 * anything — no artifact table exists in the schema and FR-084 says none is
 * meant to. What a release document prints is an accepted digest, which somebody
 * stated; this only makes the repository's current answer visible so that
 * somebody can notice a tag was pushed over.
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
    private final List<ArtifactCollector> collectors;

    public ArtifactService(ApplicationVersionRepository versions,
                           ExternalBindingRepository bindings,
                           List<ArtifactCollector> collectors) {
        this.versions = Objects.requireNonNull(versions);
        this.bindings = Objects.requireNonNull(bindings);
        this.collectors = List.copyOf(collectors);
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

        List<ConfirmedArtifact> artifacts = new ArrayList<>();
        for (Composed entry : composed) {
            artifacts.add(describe(entry, answers.get(entry.coordinateKey())));
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

    private ConfirmedArtifact describe(Composed entry, Answer answer) {
        ArtifactCoordinateBinding template = entry.template();

        if (entry.coordinate() == null) {
            // The template wanted something this version does not carry. Only
            // the commit can be missing: a version string is required for an
            // Application Version to exist at all.
            return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                    null, null, null, 0, null, State.NOT_ADDRESSABLE,
                    "This version carries no commit, and the template needs one: "
                            + template.coordinateTemplate());
        }
        if (answer == null || answer.state() == State.UNREAD) {
            return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                    entry.coordinate(), null, null, 0, null, State.UNREAD,
                    answer == null ? "The repository was not read." : answer.detail());
        }
        if (answer.state() == State.ABSENT) {
            return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                    entry.coordinate(), null, null, 0, null, State.ABSENT,
                    "The repository has nothing at this coordinate.");
        }

        ArtifactCollector.ConfirmedArtifact found = answer.artifact();
        return new ConfirmedArtifact(template.kind(), template.connectorId(), template.system(),
                entry.coordinate(),
                found.digest() == null || found.digest().isBlank() ? null : found.digest(),
                found.storedAt(), found.sizeBytes(),
                found.url() == null || found.url().isBlank() ? null : found.url(),
                State.PRESENT, null);
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
