package dev.tower.application.binding;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;

/**
 * Which repository an Application's code lives in, and which refs in it name a
 * version (ADR-012, ADR-014).
 *
 * <p>The sibling of {@link ApplicationBinding}. That one recognises an
 * Application in what a Deployment Platform reports; this one recognises it in
 * what source control holds. Both exist for the same reason ADR-012 gives: the
 * correspondence between a Tower concept and a vendor's locator is a convention
 * the team holds, and Tower must not guess it.
 *
 * <p>{@code refSelection} matters more here than it might look. Most teams tag
 * releases, some cut release branches, and reading both when a team only uses one
 * fills the candidate list with noise — every feature branch offered as a version.
 * Tags are the default because that is the common case, not because branches are
 * wrong.
 *
 * <p>{@code versionPattern} works exactly as {@link ApplicationBinding}'s does: a
 * regular expression with at least one capturing group, the first of which is the
 * version. The tag {@code v2.5.0} with {@code ^v(.+)$} yields {@code 2.5.0}.
 *
 * <p>No credential is held here. The repository may be public, and when it is not
 * the secret lives in the credential store keyed by this Connector and this URL
 * (NFR-028), never in the database beside the configuration that points at it.
 *
 * @param applicationId  the Application whose code this repository holds
 * @param connectorId    the Connector that can read it
 * @param repositoryUrl  the git remote, per ADR-014 — the vendor is whatever this points at
 * @param refSelection   which refs are candidates
 * @param versionPattern how to extract the version from a ref name
 */
public record RepositoryBinding(
        ApplicationId applicationId,
        String connectorId,
        String repositoryUrl,
        RefSelection refSelection,
        String versionPattern) {

    /** Treats the whole ref name as the Application Version. */
    public static final String WHOLE_REF = "^(.+)$";

    /**
     * Which refs a binding offers as candidate versions.
     *
     * <p>Deliberately not {@code SourceRef.Kind} from the connector SPI: the
     * application layer may not name a Connector type (CM-03, ADR-003), and this
     * is a user's configuration choice rather than a thing a Connector reported.
     */
    public enum RefSelection {
        TAGS,
        BRANCHES,
        ALL;

        public static RefSelection parse(String value) {
            if (value == null || value.isBlank()) {
                return TAGS;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException(
                        "\"" + value + "\" is not a ref selection. Use TAGS, BRANCHES or ALL.");
            }
        }

        public boolean includesTags() {
            return this == TAGS || this == ALL;
        }

        public boolean includesBranches() {
            return this == BRANCHES || this == ALL;
        }
    }

    public RepositoryBinding {
        InvalidRequestException.require(applicationId != null,
                "A repository binding must name the Application whose code it holds.");
        connectorId = requireText(connectorId, "connectorId");
        repositoryUrl = requireText(repositoryUrl, "repository URL");
        refSelection = refSelection == null ? RefSelection.TAGS : refSelection;
        versionPattern = validPattern(versionPattern);
    }

    /**
     * The Application Version this ref name denotes, or empty when it does not
     * match.
     *
     * <p>Empty is a normal answer rather than a failure: it means this binding
     * does not treat that ref as a version, which is how a {@code main} branch or
     * a {@code sandbox} tag is passed over without anyone being told something
     * went wrong.
     */
    public Optional<String> resolveVersion(String refName) {
        if (refName == null || refName.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(versionPattern).matcher(refName.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String version = matcher.group(1);
        return version == null || version.isBlank() ? Optional.empty() : Optional.of(version);
    }

    private static String validPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return WHOLE_REF;
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
                        + "for example ^v(.+)$. Use " + WHOLE_REF + " to treat the whole ref name as the version.");
        return trimmed;
    }

    private static String requireText(String value, String what) {
        InvalidRequestException.require(value != null && !value.isBlank(),
                "A repository binding must name its " + what + ".");
        return value.trim();
    }
}
