package dev.tower.connector.api;

/**
 * Where a CI/CD Connector should look for runs.
 *
 * <p>Two fields, and the split matters. The <em>system</em> is the server: a
 * Jenkins controller, a GitLab instance, the host that answers. The <em>job</em>
 * is the thing within it whose runs are wanted, written the way that system
 * writes it — a folder path in Jenkins, a workflow file in GitHub Actions, a
 * project path in GitLab.
 *
 * <p>Neither is parsed above the Connector. That is what keeps this record from
 * becoming Jenkins-shaped: nothing outside the Connector may know that a Jenkins
 * job path has {@code /job/} between its segments, any more than
 * {@link IssueLocator} lets anything above it know that a GitHub locator is
 * {@code owner/repo}.
 *
 * @param system where the CI system lives, as its own address
 * @param job    the job, pipeline or workflow within it, as that system names it
 */
public record PipelineLocator(String system, String job) {

    public PipelineLocator {
        if (system == null || system.isBlank()) {
            throw new ConnectorException("A pipeline locator must name the CI system to read.");
        }
        if (job == null || job.isBlank()) {
            throw new ConnectorException(
                    "A pipeline locator must name the job whose runs are wanted.");
        }
        system = system.trim();
        job = job.trim();
    }

    /** Rendered into failure messages, so it stays readable. */
    @Override
    public String toString() {
        return job + " at " + system;
    }
}
