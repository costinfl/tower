package dev.tower.api.observation;

import dev.tower.domain.application.ApplicationVersion;

/**
 * Resolved Application Version detail embedded in {@link EnvironmentStateView}, carrying every
 * identifying attribute (Glossary.md) so the Viewer can show what is deployed without a follow-up
 * round trip.
 */
public record ApplicationVersionDetailView(
        String id, String version, String branch, String tag, String commit, String buildIdentifier) {

    public static ApplicationVersionDetailView from(ApplicationVersion version) {
        return new ApplicationVersionDetailView(version.id().toString(), version.version(), version.branch(),
                version.tag(), version.commit(), version.buildIdentifier());
    }
}
