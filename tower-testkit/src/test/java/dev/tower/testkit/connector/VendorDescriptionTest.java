package dev.tower.testkit.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The check that proves the check (ADR-019).
 *
 * <p>A validator that passes everything is worse than none: it is a green tick
 * that means nothing, and everybody stops looking at it. So this feeds it bodies
 * that are deliberately wrong and requires it to say so.
 *
 * <p>The corruptions are made through the JSON tree and asserted to have changed
 * something, rather than by replacing text. The first draft of this class did use
 * text replacement, against a number the fixture did not contain — so it validated
 * an untouched body and failed only because it had expected a failure. A
 * corruption that silently does not happen is exactly how a suite like this rots.
 */
@DisplayName("Checking a fixture against the vendor's own description")
class VendorDescriptionTest {

    private static final String GITHUB = "specs/github/api.github.com.slice.json";
    private static final String ISSUE = "/repos/acme/retail/issues/42";
    private static final ObjectMapper JSON = new ObjectMapper();

    private VendorDescription github() {
        Optional<VendorDescription> found = VendorDescription.at(GITHUB);
        assumeTrue(found.isPresent(), VendorDescription.absenceOf(GITHUB));
        return found.get();
    }

    /** A response recorded from the real GitHub, as the Connector's tests serve it. */
    private String recordedIssue() {
        try {
            return Files.readString(Path.of(
                    "../tower-connector-github/src/test/resources/fixtures/issue.json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The recorded body with one deliberate change, asserted to have happened. */
    private String corrupted(Consumer<ObjectNode> change) {
        try {
            ObjectNode issue = (ObjectNode) JSON.readTree(recordedIssue());
            String before = JSON.writeValueAsString(issue);
            change.accept(issue);
            String after = JSON.writeValueAsString(issue);
            assertThat(after)
                    .as("the corruption must actually change the body, or this test proves nothing")
                    .isNotEqualTo(before);
            return after;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void accepts_a_body_recorded_from_the_real_api() {
        github().requireResponseMatches(ISSUE, 200, recordedIssue());
    }

    @Test
    void rejects_a_body_missing_something_the_vendor_says_is_always_there() {
        String withoutUrl = corrupted(issue -> {
            JsonNode url = issue.remove("html_url");
            assertThat(url).as("the fixture should have had html_url to remove").isNotNull();
        });

        assertThatThrownBy(() -> github().requireResponseMatches(ISSUE, 200, withoutUrl))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("html_url");
    }

    @Test
    void rejects_a_field_whose_type_is_wrong() {
        // The mistake a hand-written fixture makes most easily: a number written
        // as text, because that is how it looked in the documentation.
        String numberAsText = corrupted(issue -> issue.put("number", "42"));

        assertThatThrownBy(() -> github().requireResponseMatches(ISSUE, 200, numberAsText))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("number");
    }

    @Test
    void rejects_a_field_nested_where_the_vendor_does_not_nest_it() {
        // The mistake I would make writing a fixture from memory: right field,
        // wrong place.
        String movedState = corrupted(issue -> {
            issue.remove("state");
            issue.putObject("fields").put("state", "open");
        });

        assertThatThrownBy(() -> github().requireResponseMatches(ISSUE, 200, movedState))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void says_which_description_and_which_operation_when_it_fails() {
        // A failure a reader can act on names the file it came from, not only
        // that something did not match.
        String broken = corrupted(issue -> issue.remove("html_url"));

        assertThatThrownBy(() -> github().requireResponseMatches(ISSUE, 200, broken))
                .hasMessageContaining("api.github.com.slice.json")
                .hasMessageContaining(ISSUE)
                .hasMessageContaining("check-openapi-drift.sh");
    }

    @Test
    void reports_a_description_that_is_not_committed_rather_than_pretending() {
        assertThat(VendorDescription.at("specs/nowhere/absent.json")).isEmpty();
        assertThat(VendorDescription.absenceOf("specs/nowhere/absent.json"))
                .contains("is not committed")
                .contains("specs/README.md");
    }
}
