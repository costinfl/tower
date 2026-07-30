package dev.tower.connector.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.RepositoryLocator;
import dev.tower.connector.api.SourceRef;

/**
 * Issue #3, ADR-014.
 *
 * <p>Tested against a real git repository on disk rather than a mock. A local
 * repository is a valid git remote, so this exercises the actual JGit transport
 * and the actual ref advertisement — which is where the interesting behaviour
 * lives, particularly for annotated tags. No network, no credentials, no mock
 * server: that is a consequence of ADR-014 choosing the protocol over a vendor
 * API, and it is worth having.
 */
@DisplayName("The git Source Control Connector")
class GitSourceControlConnectorTest {

    private final GitSourceControlConnector connector = new GitSourceControlConnector();

    @TempDir
    Path tempDir;

    /** A repository with a branch, a lightweight tag and an annotated tag. */
    private Fixture repository() throws Exception {
        Path work = tempDir.resolve("repo");
        try (Git git = Git.init().setDirectory(work.toFile()).call()) {
            ObjectId first = git.commit()
                    .setAllowEmpty(true).setMessage("first").setSign(false)
                    .call().getId();

            git.branchCreate().setName("release/2.5").call();
            git.tag().setName("v2.5.0").setAnnotated(false).call();

            git.checkout().setName("release/2.5").call();
            ObjectId second = git.commit()
                    .setAllowEmpty(true).setMessage("second").setSign(false)
                    .call().getId();
            git.tag().setName("v2.5.1").setAnnotated(true).setMessage("release 2.5.1").call();

            return new Fixture(work, first, second);
        }
    }

    private record Fixture(Path work, ObjectId first, ObjectId second) {
        RepositoryLocator locator() {
            return new RepositoryLocator(work.toString());
        }
    }

    @Nested
    @DisplayName("reports branches and tags in Tower's vocabulary")
    class Reading {

        @Test
        void finds_every_branch_with_the_commit_it_points_at() throws Exception {
            Fixture fixture = repository();

            List<SourceRef> refs = connector.readRefs(fixture.locator(), ConnectorCredential.none());

            assertThat(refs).filteredOn(SourceRef::isBranch)
                    .extracting(SourceRef::name)
                    .containsExactly("master", "release/2.5");
            assertThat(refs).filteredOn(r -> r.name().equals("release/2.5"))
                    .extracting(SourceRef::commit)
                    .containsExactly(fixture.second().name());
        }

        @Test
        void strips_the_refs_prefix_because_it_is_git_bookkeeping() throws Exception {
            List<SourceRef> refs = connector.readRefs(repository().locator(), ConnectorCredential.none());

            assertThat(refs).extracting(SourceRef::name)
                    .noneMatch(name -> name.startsWith("refs/"));
        }

        @Test
        void finds_a_lightweight_tag() throws Exception {
            Fixture fixture = repository();

            List<SourceRef> refs = connector.readRefs(fixture.locator(), ConnectorCredential.none());

            assertThat(refs).filteredOn(r -> r.name().equals("v2.5.0"))
                    .singleElement()
                    .satisfies(ref -> {
                        assertThat(ref.isTag()).isTrue();
                        assertThat(ref.commit()).isEqualTo(fixture.first().name());
                    });
        }

        @Test
        void reports_an_annotated_tag_as_the_commit_rather_than_the_tag_object() throws Exception {
            // The case that makes this worth testing against a real repository.
            // An annotated tag points at a tag object, which points at the commit.
            // Reporting the tag object's id would give a hash that names the label
            // rather than the code, and would not match `git rev-parse` for anyone
            // checking Tower's work.
            Fixture fixture = repository();

            List<SourceRef> refs = connector.readRefs(fixture.locator(), ConnectorCredential.none());

            assertThat(refs).filteredOn(r -> r.name().equals("v2.5.1"))
                    .singleElement()
                    .satisfies(ref -> assertThat(ref.commit()).isEqualTo(fixture.second().name()));
        }

        @Test
        void does_not_report_head_as_a_ref() throws Exception {
            // A remote advertises HEAD as a pointer to whichever branch is
            // default. It is not a ref anyone would register a version from.
            List<SourceRef> refs = connector.readRefs(repository().locator(), ConnectorCredential.none());

            assertThat(refs).extracting(SourceRef::name).doesNotContain("HEAD");
        }

