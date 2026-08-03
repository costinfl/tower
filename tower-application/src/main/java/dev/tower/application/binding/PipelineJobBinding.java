package dev.tower.application.binding;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;

/**
 * Which pipeline job's runs say that an Application reached an Environment
 * (ADR-012, ADR-020, FR-079).
 *
 * <p>The widest binding in Tower, and it has to be. Every other one maps one
 * Tower concept to one vendor locator; this maps two — a run of this job means
 * <em>this</em> Application arrived in <em>that</em> Environment — and then has
 * to say where in the run the version is written, which no Connector can infer.
 *
 * <p>That last part is the whole reason {@link VersionSource} exists rather than
 * a single assumed convention. A CI system used well records the version in a
 * build parameter. A CI system used as most of them actually are records it in
 * whatever place somebody found convenient years ago: the run's own name, or the
 * job's path, or a parameter named something nobody would guess. Tower cannot
 * fix that, and a binding that assumed one arrangement would simply not work for
 * teams who chose another. So the arrangement is configuration.
 *
 * <p>What it will not do is read the console log. ADR-020 refuses that: it would
 * make Tower's correctness depend on log formatting, and a wrong parse produces
 * Observations that are immutable and therefore permanent.
 *
 * <p>{@code versionPattern} works exactly as it does in {@link RepositoryBinding}
 * and {@link ApplicationBinding} — a regular expression whose first capturing
 * group is the version. ADR-012's warning applies here with more force than
 * anywhere else, because a run's text is messier than a git tag: a wrong pattern
 * produces wrong Application Versions, and Observations survive the correction.
 *
 * <p>No credential is held here. It lives in the credential store keyed by this
 * Connector and this {@code system} (NFR-028), so one token serves every job on
 * the same CI server rather than being copied per binding.
 *
 * @param environmentId  the Environment a successful run puts something into
 * @param applicationId  the Application it puts there
 * @param connectorId    the Connector that can read the system
 * @param system         where the CI system lives; also the credential's target
 * @param job            the job within it, as that system names it
 * @param versionSource  where in a run the Application Version is written
 * @param versionKey     the name to read, when the source is a named value
 * @param versionPattern how to extract the version from whatever that yields
 */
public record PipelineJobBinding(
        EnvironmentId environmentId,
        ApplicationId applicationId,
        String connectorId,
        String system,
        String job,
        VersionSource versionSource,
        String versionKey,
        String versionPattern) {

    /** Treats whatever the source yields as the Application Version. */
    public static final String WHOLE_VALUE = "^(.+)$";

    /**
     * Where a run records the version it deployed.
     *
     * <p>Deliberately a small closed set rather than an expression language. Each
     * of these is somewhere a real team has been observed to keep it, and every
     * one of them is a place a machine can read without guessing.
     */
    public enum VersionSource {

        /** A named value the run carried — a parameter, an input, a variable. */
        PARAMETER,

        /** The run's own name, where the system lets a run be named. */
        RUN_NAME,

        /**
         * The job's path.
         *
         * <p>For the arrangement where a job exists per version, which is a thing
         * that happens. Nothing about the run is read at all — every run of such a
         * job deploys the same version, and the pattern extracts it from the path.
         */
        JOB_PATH;

        public static VersionSource parse(String value) {
            if (value == null || value.isBlank()) {
                return PARAMETER;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("\"" + value + "\" is not a version source."
                        + " Use PARAMETER, RUN_NAME or JOB_PATH.");
            }
        }

        /** Whether this source needs a name to read it by. */
        public boolean needsKey() {
            return this == PARAMETER;
        }
    }

    public PipelineJobBinding {
        InvalidRequestException.require(environmentId != null,
                "A pipeline job binding must name the Environment its runs deploy to.");
        InvalidRequestException.require(applicationId != null,
                "A pipeline job binding must name the Application its runs deploy.");
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
     * <p>Empty is an answer rather than a failure, as it is for a git ref: it
     * means this binding does not recognise a version there. The caller reports
     * it (FR-080) rather than discarding it, because a user whose pattern is
     * wrong needs to see what it failed on.
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
                "A pipeline job binding must name its " + what + ".");
        return value.trim();
    }
}
