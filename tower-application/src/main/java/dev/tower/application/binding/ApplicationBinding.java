package dev.tower.application.binding;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import dev.tower.application.service.InvalidRequestException;
import dev.tower.domain.application.ApplicationId;

/**
 * How to recognise an Application, and its version, in what a platform reports
 * (ADR-012, FR-056).
 *
 * <p>A platform reports an image and a tag. Which image is the Application named
 * Customer API, and which part of the tag is the Application Version, are
 * conventions the team holds — ADR-012 records that Tower cannot discover them
 * and must not guess, because a wrong guess produces an Observation with false
 * provenance that is immutable once stored.
 *
 * <p>{@code versionPattern} is a regular expression with at least one capturing
 * group; the first group is the Application Version. The default {@code ^(.+)$}
 * treats the whole tag as the version, which is right for teams that tag with
 * the version alone.
 *
 * <p>The pattern is compiled here rather than on every match, and a pattern that
 * does not compile is rejected at the point of saving. That matters more than it
 * looks: the alternative is discovering it during a synchronization run, where
 * the failure is remote from the mistake.
 *
 * @param applicationId  the Application this recognises
 * @param connectorId    the Connector whose reports it applies to
 * @param image          the image reference, without a tag
 * @param versionPattern how to extract the version from the tag
 */
public record ApplicationBinding(
        ApplicationId applicationId, String connectorId, String image, String versionPattern) {

    /** Treats the whole tag as the Application Version. */
    public static final String WHOLE_TAG = "^(.+)$";

    public ApplicationBinding {
        InvalidRequestException.require(applicationId != null,
                "A binding must name the Application it recognises.");
        connectorId = requireText(connectorId, "connectorId");
        image = requireText(image, "image");
        versionPattern = validPattern(versionPattern);
    }

    /**
     * The Application Version this tag denotes, or empty when the tag does not
     * match.
     *
     * <p>Empty is a normal answer, not a failure: it means this binding does not
     * recognise that tag, and the workload is reported as unrecognized (FR-060)
     * rather than attributed on a guess.
     */
    public Optional<String> resolveVersion(String imageTag) {
        if (imageTag == null || imageTag.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(versionPattern).matcher(imageTag.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String version = matcher.group(1);
        return version == null || version.isBlank() ? Optional.empty() : Optional.of(version);
    }

    /** Whether this binding applies to an image the platform reported. */
    public boolean matchesImage(String reportedImage) {
        return reportedImage != null && image.equals(reportedImage.trim());
    }

    private static String validPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return WHOLE_TAG;
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
                        + "for example ^release-(.+)$. Use " + WHOLE_TAG + " to treat the whole tag as the version.");
        return trimmed;
    }

    private static String requireText(String value, String what) {
        InvalidRequestException.require(value != null && !value.isBlank(),
                "A binding must name its " + what + ".");
        return value.trim();
    }
}
