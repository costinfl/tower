package dev.tower.connector.k8s;

/**
 * Splits a container image reference into the parts a binding matches against.
 *
 * <p>An image reference is one of:
 *
 * <pre>
 *   registry.example/acme/api:2026.08.1        tagged
 *   registry.example/acme/api@sha256:abc...    digest-pinned
 *   registry.example:5000/acme/api:2026.08.1   registry with a port
 *   registry.example/acme/api                  neither, meaning "latest"
 * </pre>
 *
 * <p>The port case is why this is not a {@code split(":")}. A registry host may
 * carry a port, so the last colon is only a tag separator when it appears after
 * the final slash.
 *
 * <p>The digest case is why this exists at all. OpenShift ImageStream triggers
 * rewrite a workload's image to a digest, and a digest yields no version — see
 * {@code RunningWorkload}, which reports such workloads rather than failing.
 *
 * @param name   the reference without tag or digest
 * @param tag    the tag, or null when digest-pinned
 * @param digest the digest, or null when tagged
 */
record ImageReference(String name, String tag, String digest) {

    /** What Kubernetes means by an image with no tag. */
    private static final String IMPLICIT_TAG = "latest";

    static ImageReference parse(String image) {
        String reference = image == null ? "" : image.trim();
        if (reference.isEmpty()) {
            return new ImageReference("", null, null);
        }

        int digestSeparator = reference.indexOf('@');
        if (digestSeparator >= 0) {
            return new ImageReference(
                    reference.substring(0, digestSeparator),
                    null,
                    reference.substring(digestSeparator + 1));
        }

        int lastColon = reference.lastIndexOf(':');
        int lastSlash = reference.lastIndexOf('/');
        if (lastColon > lastSlash) {
            return new ImageReference(
                    reference.substring(0, lastColon), reference.substring(lastColon + 1), null);
        }

        // No tag written. Kubernetes resolves this to "latest", and saying so is
        // more honest than reporting no tag at all: the platform really is
        // running something the user called latest.
        return new ImageReference(reference, IMPLICIT_TAG, null);
    }
}
