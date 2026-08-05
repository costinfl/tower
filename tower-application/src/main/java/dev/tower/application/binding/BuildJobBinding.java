package dev.tower.application.binding;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import dev.tower.application.binding.PipelineJobBinding.VersionSource;
import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;

/**
 * Which pipeline job's runs <em>build</em> an Application (ADR-012, ADR-020).
 *
 * <p>The sibling of {@link PipelineJobBinding}, and the difference between them
 * is the whole of ADR-020: that one names an Environment, this one does not. A
 * build says "version 2.5.0 was produced from this job". That is not a fact
 * about any Environment, so there is nothing to put on the left but the
 * Application.
 *
 * <p>Which is also why the two cannot be one binding with a nullable Environment.
 * A null there would have to mean "this run deployed nothing anywhere", and every
 * query over deployments would then have to remember to exclude it. Worse, the
 * primary key would stop being a key: two build jobs for one Application are
 * ordinary, and V12's table exists precisely to forbid two <em>deployment</em>
 * jobs for one pair.
 *
 * <p>What a run of this produces is a <em>candidate</em> Application Version,
 * proposed and stored nowhere — the footing ADR-014 established for a git ref,
 * and exactly what source control discovery already does. BR-01 makes an
 * Application Version immutable, so one created without anybody asking would be
 * immutable too.
 *
 * <p>{@link VersionSource} and {@code versionPattern} are reused from the
 * deployment binding unchanged, and reused rather than copied: a build job keeps
 * its version in the same small set of places a deployment job does, and two
 * enumerations with identical members would drift the first time one gained a
 * fourth.
 *
 * <p>No credential is held here. It lives in the credential store keyed by this
 * Connector and this {@code system} (NFR-028), so one token serves every job on
 * the same CI server — including the deployment jobs bound beside these.
 *
 * @param applicationId  the Application this job builds
 * @param connectorId    the Connector that can read the system
 * @param system         where the CI system lives; also the credential's target
 * @param job            the job within it, as that system names it
 * @param versionSource  where in a run the Application Version is written
 * @param versionKey     the name to read, when the source is a named value
 * @param versionPattern how to extract the version from whatever that yields
 */
public record BuildJobBinding(
        ApplicationId applicationId,
        String connectorId,
        String system,
        String job,
        VersionSource versionSource,
        String versionKey,
        String versionPattern) {

    /** Treats whatever the source yields as the Application Version. */
    public static final String WHOLE_VALUE = PipelineJobBinding.WHOLE_VALUE;

    public BuildJobBinding {
        InvalidRequestException.require(applicationId != null,
                "A build job binding must name the Application its runs build.");
        connectorId = requireText(connectorId, "connectorId");
        system = requireText(system, "CI system");
        job = requireText(job, "job");
        versionSource = versionSource == null ? VersionSource.PARAMETER : versionSource;
        versionKey = versionKey == null ? "" : versionKey.trim();
        InvalidRequestException.require(!versionSource.needsKey() || !versionKey.isBlank(),
                "Reading the version from a parameter needs the parameter's name.");
        versionPattern = validPattern(versionPattern);
    }

    /**
     * The Application Version a run's text denotes, or empty when it does not
     * match.
     *
     * <p>Empty is an answer rather than a failure, and here it costs less than
     * anywhere else in Tower: a build run Tower cannot attribute produces no
     * candidate, and a candidate is a proposal a person accepts or ignores.
     * Nothing is written either way, so a wrong pattern here is corrected rather
     * than outlived — unlike the same mistake on a deployment binding, where
     * ADR-012's warning bites and the Observations survive.
     */
    public Optional<String> resolveVersion(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(versionPattern).matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String version = matcher.group(1);
        return version == null || version.isBlank() ? Optional.empty() : Optional.of(version);
    }

    private static String validPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return WHOLE_VALUE;
        }
        String trimmed = pattern.trim();
        Pattern compiled;
        try {
            compiled = Pattern.compile(trimmed);
        } catch (PatternSyntaxException e) {
            throw new InvalidRequestException(
                    "The version pattern is not a valid regular expression: " + e.getDescription() + ".");
        }
        InvalidRequestException.require(compiled.matcher("").groupCount() >= 1,
                "The version pattern must contain a capturing group marking the version, "
                        + "for example ^release-(.+)$. Use " + WHOLE_VALUE
                        + " to treat the whole value as the version.");
        return trimmed;
    }

    private static String requireText(String value, String what) {
        InvalidRequestException.require(value != null && !value.isBlank(),
                "A build job binding must name its " + what + ".");
        return value.trim();
    }
}
