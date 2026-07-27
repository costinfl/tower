package dev.tower.application.port.in;

import dev.tower.domain.application.Application;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;
import dev.tower.domain.application.ApplicationVersionId;

import java.util.List;

/**
 * Inbound port for the Application and Application Version registry
 * (issue #14, gap G2).
 *
 * <p>FR-003, FR-011, FR-014 and FR-015 all presume Applications and their
 * Versions exist without specifying how they come to. G2 recorded that gap.
 *
 * <p>BR-01 makes an Application Version immutable, so there is no update for
 * one: recording a correction means registering a different version.
 */
public interface ApplicationUseCases {

    Application register(RegisterApplication command);

    Application update(UpdateApplication command);

    List<Application> list();

    Application get(ApplicationId id);

    /** Refused while the Application still has Versions. */
    void delete(ApplicationId id);

    ApplicationVersion registerVersion(RegisterVersion command);

    List<ApplicationVersion> listVersions();

    List<ApplicationVersion> listVersionsOf(ApplicationId applicationId);

    ApplicationVersion getVersion(ApplicationVersionId id);

    /** Refused while any Release Pack contains the Version. */
    void deleteVersion(ApplicationVersionId id);

    record RegisterApplication(String name, String description) {}

    record UpdateApplication(ApplicationId id, String name, String description) {}

    record RegisterVersion(ApplicationId applicationId, String version, String branch,
                           String tag, String commit, String buildIdentifier) {}
}
