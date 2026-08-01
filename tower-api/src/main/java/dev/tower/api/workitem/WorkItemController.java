package dev.tower.api.workitem;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tower.application.port.in.WorkItemUseCases;
import dev.tower.application.port.in.WorkItemUseCases.ResolvedWorkItem;
import dev.tower.domain.releasepack.ReleasePackId;

/**
 * Reading a Release Pack's work items against the tracker (ADR-018).
 *
 * <p>Two reads and nothing else. Linking, unlinking and accepting a title live
 * on the Release Pack, because those change what Tower holds; everything here
 * only looks at somebody else's system and stores none of it.
 *
 * <p>A tracker that could not be reached is answered with 200 and a stated
 * failure rather than an error status. The request succeeded — Tower now knows,
 * and can say, that it could not reach the tracker — and a 502 would make the
 * Viewer show an error page where it should show the release's own work items
 * marked unresolved.
 */
@RestController
public class WorkItemController {

    private final WorkItemUseCases workItems;

    public WorkItemController(WorkItemUseCases workItems) {
        this.workItems = workItems;
    }

    @GetMapping("/api/release-packs/{id}/work-items/resolved")
    public ResolutionView resolve(@PathVariable("id") String id) {
        var resolution = workItems.resolve(ReleasePackId.of(id));
        return new ResolutionView(
                resolution.connectorId(),
                resolution.reachedTracker(),
                resolution.failure(),
                resolution.items().stream().map(ResolvedWorkItemView::from).toList());
    }

    @GetMapping("/api/work-items/connection-test")
    public WorkItemUseCases.ConnectionReport testConnection(
            @RequestParam("connectorId") String connectorId) {
        return workItems.testConnection(connectorId);
    }

    /**
     * @param reachedTracker stated rather than inferred from a null failure, so a
     *                       client cannot render "everything is fine" from a
     *                       response that never reached the tracker
     */
    public record ResolutionView(String connectorId, boolean reachedTracker, String failure,
                                 List<ResolvedWorkItemView> items) {
    }

    /**
     * @param state RESOLVED, DIVERGED, NOT_FOUND or UNRESOLVED — named by the
     *              server, because the difference between "the tracker does not
     *              have this" and "Tower could not ask" is exactly what a null
     *              would hide
     */
    public record ResolvedWorkItemView(String identifier, String acceptedTitle,
                                       String trackerTitle, String status, boolean closed,
                                       String url, String state, boolean diverged) {

        static ResolvedWorkItemView from(ResolvedWorkItem item) {
            return new ResolvedWorkItemView(
                    item.identifier(), item.acceptedTitle(), item.trackerTitle(),
                    item.status(), item.closed(), item.url(),
                    item.state().name(), item.hasDiverged());
        }
    }
}
