package dev.tower.domain.releasepack;

import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersionId;
import dev.tower.domain.handover.Handover;
import dev.tower.domain.iteration.Iteration;
import dev.tower.domain.iteration.IterationId;
import dev.tower.domain.promotionpath.PromotionPathId;
import dev.tower.domain.shared.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A logical grouping of Application Versions prepared for delivery together —
 * the central business concept of Tower (ADR-004).
 *
 * <p>A Release Pack is the developer's view of a software release. It groups
 * Application Versions, records which Promotion Path version it follows, and
 * owns the Handover information and validation Iterations that accompany it.
 *
 * <p>A Release Pack does <em>not</em> own deployment state. Where a release has
 * actually been seen is derived from Observations (ADR-008), which arrive with
 * Epic 3. What this type carries instead is {@code archived}: an explicit
 * lifecycle flag, classified as Intent rather than Observation, because no
 * Observation can express that a team has stopped progressing a release.
 */
public final class ReleasePack {

    public static final int NAME_MAX_LENGTH = 150;

    private final ReleasePackId id;
    private final String name;
    private final String description;
    private final PromotionPathAssignment promotionPath;
    private final List<PackedVersion> contents;
    private final Handover handover;
    private final List<Iteration> iterations;
    private final boolean archived;

    private ReleasePack(ReleasePackId id, String name, String description,
                        PromotionPathAssignment promotionPath, List<PackedVersion> contents,
                        Handover handover, List<Iteration> iterations, boolean archived) {
        DomainException.require(id != null, "Release Pack id is required.");
        DomainException.require(name != null && !name.isBlank(), "Release Pack name is required.");
        DomainException.require(name.length() <= NAME_MAX_LENGTH,
                "Release Pack name must be at most " + NAME_MAX_LENGTH + " characters.");
        this.id = id;
        this.name = name.trim();
        this.description = description == null ? "" : description.strip();
        this.promotionPath = promotionPath;
        this.contents = List.copyOf(contents);
        this.handover = handover == null ? Handover.empty() : handover;
        this.iterations = List.copyOf(iterations);
        this.archived = archived;
    }

    /**
     * Creates an empty Release Pack.
     *
     * <p>SM-05: a Release Pack may exist before any deployment occurs, and
     * before it has contents or a Promotion Path. Planning starts somewhere.
     */
    public static ReleasePack create(String name, String description) {
        return new ReleasePack(ReleasePackId.newId(), name, description, null,
                List.of(), Handover.empty(), List.of(), false);
    }

    /** Reconstitutes a Release Pack from storage. */
    public static ReleasePack reconstitute(ReleasePackId id, String name, String description,
                                           PromotionPathAssignment promotionPath, List<PackedVersion> contents,
                                           Handover handover, List<Iteration> iterations, boolean archived) {
        return new ReleasePack(id, name, description, promotionPath, contents, handover, iterations, archived);
    }

    public ReleasePack updateMetadata(String newName, String newDescription) {
        requireActive("renamed or described");
        return copyWith(newName, newDescription, promotionPath, contents, handover, iterations, archived);
    }

    /**
     * Assigns the Promotion Path version this pack follows (FR-005).
     *
     * <p>Pinning the version is the point: a later edit to that path publishes a
     * new version and leaves this pack pointing at the topology it was planned
     * against (ADR-007).
     */
    public ReleasePack assignPromotionPath(PromotionPathId pathId, int versionNumber) {
        requireActive("assigned a Promotion Path");
        return copyWith(name, description, new PromotionPathAssignment(pathId, versionNumber),
                contents, handover, iterations, archived);
    }

    public ReleasePack clearPromotionPath() {
        requireActive("changed");
        return copyWith(name, description, null, contents, handover, iterations, archived);
    }

    /**
     * Adds an Application Version to the pack (FR-003).
     *
     * <p>A pack may hold at most one version of any given Application. A Release
     * Pack describes what is delivered together, and two versions of the same
     * Application cannot be — one of them would simply overwrite the other.
     */
    public ReleasePack addApplicationVersion(ApplicationId applicationId, ApplicationVersionId versionId) {
        requireActive("changed");
        PackedVersion incoming = new PackedVersion(applicationId, versionId);

        for (PackedVersion existing : contents) {
            DomainException.require(!existing.versionId().equals(versionId),
                    "This Application Version is already in the Release Pack.");
            DomainException.require(!existing.applicationId().equals(applicationId),
                    "The Release Pack already contains a different version of this Application."
                            + " Remove it first, since only one version of an Application can be delivered.");
        }

        List<PackedVersion> updated = new ArrayList<>(contents);
        updated.add(incoming);
        return copyWith(name, description, promotionPath, updated, handover, iterations, archived);
    }

