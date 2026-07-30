package dev.tower.connector.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import org.springframework.stereotype.Component;

import dev.tower.connector.api.ConnectorCredential;
import dev.tower.connector.api.ConnectorException;
import dev.tower.connector.api.RepositoryLocator;
import dev.tower.connector.api.SourceControlConnector;
import dev.tower.connector.api.SourceRef;

/**
 * Reads branches and tags from a git remote (issue #3, ADR-014).
 *
 * <p>Reference discovery only — the equivalent of {@code git ls-remote}. Nothing
 * here clones, fetches objects or reads file content, and nothing here writes.
 * That is a property of the operation rather than of this class's discipline: the
 * exchange this performs has no write counterpart, so unlike a vendor REST client
 * there is no push endpoint being deliberately avoided.
 *
 * <p>Stateless and holds no connection. Every call builds its own command, for
 * the same reason the Kubernetes Connector builds its own client per call: a
 * connector that cached a transport would have to decide when the cache is stale,
 * and the answer would be wrong the first time a credential rotated.
 *
 * <p>Vendor-neutral by construction. The remote may be GitHub, GitLab, Bitbucket,
 * a self-hosted server or a bare repository on a file share, and this class
 * cannot tell the difference because it only ever asks git questions.
 */
@Component
public class GitSourceControlConnector implements SourceControlConnector {

    /**
     * Stable, and deliberately naming the protocol rather than a vendor.
     *
     * <p>Anything Tower stores keeps referring to this string. Calling it
     * "github" would have made a GitLab remote read as though it came from
     * GitHub, and would have to change the day someone points it elsewhere —
     * rewriting stored provenance, which ADR-002 forbids.
     */
    private static final String CONNECTOR_ID = "git";

    @Override
    public String connectorId() {
        return CONNECTOR_ID;
    }

    @Override
    public List<SourceRef> readRefs(RepositoryLocator locator, ConnectorCredential credential) {
        Collection<Ref> refs = lsRemote(locator, credential);

        List<SourceRef> found = new ArrayList<>(refs.size());
        for (Ref ref : refs) {
            SourceRef converted = convert(ref);
            if (converted != null) {
                found.add(converted);
            }
        }

        // Sorted so the same repository yields the same order every time. JGit
        // returns whatever the remote advertised, and a caller comparing today's
        // refs against yesterday's should not see a difference that is only the
        // remote's ordering.
        found.sort(Comparator.comparing(SourceRef::kind).thenComparing(SourceRef::name));
        return List.copyOf(found);
    }

    @Override
    public void checkConnection(RepositoryLocator locator, ConnectorCredential credential) {
        // The same request readRefs makes. There is no cheaper probe in the git
        // protocol - reference discovery is the first thing any exchange does -
        // and inventing a lighter one would test something other than what a real
        // read will do.
        lsRemote(locator, credential);
    }

    private Collection<Ref> lsRemote(RepositoryLocator locator, ConnectorCredential credential) {
        LsRemoteCommand command = Git.lsRemoteRepository()
                .setRemote(locator.url())
                .setHeads(true)
                .setTags(true);

        char[] token = null;
        try {
            if (credential != null && credential.isPresent()) {
                token = credential.token();
                // A token is presented as the password with a fixed username.
                // GitHub, GitLab and Bitbucket all accept a personal access token
                // this way over HTTPS; the username is ignored by each of them but
                // basic authentication requires one to be sent.
                command.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider("token", token));
            }
            return command.call();
        } catch (TransportException e) {
            // Split out from the general case because this is the failure a user
            // can act on: a wrong URL, a private repository, an expired token.
            throw new ConnectorException(failureMessage(locator, e), e);
        } catch (GitAPIException | RuntimeException e) {
            throw new ConnectorException(failureMessage(locator, e), e);
        } finally {
            if (token != null) {
                java.util.Arrays.fill(token, '\0');
            }
        }
    }

    /**
     * Maps a git ref to Tower's vocabulary, or null for a ref that is neither a
     * branch nor a tag.
     *
     * <p>The null cases are real and deliberately ignored rather than reported.
     * A remote advertises {@code HEAD} — a pointer to whichever branch is default,
     * not a ref in its own right — and may advertise {@code refs/pull/…} or
     * {@code refs/merge-requests/…}, which are the vendor's own bookkeeping and
     * exactly the vendor concepts CM-03 requires to stop at this boundary.
     */
    private SourceRef convert(Ref ref) {
        String name = ref.getName();
        ObjectId commit = commitOf(ref);
        if (commit == null) {
            // A symbolic or unresolved ref. Nothing to identify code with.
            return null;
        }
        if (name.startsWith(Constants.R_HEADS)) {
            return SourceRef.branch(name.substring(Constants.R_HEADS.length()), commit.name());
        }
        if (name.startsWith(Constants.R_TAGS)) {
            return SourceRef.tag(name.substring(Constants.R_TAGS.length()), commit.name());
        }
        return null;
    }

    /**
     * The commit a ref identifies.
     *
     * <p>An annotated tag does not point at a commit. It points at a tag object
     * which points at the commit, and a remote advertises the resolved commit
     * separately as the ref's peeled object id. Reporting the tag object's own id
     * would give a hash that names the label rather than the code, and would not
     * match what {@code git rev-parse} gives anyone checking Tower's work —
     * ADR-014 records why the commit is what belongs here.
     */
    private ObjectId commitOf(Ref ref) {
        return ref.getPeeledObjectId() != null ? ref.getPeeledObjectId() : ref.getObjectId();
    }

    /**
     * Names the repository once.
     *
     * <p>JGit usually names the remote itself — {@code /path/to/repo: not found} —
     * so prefixing unconditionally produces "Could not read /path/to/repo:
     * /path/to/repo: not found". That duplication is exactly what went out in
     * Milestone 2 and was found by reading a live response rather than by any
     * assertion, because every test used {@code contains()}. Here a test asserts
     * the URL appears once, and it caught this before the code was committed.
     *
     * <p>So the locator is added only when the underlying message has not already
     * mentioned it. A message that omits it still gets it, because "not found"
     * alone tells a user nothing about which repository was not found.
     */
    private String failureMessage(RepositoryLocator locator, Throwable e) {
        String detail = rootMessage(e);
        return detail.contains(locator.url())
                ? "Could not read this repository: " + detail
                : "Could not read " + locator + ": " + detail;
    }

    /** The innermost message, because JGit's outer ones repeat each other. */
    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
