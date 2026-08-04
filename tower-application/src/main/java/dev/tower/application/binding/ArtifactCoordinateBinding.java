package dev.tower.application.binding;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.AcceptedArtifact;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.application.ApplicationVersion;

/**
 * How to address one kind of artifact an Application Version produced (ADR-012,
 * ADR-021, FR-083).
 *
 * <p>ADR-012's shape running the other way. A version pattern
 * ({@link RepositoryBinding}, {@link ApplicationBinding}, {@link PipelineJobBinding})
 * <em>extracts</em> an Application Version from a string a vendor produced; this
 * <em>composes</em> a string a vendor will recognise from an Application Version
 * Tower already holds. The two look alike enough that ADR-021 records the
 * symmetry explicitly, so that a later reader does not take one for a mistaken
 * copy of the other.
 *
 * <p>Composing is possible at all only because of a convention the team keeps: an
 * image and a chart tagged with the version and the short commit make the
 * coordinate a function of two fields {@link ApplicationVersion} has carried
 * since Milestone 1. Where that convention does not hold, Tower would have to
 * search for a coordinate it cannot derive, and the capable searches these
 * repositories offer are POSTs that ADR-001 forbids.
 *
 * <p>{@code kind} is the team's own word — "image", "chart", "installer" — and
 * Tower never interprets it. It is the same restraint ADR-018 applies to a
 * tracker's status: the label belongs to the people who chose it, and a Tower
 * that decided which kinds exist would be wrong for the first team that had a
 * fourth.
 *
 * <p>The failure mode here is unusually mild, and ADR-021 argues that this is the
 * point. A wrong version pattern produces wrong Observations, which are immutable
 * and outlive the correction; a wrong template produces a false "not found",
 * which is visible, harmless, and fixed by correcting the template. Nothing is
 * written, so nothing survives the mistake.
 *
 * <p>No credential is held here. It lives in the credential store keyed by this
 * Connector and this {@code system} (NFR-028), as it does for a pipeline job.
 *
 * @param applicationId      whose artifacts these are
 * @param connectorId        the Connector that can read the repository
 * @param kind               the team's word for what this template addresses
 * @param system             where the repository lives; also the credential's target
 * @param coordinateTemplate the vendor locator, with {@code {version}},
 *                           {@code {commit}} and {@code {shortCommit}} standing
 *                           in for what Tower holds
 * @param shortCommitLength  how many characters of the commit {@code {shortCommit}}
 *                           takes
 */