    /** Removes an Application Version from the pack (FR-004). */
    public ReleasePack removeApplicationVersion(ApplicationVersionId versionId) {
        requireActive("changed");
        List<PackedVersion> updated = new ArrayList<>(contents);
        boolean removed = updated.removeIf(entry -> entry.versionId().equals(versionId));
        DomainException.require(removed, "This Application Version is not in the Release Pack.");
        return copyWith(name, description, promotionPath, updated, handover, iterations, archived);
    }

    /** Replaces the Handover information (FR-006). */
    public ReleasePack updateHandover(Handover newHandover) {
        requireActive("changed");
        DomainException.require(newHandover != null, "Handover information is required.");
        return copyWith(name, description, promotionPath, contents, newHandover, iterations, archived);
    }

    /** Records a validation Iteration against this pack (gap G4). */
    public ReleasePack startIteration(String iterationName, Instant startedAt, String notes) {
        requireActive("changed");
        List<Iteration> updated = new ArrayList<>(iterations);
        updated.add(Iteration.start(iterationName, startedAt, notes));
        return copyWith(name, description, promotionPath, contents, handover, updated, archived);
    }

    public ReleasePack replaceIteration(Iteration iteration) {
        requireActive("changed");
        DomainException.require(iteration != null, "Iteration is required.");
        List<Iteration> updated = new ArrayList<>(iterations);
        int index = indexOfIteration(iteration.id());
        DomainException.require(index >= 0, "This Iteration does not belong to the Release Pack.");
        updated.set(index, iteration);
        return copyWith(name, description, promotionPath, contents, handover, updated, archived);
    }

    public ReleasePack removeIteration(IterationId iterationId) {
        requireActive("changed");
        List<Iteration> updated = new ArrayList<>(iterations);
        boolean removed = updated.removeIf(existing -> existing.id().equals(iterationId));
        DomainException.require(removed, "This Iteration does not belong to the Release Pack.");
        return copyWith(name, description, promotionPath, contents, handover, updated, archived);
    }

    /**
     * Marks the pack as no longer actively progressing.
     *
     * <p>ADR-008 separates this from the derived observed state. Archiving says
     * the team has stopped working the release; it says nothing about where the
     * release was seen, and it erases nothing.
     */
    public ReleasePack archive() {
        return archived ? this
                : copyWith(name, description, promotionPath, contents, handover, iterations, true);
    }

    public ReleasePack restore() {
        return archived
                ? copyWith(name, description, promotionPath, contents, handover, iterations, false)
                : this;
    }

    public Optional<Iteration> iteration(IterationId iterationId) {
        return iterations.stream().filter(i -> i.id().equals(iterationId)).findFirst();
    }

    public boolean contains(ApplicationVersionId versionId) {
        return contents.stream().anyMatch(entry -> entry.versionId().equals(versionId));
    }

    public boolean containsApplication(ApplicationId applicationId) {
        return contents.stream().anyMatch(entry -> entry.applicationId().equals(applicationId));
    }

    /** Application Versions in the pack, keyed by their owning Application. */
    public Map<ApplicationId, ApplicationVersionId> versionsByApplication() {
        Map<ApplicationId, ApplicationVersionId> byApplication = new LinkedHashMap<>();
        contents.forEach(entry -> byApplication.put(entry.applicationId(), entry.versionId()));
        return Map.copyOf(byApplication);
    }

    public ReleasePackId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Optional<PromotionPathAssignment> promotionPath() {
        return Optional.ofNullable(promotionPath);
    }

    public List<PackedVersion> contents() {
        return contents;
    }

    public Handover handover() {
        return handover;
    }

    public List<Iteration> iterations() {
        return iterations;
    }

    public boolean isArchived() {
        return archived;
    }

    private int indexOfIteration(IterationId iterationId) {
        for (int i = 0; i < iterations.size(); i++) {
            if (iterations.get(i).id().equals(iterationId)) {
                return i;
            }
        }
        return -1;
    }

    private void requireActive(String action) {
        DomainException.require(!archived,
                "An archived Release Pack cannot be " + action + ". Restore it first.");
    }

    private ReleasePack copyWith(String name, String description, PromotionPathAssignment promotionPath,
                                 List<PackedVersion> contents, Handover handover,
                                 List<Iteration> iterations, boolean archived) {
        return new ReleasePack(id, name, description, promotionPath, contents, handover, iterations, archived);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ReleasePack other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ReleasePack[" + id + ", name=" + name + ", applications=" + contents.size()
                + (archived ? ", archived" : "") + "]";
    }
}
