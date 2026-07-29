package dev.tower.connector.api;

import java.time.Instant;

/**
 * One running thing a Deployment Platform reported, in Tower's vocabulary
 * rather than the platform's.
 *
 * <p>CM-03 makes this record the termination point for vendor concepts: a
 * Kubernetes Deployment, an OpenShift DeploymentConfig and an ECS service all
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
 * <p>{@code imageTag} may be null, and that is the interesting case. OpenShift
 * ImageStream triggers rewrite a running workload's image from a tag to a
 * digest, so what the platform reports is
 * {@code registry/acme/api@sha256:abc…} with no tag at all. There is then
 * nothing for a version pattern to match. Such a workload is reported and ends
 * up as unrecognized (FR-060) rather than throwing: one workload Tower cannot
 * attribute must not fail the whole run and hide every workload it could.
 *
 * @param name       what the platform calls this workload, used when reporting
 *                   an unrecognized workload back to the user (FR-060)
 * @param image      the image reference without its tag or digest, matched against bindings
 * @param imageTag   the tag, or null when the platform reported a digest instead
 * @param digest     the digest, or null when the image carried a tag
 * @param observedAt when the platform says this became the running state
 */
public record RunningWorkload(
        String name, String image, String imageTag, String digest, Instant observedAt) {

    public RunningWorkload {
        name = requireText(name, "A running workload must carry the name the platform gave it.");
        image = requireText(image, "A running workload must carry an image reference.");
        imageTag = blankToNull(imageTag);
        digest = blankToNull(digest);
        if (observedAt == null) {
            throw new ConnectorException(
                    "A running workload must carry the platform's own timestamp (FR-021).");
        }
    }

    /** A workload whose image carries a tag, which is the ordinary case. */
    public static RunningWorkload tagged(String name, String image, String imageTag, Instant observedAt) {
        return new RunningWorkload(name, image, imageTag, null, observedAt);
    }

    /**
     * A workload whose image was resolved to a digest, so no version can be
     * derived from it.
     */
    public static RunningWorkload digestPinned(String name, String image, String digest, Instant observedAt) {
        return new RunningWorkload(name, image, null, digest, observedAt);
    }

    /**
     * Whether this workload can yield an Application Version at all.
     *
     * <p>False for a digest-pinned image. The Collector reports those as
     * unrecognized with a reason, rather than silently dropping them: a workload
     * Tower can see but cannot name is information the user wants.
     */
    public boolean hasVersionableTag() {
        return imageTag != null;
    }

    /** The full reference as the platform expressed it, for user-facing reports. */
    public String imageReference() {
        if (imageTag != null) {
            return image + ":" + imageTag;
        }
        return digest != null ? image + "@" + digest : image;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConnectorException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
