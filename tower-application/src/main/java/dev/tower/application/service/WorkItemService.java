package dev.tower.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.port.in.WorkItemUseCases;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.application.port.out.WorkItemCollector;
import dev.tower.application.sync.ConnectionTest;
import dev.tower.domain.releasepack.ReleasePack;
import dev.tower.domain.releasepack.ReleasePackId;
import dev.tower.domain.releasepack.WorkItemReference;

/**
 * Resolving a Release Pack's work items against the tracker (ADR-018).
 *
 * <p>Reads and shows; stores nothing. The tracker is never written back into
 * Tower, which is what keeps a generated document reproducible: the title a
 * document prints is the one a person accepted, and this service only makes the
 * difference visible so somebody can decide whether to accept the newer one.
 *
 * <p>Every failure here is contained. A tracker that is unconfigured,
 * unreachable or refusing the credential produces a Resolution that still lists
 * every reference the release carries, marked unresolved. A release does not
 * become less true because a tracker is down, and a screen that showed nothing
 * would say the opposite.
 *
 * <p>Carries no framework annotation; Spring wiring lives in tower-api.
 */
public class WorkItemService implements WorkItemUseCases {

    private final ReleasePackRepository releasePacks;
    private final ExternalBindingRepository bindings;
    private final List<WorkItemCollector> collectors;

    public WorkItemService(ReleasePackRepository releasePacks,
                           ExternalBindingRepository bindings,
                           List<WorkItemCollector> collectors) {
        this.releasePacks = Objects.requireNonNull(releasePacks);
        this.bindings = Objects.requireNonNull(bindings);
        this.collectors = List.copyOf(collectors);
    }

    @Override
    public Resolution resolve(ReleasePackId releasePackId) {
        ReleasePack pack = releasePacks.findById(releasePackId)
                .orElseThrow(() -> new NotFoundException(
                        "Release Pack " + releasePackId + " does not exist."));

        List<WorkItemReference> references = pack.workItems();
        Optional<WorkItemCollector> collector = configuredCollector();

        if (collector.isEmpty()) {
            return new Resolution(null, unresolved(references),
                    "No issue tracker is configured, so Tower cannot say what these items are now."
                            + " The titles below are the ones already accepted.");
        }

        List<CollectedWorkItemHolder> collected;
        try {
            collected = collector.get()
                    .read(references.stream().map(WorkItemReference::identifier).toList())
                    .stream().map(CollectedWorkItemHolder::new).toList();
        } catch (RuntimeException e) {
            // Contained deliberately: the references are still shown, and the
            // message says the tracker could not be read rather than implying
            // the items do not exist.
            return new Resolution(collector.get().connectorId(), unresolved(references),
                    "The tracker could not be read: " + describe(e));
        }

        Map<String, WorkItemCollector.CollectedWorkItem> byIdentifier = new LinkedHashMap<>();
        for (CollectedWorkItemHolder holder : collected) {
            // Matched case-insensitively for the same reason references are:
            // trackers are, and "proj-123" is not a second ticket.
            byIdentifier.put(holder.item().identifier().toLowerCase(java.util.Locale.ROOT), holder.item());
        }

        List<ResolvedWorkItem> items = new ArrayList<>();
        for (WorkItemReference reference : references) {
            WorkItemCollector.CollectedWorkItem found =
                    byIdentifier.get(reference.identifier().toLowerCase(java.util.Locale.ROOT));

            if (found == null) {
                // The tracker answered and does not know this identifier. That is
                // a different fact from not having asked, and the state says so.
                items.add(new ResolvedWorkItem(reference.identifier(), reference.title(),
                        null, null, false, null, State.NOT_FOUND));
                continue;
            }

            // Diverged only when a title was actually accepted. A reference with
            // no accepted title has nothing to have drifted from.
            boolean diverged = reference.hasTitle() && !reference.title().equals(found.title());
            items.add(new ResolvedWorkItem(reference.identifier(), reference.title(),
                    found.title(), found.status(), found.closed(), found.url(),
                    diverged ? State.DIVERGED : State.RESOLVED));
        }

        return new Resolution(collector.get().connectorId(), items, null);
    }

    @Override
    public ConnectionReport testConnection(String connectorId) {
        Optional<IssueTrackerBinding> binding = bindings.findIssueTrackerBinding(connectorId);
        if (binding.isEmpty()) {
            return new ConnectionReport(connectorId, null, false,
                    "No tracker is bound to this Connector yet.");
        }

        Optional<WorkItemCollector> collector = collectors.stream()
                .filter(c -> c.connectorId().equals(connectorId))
                .findFirst();
        if (collector.isEmpty()) {
            return new ConnectionReport(connectorId, binding.get().locator(), false,
                    "No Connector named '" + connectorId + "' is installed.");
        }

        ConnectionTest test = collector.get().checkConnection();
        return new ConnectionReport(connectorId, binding.get().locator(),
                test.reachable(), test.message());
    }

    /**
     * The one tracker configured, if any.
     *
     * <p>ADR-018 binds a tracker per Connector and expects one. Where more than
     * one is bound the first by Connector id is used, chosen deterministically so
     * two reads of the same configuration cannot disagree.
     */
    private Optional<WorkItemCollector> configuredCollector() {
        return bindings.findAllIssueTrackerBindings().stream()
                .map(IssueTrackerBinding::connectorId)
                .sorted()
                .flatMap(connectorId -> collectors.stream()
                        .filter(c -> c.connectorId().equals(connectorId)))
                .findFirst();
    }

    private List<ResolvedWorkItem> unresolved(List<WorkItemReference> references) {
        return references.stream()
                .map(reference -> new ResolvedWorkItem(reference.identifier(), reference.title(),
                        null, null, false, null, State.UNRESOLVED))
                .toList();
    }

    /**
     * The failure in words, without the exception's class name.
     *
     * <p>A message that names an internal type tells a reader nothing they can
     * act on, and NFR-028 keeps implementation detail out of what a caller sees.
     */
    private String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "no reason was given." : message;
    }

    /** Wrapper so the collected list can be iterated without null entries. */
    private record CollectedWorkItemHolder(WorkItemCollector.CollectedWorkItem item) {
        private CollectedWorkItemHolder {
            Objects.requireNonNull(item, "A collector must not return a null work item.");
        }
    }
}
