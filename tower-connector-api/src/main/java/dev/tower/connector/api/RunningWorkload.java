package dev.tower.connector.api;

import java.time.Instant;

/**
 * One running thing a Deployment Platform reported, in Tower's vocabulary
 * rather than the platform's.
 *
 * <p>CM-03 makes this record the termination point for vendor concepts: a
 * Kubernetes Deployment, an ECS service and an OpenShift DeploymentConfig all
 * arrive here as the same shape, and nothing above this package can tell which
 * platform produced it.
 *
 * <p>This is deliberately <em>not</em> an Observation. It carries no Environment
 * and no Application, because a Connector cannot know them — ADR-012 places that
 * correspondence in External Bindings, which the Collector applies. A Connector
 * reports what is running; it does not decide what that means.
 *
 * <p>{@code observedAt} is the platform's own timestamp, carried through
 * unmodified so FR-021 holds all the way from the source to the stored
 * Observation. It is not the time of synchronization.
 *
 * @param name       what the platform calls this workload, used for reporting
 *                   unrecognized workloads back to the user (FR-060)
 * @param image      the image reference without its tag, matched against bindings
 * @param imageTag   the tag, from which the Application Version is derived
 * @param observedAt when the platform says this became the running state
 */
public record RunningWorkload(String name, String image, String imageTag, Instant observedAt) {

    public RunningWorkload {
        name = requireText(name, "A running workload must carry the name the platform gave it.");
        image = requireText(image, "A running workload must carry an image reference.");
        imageTag = requireText(imageTag, "A running workload must carry an image tag.");
        if (observedAt == null) {
            throw new ConnectorException(
                    "A running workload must carry the platform's own timestamp (FR-021).");
        }
    }

    /** The full reference as the platform expressed it, for user-facing reports. */
    public String imageReference() {
        return image + ":" + imageTag;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConnectorException(message);
        }
        return value.trim();
    }
}