public record ArtifactCoordinateBinding(
        ApplicationId applicationId,
        String connectorId,
        String kind,
        String system,
        String coordinateTemplate,
        int shortCommitLength) {

    /**
     * What {@code git rev-parse --short} gives by default.
     *
     * <p>Configurable rather than fixed, because that default is not a guarantee:
     * git lengthens it where seven characters would be ambiguous, and a pipeline
     * may have pinned a different length years ago. A team whose build uses
     * another length and does not say so here will see every artifact reported
     * absent — which is the mild failure described above, and is why it is safe
     * to have a default at all.
     */
    public static final int DEFAULT_SHORT_COMMIT_LENGTH = 7;

    /** A git object name is forty hexadecimal characters; shorter is meaningless below four. */
    private static final int MIN_SHORT_COMMIT_LENGTH = 4;
    private static final int MAX_SHORT_COMMIT_LENGTH = 40;

    public static final String VERSION = "version";
    public static final String COMMIT = "commit";
    public static final String SHORT_COMMIT = "shortCommit";

    private static final Set<String> TOKENS = Set.of(VERSION, COMMIT, SHORT_COMMIT);

    /** Matches {@code {anything}}, including the tokens nobody meant to write. */
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]*)\\}");

    public static final int KIND_MAX_LENGTH = 50;

    public ArtifactCoordinateBinding {
        InvalidRequestException.require(applicationId != null,
                "An artifact coordinate binding must name the Application whose artifacts these are.");
        connectorId = requireText(connectorId, "connectorId");
        kind = normaliseKind(requireText(kind, "artifact kind"));
        InvalidRequestException.require(kind.length() <= KIND_MAX_LENGTH,
                "An artifact kind must be at most " + KIND_MAX_LENGTH + " characters.");
        system = requireText(system, "repository system");
        coordinateTemplate = validTemplate(coordinateTemplate);
        shortCommitLength = shortCommitLength == 0 ? DEFAULT_SHORT_COMMIT_LENGTH : shortCommitLength;
        InvalidRequestException.require(
                shortCommitLength >= MIN_SHORT_COMMIT_LENGTH && shortCommitLength <= MAX_SHORT_COMMIT_LENGTH,
                "The short commit length must be between " + MIN_SHORT_COMMIT_LENGTH + " and "
                        + MAX_SHORT_COMMIT_LENGTH + " characters.");
    }

    /**
     * The coordinate this version's artifact would have, or empty when the
     * version does not carry what the template asks for.
     *
     * <p>Empty is an answer rather than a failure, exactly as it is for a version
     * pattern that did not match: a template naming {@code {commit}} cannot be
     * composed for a version registered without one, and that is a thing to
     * report to a user rather than an error to raise. Composing something with a
     * hole in it would be worse — it would ask the repository a question about a
     * coordinate nobody's build ever wrote.
     */
    public Optional<String> compose(ApplicationVersion version) {
        if (version == null) {
            return Optional.empty();
        }
        StringBuilder composed = new StringBuilder();
        Matcher matcher = TOKEN.matcher(coordinateTemplate);
        int cursor = 0;
        while (matcher.find()) {
            composed.append(coordinateTemplate, cursor, matcher.start());
            String value = valueOf(matcher.group(1), version);
            if (value == null) {
                return Optional.empty();
            }
            composed.append(value);
            cursor = matcher.end();
        }
        composed.append(coordinateTemplate.substring(cursor));
        return Optional.of(composed.toString());
    }

    /** Which of the version's fields this template needs, for a readable report. */
    public Set<String> requiredFields() {
        Set<String> required = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(coordinateTemplate);
        while (matcher.find()) {
            required.add(matcher.group(1));
        }
        return required;
    }

    private String valueOf(String token, ApplicationVersion version) {
        return switch (token) {
            case VERSION -> version.version();
            case COMMIT -> version.commit();
            case SHORT_COMMIT -> shorten(version.commit());
            // Unreachable: validTemplate rejects an unknown token at binding
            // time, which is the point of rejecting it there.
            default -> null;
        };
    }

    private String shorten(String commit) {
        if (commit == null || commit.isBlank()) {
            return null;
        }
        String trimmed = commit.trim();
        return trimmed.length() <= shortCommitLength ? trimmed : trimmed.substring(0, shortCommitLength);
    }

    /**
     * A template whose tokens Tower recognises.
     *
     * <p>An unrecognised token is refused rather than left to stand for itself.
     * {@code {shortcommit}} written with a small c would otherwise become a
     * literal in the coordinate, the repository would answer that no such
     * artifact exists, and the report would be truthful and useless. Refusing it
     * here turns a mystery into a message.
     */
    private static String validTemplate(String template) {
        InvalidRequestException.require(template != null && !template.isBlank(),
                "An artifact coordinate binding must carry a template, "
                        + "for example docker-local/acme/api:{version}-{shortCommit}.");
        String trimmed = template.trim();

        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(trimmed);
        boolean any = false;
        while (matcher.find()) {
            any = true;
            if (!TOKENS.contains(matcher.group(1))) {
                unknown.add(matcher.group(1));
            }
        }
        InvalidRequestException.require(unknown.isEmpty(),
                "The coordinate template uses " + describe(unknown)
                        + " Tower does not recognise. Use {version}, {commit} or {shortCommit}.");
        InvalidRequestException.require(any,
                "The coordinate template names no part of the version, so every version would "
                        + "resolve to the same artifact. Use at least one of {version}, {commit} "
                        + "or {shortCommit}.");
        return trimmed;
    }

    private static String describe(Set<String> unknown) {
        String names = unknown.stream().map(name -> "{" + name + "}")
                .reduce((a, b) -> a + ", " + b).orElse("");
        return unknown.size() == 1 ? "a token " + names + " that" : "tokens " + names + " that";
    }

    /**
     * Two kinds differing only in case are the same kind.
     *
     * <p>Kinds are keys a person types twice — once when binding, once when
     * reading a report — and "Image" beside "image" would be two rows saying the
     * same thing.
     *
     * <p>Delegates to {@link AcceptedArtifact}, which owns the rule. Two
     * definitions of "the same kind" would eventually disagree, and the one that
     * decides whether a document finds its accepted digest is that one.
     */
    public static String normaliseKind(String kind) {
        return AcceptedArtifact.normaliseKind(kind);
    }

    private static String requireText(String value, String what) {
        InvalidRequestException.require(value != null && !value.isBlank(),
                "An artifact coordinate binding must name its " + what + ".");
        return value.trim();
    }
}
