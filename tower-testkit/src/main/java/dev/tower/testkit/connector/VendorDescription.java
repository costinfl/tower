package dev.tower.testkit.connector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * The vendor's own description of their API, used to check our fixtures against
 * something we did not write (ADR-019).
 *
 * <p>The problem this exists for: a canned response written by hand and a
 * Connector written by hand carry the same beliefs about the vendor. If a field
 * name was guessed wrong, both are wrong the same way, and the test passes.
 * Implementation-Plan.md already put it as well as it can be put — "a test
 * written against our own Connector could only confirm what we already
 * intended". A vendor's published description is the cheapest opinion available
 * that is not ours.
 *
 * <p>What it does not establish: that the vendor's server answers the way the
 * vendor's description says. Descriptions drift from implementations. The live
 * checks in CHECKLIST.md are what settle that, and they stay outstanding however
 * green this is.
 *
 * <p>Absent by design rather than by accident. A description that could not be
 * vendored — Atlassian's, at the time of writing — leaves {@link #at} empty and
 * a test skipping with a stated reason, which is honest. What it must never do
 * is pass quietly.
 */
public final class VendorDescription {

    private final Path slice;
    private final OpenApiInteractionValidator validator;

    private VendorDescription(Path slice, OpenApiInteractionValidator validator) {
        this.slice = slice;
        this.validator = validator;
    }

    /**
     * The description at this path, or empty when it is not committed.
     *
     * <p>Paths are resolved from the repository root, found by walking up from the
     * working directory, so a test reads the same file whether Maven ran it from
     * the reactor root or from inside the module.
     */
    public static Optional<VendorDescription> at(String repositoryRelativePath) {
        Path slice = repositoryRoot().resolve(repositoryRelativePath);
        if (!Files.isRegularFile(slice)) {
            return Optional.empty();
        }
        return Optional.of(new VendorDescription(slice,
                OpenApiInteractionValidator.createFor(read(slice)).build()));
    }

    /** Why a test that wanted this description is being skipped. */
    public static String absenceOf(String repositoryRelativePath) {
        return repositoryRelativePath + " is not committed, so these fixtures are checked"
                + " against nobody's description but our own. specs/README.md says how to"
                + " produce it and what to check before committing it.";
    }

    /**
     * Fails unless this body is what the vendor says that operation answers.
     *
     * @param pathTemplate the path as the description writes it, braces and all
     * @param status       the status the fixture stands for
     * @param body         the fixture, exactly as the fake server would serve it
     */
    public void requireResponseMatches(String pathTemplate, int status, String body) {
        ValidationReport report = validator.validateResponse(
                pathTemplate, Request.Method.GET,
                SimpleResponse.Builder.status(status)
                        .withContentType("application/json")
                        .withBody(body)
                        .build());

        if (report.hasErrors()) {
            throw new AssertionError("This fixture is not what " + slice.getFileName()
                    + " says " + pathTemplate + " answers with " + status + ":\n"
                    + report.getMessages().stream()
                            .map(VendorDescription::describe)
                            .collect(Collectors.joining("\n"))
                    + "\n\nEither the fixture is wrong, or the vendor changed their description."
                    + " scripts/check-openapi-drift.sh tells the two apart.");
        }
    }

    /**
     * One violation, with the field it is about.
     *
     * <p>The library's own message is "string found, integer expected", which
     * against a six-kilobyte body is close to useless. The pointer that says
     * <em>which</em> field is in the message's context rather than its text, so it
     * is put back where a reader will look.
     */
    private static String describe(ValidationReport.Message message) {
        String where = message.getContext()
                .flatMap(ValidationReport.MessageContext::getPointers)
                .map(ValidationReport.MessageContext.Pointers::getInstance)
                .map(pointer -> " at " + pointer)
                .orElse("");
        return "  - " + message.getMessage() + where;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    /**
     * The repository root.
     *
     * <p>Found by looking for the directory holding {@code specs}, rather than
     * assumed to be the working directory: Surefire runs with the module as its
     * working directory, and a relative path would resolve differently depending
     * on whether the whole reactor or one module was built.
     */
    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("specs"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not find the repository root: no directory above "
                        + Path.of("").toAbsolutePath() + " holds specs/.");
    }
}
