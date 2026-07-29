package dev.tower.application.sync;

/**
 * Something running that Tower could see but could not attribute (FR-060, ADR-012).
 *
 * <p>ADR-012 forbids guessing. A workload whose image matches no binding, or
 * whose tag no version pattern recognises, is reported as this rather than
 * attributed to an Application on inference — an Observation with false
 * provenance is immutable once stored and therefore worse than no Observation.
 *
 * <p>Silently dropping it would be worse still. A thing running in an
 * Environment that Tower cannot explain is information the user wants: it means
 * either a binding is missing or something is deployed that nobody expected.
 *
 * @param scope          where it was seen, in the platform's terms
 * @param name           what the platform called it
 * @param imageReference the image, so the user can write a binding for it
 * @param reason         why Tower could not attribute it, in plain words
 */
public record UnrecognizedWorkload(String scope, String name, String imageReference, String reason) {

    /** No binding names this image. */
    public static UnrecognizedWorkload noBinding(String scope, String name, String imageReference) {
        return new UnrecognizedWorkload(scope, name, imageReference,
                "No Application is bound to this image.");
    }

    /** A binding matched the image, but its pattern did not recognise the tag. */
    public static UnrecognizedWorkload tagNotMatched(
            String scope, String name, String imageReference, String versionPattern) {
        return new UnrecognizedWorkload(scope, name, imageReference,
                "The tag does not match the version pattern " + versionPattern + ".");
    }

    /**
     * The platform reported a digest rather than a tag, so there is no version
     * to derive. Common on OpenShift, where an ImageStream trigger rewrites the
     * image.
     */
    public static UnrecognizedWorkload digestPinned(String scope, String name, String imageReference) {
        return new UnrecognizedWorkload(scope, name, imageReference,
                "The image is pinned to a digest, so it carries no version.");
    }
}