        @Test
        void returns_the_same_order_every_time() throws Exception {
            // JGit returns whatever the remote advertised. A caller comparing
            // today's refs against yesterday's must not see a difference that is
            // only the remote's ordering.
            Fixture fixture = repository();

            assertThat(connector.readRefs(fixture.locator(), ConnectorCredential.none()))
                    .isEqualTo(connector.readRefs(fixture.locator(), ConnectorCredential.none()));
        }

        @Test
        void reports_an_empty_repository_as_empty_rather_than_failing() throws Exception {
            Path work = tempDir.resolve("fresh");
            try (Git ignored = Git.init().setDirectory(work.toFile()).call()) {
                assertThat(connector.readRefs(
                        new RepositoryLocator(work.toString()), ConnectorCredential.none()))
                        .isEmpty();
            }
        }

        @Test
        void carries_no_build_identifier_because_git_has_none() throws Exception {
            // ADR-014: a build identifier comes from a build system and is not
            // recorded in a ref. This asserts the shape rather than a value —
            // there is nowhere on SourceRef to put one, which is the point.
            List<SourceRef> refs = connector.readRefs(repository().locator(), ConnectorCredential.none());

            assertThat(refs).isNotEmpty();
            assertThat(SourceRef.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactly("kind", "name", "commit");
        }
    }

    @Nested
    @DisplayName("names the repository when it cannot read one")
    class Failures {

        @Test
        void an_unreachable_remote_fails_with_the_locator_in_the_message() {
            var missing = new RepositoryLocator(tempDir.resolve("nothing-here").toString());

            assertThatThrownBy(() -> connector.readRefs(missing, ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("nothing-here");
        }

        @Test
        void the_locator_appears_once_and_not_twice() {
            // The duplication caught in Milestone 2 by reading a live response
            // rather than by any assertion: "Could not read X: Could not read X: …"
            var missing = new RepositoryLocator(tempDir.resolve("absent-repo").toString());

            assertThatThrownBy(() -> connector.readRefs(missing, ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(e -> {
                        String message = e.getMessage();
                        int first = message.indexOf("absent-repo");
                        assertThat(first).isNotNegative();
                        assertThat(message.indexOf("absent-repo", first + 1)).isNegative();
                    });
        }

        @Test
        void a_connection_test_reads_the_same_way_a_real_read_would() throws Exception {
            Fixture fixture = repository();

            // Succeeds against a repository that exists...
            connector.checkConnection(fixture.locator(), ConnectorCredential.none());

            // ...and fails against one that does not, rather than reporting
            // reachable because it probed something cheaper.
            var missing = new RepositoryLocator(tempDir.resolve("absent").toString());
            assertThatThrownBy(() -> connector.checkConnection(missing, ConnectorCredential.none()))
                    .isInstanceOf(ConnectorException.class);
        }
    }

    @Nested
    @DisplayName("handles credentials without leaking them")
    class Credentials {

        @Test
        void leaves_the_callers_credential_usable_after_a_call() throws Exception {
            // The connector zeroes its own copy. If it zeroed the caller's, a
            // second repository in the same synchronization run would fail
            // authentication for no visible reason.
            var credential = ConnectorCredential.bearerToken("a-secret-token".toCharArray());

            connector.readRefs(repository().locator(), credential);

            assertThat(credential.isPresent()).isTrue();
            assertThat(credential.token()).isEqualTo("a-secret-token".toCharArray());
        }

        @Test
        void never_puts_a_token_in_a_failure_message() {
            var credential = ConnectorCredential.bearerToken("a-secret-token".toCharArray());
            var missing = new RepositoryLocator(tempDir.resolve("absent").toString());

            assertThatThrownBy(() -> connector.readRefs(missing, credential))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(e -> assertThat(fullText(e)).doesNotContain("a-secret-token"));
        }

        /** The whole chain, since a cause is what usually carries the leak. */
        private String fullText(Throwable e) {
            StringBuilder text = new StringBuilder();
            for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
                text.append(t).append('\n');
            }
            return text.toString();
        }
    }

    @Test
    @DisplayName("identifies itself by protocol rather than by vendor")
    void connector_id_names_git() {
        // Calling it "github" would make a GitLab remote read as though it came
        // from GitHub, and would have to change the day someone points it
        // elsewhere — rewriting stored provenance, which ADR-002 forbids.
        assertThat(connector.connectorId()).isEqualTo("git");
    }
}
