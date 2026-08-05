import { useCallback, useEffect, useState } from "react";
import {
  ApplicationBinding,
  Application,
  ArtifactCoordinateBinding,
  BuildJobBinding,
  ConnectionTest,
  CoordinatePreview,
  CredentialStatus,
  Environment,
  EnvironmentBinding,
  IssueTrackerBinding,
  IssueTrackerConnectionReport,
  PipelineJobBinding,
  PipelineSyncReport,
  RefSelection,
  VersionSource,
  RepositoryBinding,
  SyncRun,
  bindApplication,
  bindArtifactCoordinate,
  bindBuildJob,
  bindIssueTracker,
  bindPipelineJob,
  bindRepository,
  bindEnvironment,
  forgetCredential,
  getApplications,
  getCredentialStatus,
  getEnvironments,
  listApplicationBindings,
  listArtifactCoordinateBindings,
  listBuildJobBindings,
  listEnvironmentBindings,
  listIssueTrackerBindings,
  listPipelineJobBindings,
  listPipelineSyncReports,
  listSyncRuns,
  previewCoordinate,
  previewVersion,
  storeCredential,
  synchronizeNow,
  synchronizePipelinesNow,
  testConnection,
  listRepositoryBindings,
  testArtifactRepositoryConnection,
  testIssueTrackerConnection,
  testPipelineConnection,
  testRepositoryConnection,
  unbindApplication,
  unbindArtifactCoordinate,
  unbindBuildJob,
  unbindIssueTracker,
  unbindPipelineJob,
  unbindRepository,
  unbindEnvironment,
} from "../api/client";
import ErrorNote, { describeError } from "../components/ErrorNote";

// Milestone 2 has one Connector. Naming the constant rather than scattering
// the string keeps the day a second one arrives to this file.
const CONNECTOR_ID = "kubernetes";

export default function ConnectorsPage() {
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [environmentBindings, setEnvironmentBindings] = useState<EnvironmentBinding[]>([]);
  const [applicationBindings, setApplicationBindings] = useState<ApplicationBinding[]>([]);
  const [repositoryBindings, setRepositoryBindings] = useState<RepositoryBinding[]>([]);
  const [trackerBindings, setTrackerBindings] = useState<IssueTrackerBinding[]>([]);
  const [artifactBindings, setArtifactBindings] = useState<ArtifactCoordinateBinding[]>([]);
  const [pipelineBindings, setPipelineBindings] = useState<PipelineJobBinding[]>([]);
  const [pipelineReports, setPipelineReports] = useState<PipelineSyncReport[]>([]);
  const [buildBindings, setBuildBindings] = useState<BuildJobBinding[]>([]);
  const [runs, setRuns] = useState<SyncRun[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [envs, apps, envBindings, appBindings, repoBindings, trackers, artifacts,
             pipelines, builds, reports, history] =
        await Promise.all([
          getEnvironments(),
          getApplications(),
          listEnvironmentBindings(),
          listApplicationBindings(),
          listRepositoryBindings(),
          listIssueTrackerBindings(),
          listArtifactCoordinateBindings(),
          listPipelineJobBindings(),
          listBuildJobBindings(),
          listPipelineSyncReports(10),
          listSyncRuns(10),
        ]);
      setEnvironments(envs);
      setApplications(apps);
      setEnvironmentBindings(envBindings);
      setApplicationBindings(appBindings);
      setRepositoryBindings(repoBindings);
      setTrackerBindings(trackers);
      setArtifactBindings(artifacts);
      setPipelineBindings(pipelines);
      setBuildBindings(builds);
      setPipelineReports(reports);
      setRuns(history);
      setError(null);
    } catch (caught: unknown) {
      setError(describeError(caught));
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return (
    <section className="page">
      <h2>Connectors</h2>
      <p className="page__intro">
        Tower reads Deployment Platforms so nobody has to type in what is deployed. It never writes
        to them: every Connector operation is a read, and a Connector that named a method for
        mutation would fail the build.
      </p>

      {error !== null && <ErrorNote error={error} />}

      <SynchronizePanel runs={runs} onDone={reload} onError={setError} />

      <EnvironmentBindingsPanel
        environments={environments}
        bindings={environmentBindings}
        onChanged={reload}
        onError={setError}
      />

      <ApplicationBindingsPanel
        applications={applications}
        bindings={applicationBindings}
        onChanged={reload}
        onError={setError}
      />

      <RepositoryBindingsPanel
        applications={applications}
        bindings={repositoryBindings}
        onChanged={reload}
        onError={setError}
      />

      <IssueTrackerBindingsPanel
        bindings={trackerBindings}
        onChanged={reload}
        onError={setError}
      />

      {/*
        The CI/CD pair sits together: the jobs, then what reading them produced.
        Deliberately not merged into the Synchronization panel above — OQ-017
        records why, and PipelineSyncPanel says it on the screen.
      */}
      <PipelineJobBindingsPanel
        environments={environments}
        applications={applications}
        bindings={pipelineBindings}
        onChanged={reload}
        onError={setError}
      />

      <BuildJobBindingsPanel
        applications={applications}
        bindings={buildBindings}
        onChanged={reload}
        onError={setError}
      />

      <PipelineSyncPanel reports={pipelineReports} onDone={reload} onError={setError} />

      <ArtifactCoordinateBindingsPanel
        applications={applications}
        bindings={artifactBindings}
        onChanged={reload}
        onError={setError}
      />
    </section>
  );
}

// --- Synchronization -------------------------------------------------------

function SynchronizePanel({
  runs,
  onDone,
  onError,
}: {
  runs: SyncRun[];
  onDone: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [running, setRunning] = useState(false);
  const latest = runs[0];

  async function run() {
    setRunning(true);
    try {
      await synchronizeNow();
      await onDone();
    } catch (caught: unknown) {
      onError(describeError(caught));
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel__header">
        <h3>Synchronization</h3>
        <button type="button" onClick={() => void run()} disabled={running}>
          {running ? "Synchronizing…" : "Synchronize now"}
        </button>
      </div>

      <p className="hint">
        On demand only. Scheduling is deliberately deferred until the image-to-version mapping is
        trusted, because a wrong pattern running unattended records facts that cannot be edited
        afterwards.
      </p>

      {!latest && <p className="hint">Tower has not synchronized yet.</p>}

      {latest && (
        <>
          <p data-outcome={latest.outcome} className="sync-outcome">
            <strong>{latest.outcome.replace("_", " ").toLowerCase()}</strong> — read{" "}
            {latest.workloadsRead} workload{latest.workloadsRead === 1 ? "" : "s"}, recorded{" "}
            {latest.observationsAppended} new observation
            {latest.observationsAppended === 1 ? "" : "s"} at{" "}
            {new Date(latest.finishedAt).toLocaleString()}.
          </p>

          {/*
            The pairing ADR-011 promised. An Observation says when a version last
            changed; this says when Tower last looked and found it unchanged.
            Only a wholly successful run may say it.
          */}
          {latest.confirmsLiveness && latest.foundNoChange && (
            <p className="hint">
              Nothing had changed. Everything Tower already knew was confirmed still present at this
              time.
            </p>
          )}

          {latest.failures.length > 0 && (
            <div className="sync-failures">
              <h4>Could not be read</h4>
              <ul>
                {latest.failures.map((failure) => (
                  <li key={failure}>{failure}</li>
                ))}
              </ul>
              <p className="hint">
                Observations recorded before this run remain valid. A scope Tower could not read
                simply produced no new facts.
              </p>
            </div>
          )}

          {latest.unrecognized.length > 0 && (
            <div className="sync-unrecognized">
              <h4>Running, but not recognized</h4>
              <p className="hint">
                Tower will not guess which Application these belong to. Bind the image below and
                they will be observed on the next run.
              </p>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Scope</th>
                    <th>Workload</th>
                    <th>Image</th>
                    <th>Why</th>
                  </tr>
                </thead>
                <tbody>
                  {latest.unrecognized.map((workload) => (
                    <tr key={`${workload.scope}/${workload.name}/${workload.imageReference}`}>
                      <td>{workload.scope}</td>
                      <td>{workload.name}</td>
                      <td className="mono">{workload.imageReference}</td>
                      <td>{workload.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {runs.length > 1 && (
        <details>
          <summary>Earlier runs ({runs.length - 1})</summary>
          <ul className="run-history">
            {runs.slice(1).map((run) => (
              <li key={run.id}>
                {new Date(run.startedAt).toLocaleString()} — {run.outcome.replace("_", " ").toLowerCase()},{" "}
                {run.observationsAppended} new
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

// --- Environment bindings --------------------------------------------------

function EnvironmentBindingsPanel({
  environments,
  bindings,
  onChanged,
  onError,
}: {
  environments: Environment[];
  bindings: EnvironmentBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [environmentId, setEnvironmentId] = useState("");
  const [target, setTarget] = useState("");
  const [scope, setScope] = useState("");

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindEnvironment({ environmentId, connectorId: CONNECTOR_ID, target, scope });
      setScope("");
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function remove(binding: EnvironmentBinding) {
    try {
      await unbindEnvironment(binding.environmentId, binding.connectorId);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  const nameOf = (id: string) => environments.find((e) => e.id === id)?.name ?? id;

  return (
    <div className="panel">
      <h3>Environments</h3>
      <p className="hint">
        Which namespace holds each Environment. An Environment is a business concept, not a
        namespace, so the correspondence is recorded here rather than on the Environment itself.
      </p>

      {bindings.length === 0 && <p className="hint">No Environment is bound yet.</p>}

      {bindings.map((binding) => (
        <EnvironmentBindingRow
          key={`${binding.environmentId}-${binding.connectorId}`}
          binding={binding}
          environmentName={nameOf(binding.environmentId)}
          onRemove={() => void remove(binding)}
          onError={onError}
        />
      ))}

      <form className="inline-form" onSubmit={(event) => void save(event)}>
        <label>
          Environment
          <select value={environmentId} onChange={(event) => setEnvironmentId(event.target.value)} required>
            <option value="">Choose…</option>
            {environments.map((environment) => (
              <option key={environment.id} value={environment.id}>
                {environment.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          API server
          <input
            value={target}
            onChange={(event) => setTarget(event.target.value)}
            placeholder="https://api.cluster.example:6443"
            required
          />
        </label>
        <label>
          Namespace
          <input
            value={scope}
            onChange={(event) => setScope(event.target.value)}
            placeholder="customer-uat"
            required
          />
        </label>
        <button type="submit">Bind</button>
      </form>
    </div>
  );
}

function EnvironmentBindingRow({
  binding,
  environmentName,
  onRemove,
  onError,
}: {
  binding: EnvironmentBinding;
  environmentName: string;
  onRemove: () => void;
  onError: (message: string) => void;
}) {
  const [credential, setCredential] = useState<CredentialStatus | null>(null);
  const [secret, setSecret] = useState("");
  const [test, setTest] = useState<ConnectionTest | null>(null);
  const [testing, setTesting] = useState(false);

  const refreshCredential = useCallback(async () => {
    try {
      setCredential(await getCredentialStatus(binding.connectorId, binding.target));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }, [binding.connectorId, binding.target, onError]);

  useEffect(() => {
    void refreshCredential();
  }, [refreshCredential]);

  async function saveSecret(event: React.FormEvent) {
    event.preventDefault();
    try {
      await storeCredential(binding.connectorId, binding.target, secret);
      // Cleared immediately: the token has been sent and the field holding it
      // should not outlive that. Nothing ever reads it back.
      setSecret("");
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function forget() {
    try {
      await forgetCredential(binding.connectorId, binding.target);
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function check() {
    setTesting(true);
    try {
      setTest(await testConnection(binding.connectorId, binding.target, binding.scope));
    } catch (caught: unknown) {
      onError(describeError(caught));
    } finally {
      setTesting(false);
    }
  }

  return (
    <div className="binding">
      <div className="binding__header">
        <strong>{environmentName}</strong>
        <span className="mono">
          {binding.target}/{binding.scope}
        </span>
        <button type="button" onClick={() => void check()} disabled={testing}>
          {testing ? "Testing…" : "Test connection"}
        </button>
        <button type="button" className="button-link" onClick={onRemove}>
          Unbind
        </button>
      </div>

      {test && (
        <p className="connection-test" data-reachable={test.reachable}>
          {test.reachable ? "✓ " : "✗ "}
          {test.message}
        </p>
      )}

      <form className="inline-form" onSubmit={(event) => void saveSecret(event)}>
        <label>
          Token
          <input
            type="password"
            value={secret}
            onChange={(event) => setSecret(event.target.value)}
            placeholder={credential?.configured ? "A token is stored" : "Paste the login token"}
            autoComplete="off"
            required
          />
        </label>
        <button type="submit">{credential?.configured ? "Replace" : "Save"}</button>
        {credential?.configured && (
          <button type="button" className="button-link" onClick={() => void forget()}>
            Forget
          </button>
        )}
      </form>

      <p className="hint">
        {credential?.configured
          ? `A token is stored for this API server${
              credential.updatedAt ? `, saved ${new Date(credential.updatedAt).toLocaleString()}` : ""
            }. It is encrypted at rest and is never shown again — replace it rather than reading it back.`
          : "No token is stored for this API server yet."}
      </p>
    </div>
  );
}

// --- Application bindings --------------------------------------------------

function ApplicationBindingsPanel({
  applications,
  bindings,
  onChanged,
  onError,
}: {
  applications: Application[];
  bindings: ApplicationBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [applicationId, setApplicationId] = useState("");
  const [image, setImage] = useState("");
  const [versionPattern, setVersionPattern] = useState("");
  const [sampleTag, setSampleTag] = useState("");
  const [preview, setPreview] = useState<string | null>(null);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindApplication({ applicationId, connectorId: CONNECTOR_ID, image, versionPattern });
      setImage("");
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function tryPattern() {
    try {
      const result = await previewVersion(versionPattern, sampleTag);
      setPreview(
        result.matched
          ? `“${result.imageTag}” would be recorded as version ${result.version}.`
          : `“${result.imageTag}” does not match. That workload would be reported as unrecognized rather than guessed.`,
      );
    } catch (caught: unknown) {
      setPreview(null);
      onError(describeError(caught));
    }
  }

  async function remove(binding: ApplicationBinding) {
    try {
      await unbindApplication(binding.applicationId, binding.connectorId);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  const nameOf = (id: string) => applications.find((a) => a.id === id)?.name ?? id;

  return (
    <div className="panel">
      <h3>Applications</h3>
      <p className="hint">
        Which image is which Application, and how to read a version from its tag. Tower will not
        guess: an image nobody has bound is reported rather than attributed, because an Observation
        recorded against the wrong Application cannot be corrected afterwards.
      </p>

      {bindings.length === 0 && <p className="hint">No Application is bound yet.</p>}

      {bindings.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Application</th>
              <th>Image</th>
              <th>Version pattern</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {bindings.map((binding) => (
              <tr key={`${binding.applicationId}-${binding.connectorId}`}>
                <td>{nameOf(binding.applicationId)}</td>
                <td className="mono">{binding.image}</td>
                <td className="mono">{binding.versionPattern}</td>
                <td>
                  <button type="button" className="button-link" onClick={() => void remove(binding)}>
                    Unbind
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form" onSubmit={(event) => void save(event)}>
        <label>
          Application
          <select value={applicationId} onChange={(event) => setApplicationId(event.target.value)} required>
            <option value="">Choose…</option>
            {applications.map((application) => (
              <option key={application.id} value={application.id}>
                {application.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Image
          <input
            value={image}
            onChange={(event) => setImage(event.target.value)}
            placeholder="registry.example/acme/customer-api"
            required
          />
        </label>
        <label>
          Version pattern
          <input
            value={versionPattern}
            onChange={(event) => setVersionPattern(event.target.value)}
            placeholder="^(.+)$ — the whole tag"
          />
        </label>
        <button type="submit">Bind</button>
      </form>

      {/*
        Not decoration. A wrong pattern produces wrong Application Versions, and
        because Observations are immutable those survive the correction — this is
        the one mistake here that cannot be undone.
      */}
      <div className="inline-form">
        <label>
          Try it against a real tag
          <input
            value={sampleTag}
            onChange={(event) => setSampleTag(event.target.value)}
            placeholder="release-2026.08.1"
          />
        </label>
        <button type="button" onClick={() => void tryPattern()} disabled={!sampleTag}>
          Preview
        </button>
      </div>
      {preview && <p className="hint">{preview}</p>}
    </div>
  );
}

// --- Repository bindings ---------------------------------------------------

// Where an Application's code lives (issue #3, ADR-014).
//
// Its own Connector, and its own panel, because the git Connector is not the
// Kubernetes one — CONNECTOR_ID above names the Deployment Platform, and reusing
// it here would have bound repositories to a Connector that cannot read them.
const SOURCE_CONNECTOR_ID = "git";

const REF_SELECTIONS: { value: RefSelection; label: string }[] = [
  { value: "TAGS", label: "Tags" },
  { value: "BRANCHES", label: "Branches" },
  { value: "ALL", label: "Tags and branches" },
];

function RepositoryBindingsPanel({
  applications,
  bindings,
  onChanged,
  onError,
}: {
  applications: Application[];
  bindings: RepositoryBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [applicationId, setApplicationId] = useState("");
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [refSelection, setRefSelection] = useState<RefSelection>("TAGS");
  const [versionPattern, setVersionPattern] = useState("");
  const [tested, setTested] = useState<string | null>(null);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindRepository({
        applicationId,
        connectorId: SOURCE_CONNECTOR_ID,
        repositoryUrl,
        refSelection,
        versionPattern,
      });
      setRepositoryUrl("");
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  // Tests the URL before it is saved, so a typo or a missing credential is found
  // here rather than as an empty candidate list on the Applications page.
  async function test() {
    try {
      const result = await testRepositoryConnection(repositoryUrl);
      setTested(result.message);
    } catch (caught: unknown) {
      setTested(null);
      onError(describeError(caught));
    }
  }

  async function remove(binding: RepositoryBinding) {
    try {
      await unbindRepository(binding.applicationId, binding.connectorId);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  const nameOf = (id: string) => applications.find((a) => a.id === id)?.name ?? id;
  const labelOf = (selection: RefSelection) =>
    REF_SELECTIONS.find((s) => s.value === selection)?.label ?? selection;

  return (
    <div className="panel">
      <h3>Repositories</h3>
      <p className="hint">
        Where each Application&apos;s code lives, so versions can be registered from what source
        control actually holds rather than typed in. Tower reads refs and nothing else: it never
        pushes, never tags and never opens a pull request.
      </p>
      <p className="hint">
        Any git remote works — GitHub, GitLab, Bitbucket, a self-hosted server or a path on a file
        share. Tower reads the protocol, not a vendor&apos;s API, so the choice is just a URL.
      </p>

      {bindings.length === 0 && <p className="hint">No repository is bound yet.</p>}

      {bindings.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Application</th>
              <th>Repository</th>
              <th>Reads</th>
              <th>Version pattern</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {bindings.map((binding) => (
              <tr key={`${binding.applicationId}-${binding.connectorId}`}>
                <td>{nameOf(binding.applicationId)}</td>
                <td className="mono">{binding.repositoryUrl}</td>
                <td>{labelOf(binding.refSelection)}</td>
                <td className="mono">{binding.versionPattern}</td>
                <td>
                  <button type="button" className="button-link" onClick={() => void remove(binding)}>
                    Unbind
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form" onSubmit={(event) => void save(event)}>
        <label>
          Application
          <select value={applicationId} onChange={(event) => setApplicationId(event.target.value)} required>
            <option value="">Choose…</option>
            {applications.map((application) => (
              <option key={application.id} value={application.id}>
                {application.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Repository
          <input
            value={repositoryUrl}
            onChange={(event) => setRepositoryUrl(event.target.value)}
            placeholder="https://github.com/acme/customer-api.git"
            required
          />
        </label>
        {/*
          Tags by default: reading branches as well when a team only tags offers
          every feature branch as a version.
        */}
        <label>
          Reads
          <select value={refSelection} onChange={(event) => setRefSelection(event.target.value as RefSelection)}>
            {REF_SELECTIONS.map((selection) => (
              <option key={selection.value} value={selection.value}>
                {selection.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Version pattern
          <input
            value={versionPattern}
            onChange={(event) => setVersionPattern(event.target.value)}
            placeholder="^v(.+)$ — v2.5.0 becomes 2.5.0"
          />
        </label>
        <button type="submit">Bind</button>
        <button type="button" onClick={() => void test()} disabled={!repositoryUrl}>
          Test
        </button>
      </form>
      {tested && <p className="hint">{tested}</p>}
    </div>
  );
}

// --- Issue tracker bindings ------------------------------------------------

// Where the team's work items live (ADR-018).
//
// The odd panel out, and deliberately: every panel above binds a Tower concept
// to a vendor locator, and this one binds nothing on the left. A work item
// belongs to a release rather than to an Application, and a team has one
// tracker, so the tracker is named once.
//
// What a locator is, and what a credential looks like, differ per tracker and
// are the Connector's business — so they are described here rather than guessed
// at by a single form that would fit neither.
const ISSUE_TRACKERS: {
  id: string;
  name: string;
  locatorLabel: string;
  placeholder: string;
  credentialHint: string;
}[] = [
  {
    id: "github-issues",
    name: "GitHub Issues",
    locatorLabel: "Repository",
    placeholder: "acme/retail",
    credentialHint:
      "A personal access token, and only for a private repository. A public one reads without any.",
  },
  {
    id: "jira",
    name: "Jira",
    locatorLabel: "Site",
    placeholder: "https://acme.atlassian.net",
    credentialHint:
      "For Jira Cloud: your account email, a colon, then an API token — person@acme.com:abc123."
      + " For Server or Data Center: a personal access token on its own.",
  },
];

function IssueTrackerBindingsPanel({
  bindings,
  onChanged,
  onError,
}: {
  bindings: IssueTrackerBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [connectorId, setConnectorId] = useState(ISSUE_TRACKERS[0].id);
  const [locator, setLocator] = useState("");

  const chosen = ISSUE_TRACKERS.find((t) => t.id === connectorId) ?? ISSUE_TRACKERS[0];

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindIssueTracker({ connectorId, locator });
      setLocator("");
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  return (
    <div className="panel">
      <h3>Issue tracker</h3>
      <p className="hint">
        Where the work items a release delivers are tracked. Tower reads them and writes nothing
        back: it never opens an issue, never closes one, never transitions one and never comments.
      </p>
      <p className="hint">
        What a release document prints is the title somebody accepted, not whatever the tracker says
        today. That is what keeps a document reproducible — nothing here changes a document on its
        own.
      </p>

      {bindings.length === 0 && <p className="hint">No tracker is bound yet.</p>}

      {bindings.map((binding) => (
        <IssueTrackerBindingRow
          key={binding.connectorId}
          binding={binding}
          onChanged={onChanged}
          onError={onError}
        />
      ))}

      {/*
        ADR-018 expects one tracker. More than one is not refused by the API —
        nothing breaks — but Tower reads the first by Connector name, and a
        screen that left that unsaid would look like it was ignoring one of them.
      */}
      {bindings.length > 1 && (
        <p className="hint">
          More than one tracker is bound. Tower reads the first by name —{" "}
          <strong>{[...bindings].map((b) => b.connectorId).sort()[0]}</strong> — so unbind the others
          unless that is what you meant.
        </p>
      )}

      {bindings.length < ISSUE_TRACKERS.length && (
        <form className="inline-form" onSubmit={(event) => void save(event)}>
          <label>
            Tracker
            <select value={connectorId} onChange={(event) => setConnectorId(event.target.value)}>
              {ISSUE_TRACKERS.filter((t) => !bindings.some((b) => b.connectorId === t.id)).map(
                (tracker) => (
                  <option key={tracker.id} value={tracker.id}>
                    {tracker.name}
                  </option>
                ),
              )}
            </select>
          </label>
          <label>
            {chosen.locatorLabel}
            <input
              value={locator}
              onChange={(event) => setLocator(event.target.value)}
              placeholder={chosen.placeholder}
              required
            />
          </label>
          <button type="submit">Bind</button>
        </form>
      )}
    </div>
  );
}

function IssueTrackerBindingRow({
  binding,
  onChanged,
  onError,
}: {
  binding: IssueTrackerBinding;
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [credential, setCredential] = useState<CredentialStatus | null>(null);
  const [secret, setSecret] = useState("");
  const [test, setTest] = useState<IssueTrackerConnectionReport | null>(null);

  const tracker = ISSUE_TRACKERS.find((t) => t.id === binding.connectorId);

  const refreshCredential = useCallback(async () => {
    try {
      setCredential(await getCredentialStatus(binding.connectorId, binding.locator));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }, [binding.connectorId, binding.locator, onError]);

  useEffect(() => {
    void refreshCredential();
  }, [refreshCredential]);

  async function remove() {
    try {
      await unbindIssueTracker(binding.connectorId);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function check() {
    try {
      setTest(await testIssueTrackerConnection(binding.connectorId));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function saveSecret(event: React.FormEvent) {
    event.preventDefault();
    try {
      await storeCredential(binding.connectorId, binding.locator, secret);
      // Cleared as soon as it is sent, like every other token field here.
      setSecret("");
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function forget() {
    try {
      await forgetCredential(binding.connectorId, binding.locator);
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  return (
    <div className="binding">
      <div className="binding__header">
        {/* The Connector's id when it is one this build does not know — honest
            about what is bound rather than rendering an empty name. */}
        <strong>{tracker?.name ?? binding.connectorId}</strong>
        <span className="mono">{binding.locator}</span>
        <button type="button" onClick={() => void check()}>
          Test connection
        </button>
        <button type="button" className="button-link" onClick={() => void remove()}>
          Unbind
        </button>
      </div>

      {test && (
        <p className="connection-test" data-reachable={test.reachable}>
          {test.reachable ? "✓ " : "✗ "}
          {test.message}
        </p>
      )}

      <form className="inline-form" onSubmit={(event) => void saveSecret(event)}>
        <label>
          Token
          <input
            type="password"
            value={secret}
            onChange={(event) => setSecret(event.target.value)}
            placeholder={credential?.configured ? "A token is stored" : "Only if the tracker is private"}
            autoComplete="off"
            required
          />
        </label>
        <button type="submit">{credential?.configured ? "Replace" : "Save"}</button>
        {credential?.configured && (
          <button type="button" className="button-link" onClick={() => void forget()}>
            Forget
          </button>
        )}
      </form>

      <p className="hint">
        {credential?.configured
          ? "A token is stored for this tracker. It is encrypted at rest and is never shown again — replace it rather than reading it back."
          : tracker?.credentialHint ?? "No token is stored."}
      </p>
    </div>
  );
}

// --- Artifact coordinates (ADR-021) ----------------------------------------

// Where the binaries an Application Version names live.
//
// The mirror image of every other binding on this page, and the one thing worth
// understanding before filling the form in. A version pattern above *extracts*
// an Application Version out of a string a vendor produced; a coordinate
// template *composes* a string a vendor will recognise out of a version Tower
// already holds. That is only possible because a team tags an image and a chart
// with the version and the short commit — and it is also what lets the Connector
// read with a plain GET rather than a search.
//
// Several templates per Application is the ordinary case rather than an edge: a
// build publishes the application, an image and a chart, and the kind is what
// tells them apart. The kind is the team's own word and Tower never interprets
// it.
const ARTIFACT_CONNECTOR_ID = "artifactory";

function ArtifactCoordinateBindingsPanel({
  applications,
  bindings,
  onChanged,
  onError,
}: {
  applications: Application[];
  bindings: ArtifactCoordinateBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [applicationId, setApplicationId] = useState("");
  const [kind, setKind] = useState("");
  const [system, setSystem] = useState("");
  const [coordinateTemplate, setCoordinateTemplate] = useState("");
  const [shortCommitLength, setShortCommitLength] = useState("7");
  const [sampleVersion, setSampleVersion] = useState("");
  const [sampleCommit, setSampleCommit] = useState("");
  const [preview, setPreview] = useState<CoordinatePreview | null>(null);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindArtifactCoordinate({
        applicationId,
        connectorId: ARTIFACT_CONNECTOR_ID,
        kind,
        system,
        coordinateTemplate,
        shortCommitLength: Number(shortCommitLength) || 0,
      });
      setKind("");
      setCoordinateTemplate("");
      setPreview(null);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  // Tries the template before it is saved. A wrong template produces a false
  // "not found" rather than a wrong fact, which is the mildest failure any
  // binding here can cause — but it is still a failure that sends somebody
  // looking in a repository for something that was never addressed correctly.
  async function tryIt() {
    try {
      setPreview(
        await previewCoordinate(
          coordinateTemplate,
          Number(shortCommitLength) || 0,
          sampleVersion,
          sampleCommit,
        ),
      );
    } catch (caught: unknown) {
      setPreview(null);
      onError(describeError(caught));
    }
  }

  async function remove(binding: ArtifactCoordinateBinding) {
    try {
      await unbindArtifactCoordinate(binding.applicationId, binding.kind, binding.connectorId);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  const nameOf = (id: string) => applications.find((a) => a.id === id)?.name ?? id;

  // One credential and one connection test per repository address, not per
  // template: the credential store is keyed by Connector and address, so one
  // token serves every artifact in the same repository.
  const systems = [...new Set(bindings.map((binding) => binding.system))].sort();

  return (
    <div className="panel">
      <h3>Artifact repositories</h3>
      <p className="hint">
        How to address the image, the chart and anything else a build published, so Tower can
        confirm they are where a version says they should be. It reads and nothing else: it never
        uploads, never promotes, never re-tags and never deletes.
      </p>
      <p className="hint">
        A template <em>composes</em> a coordinate where the version patterns above{" "}
        <em>extract</em> a version. Write <code>{"{version}"}</code>, <code>{"{commit}"}</code> and{" "}
        <code>{"{shortCommit}"}</code> where the build puts them — for example{" "}
        <code>docker-local/acme/api:{"{version}"}-{"{shortCommit}"}</code>.
      </p>
      <p className="hint">
        Nothing read from a repository is stored. What a release document prints is a digest
        somebody accepted on the Applications page, which is why a re-pushed tag shows up as a
        difference rather than quietly changing a document.
      </p>

      {bindings.length === 0 && <p className="hint">No artifact template is bound yet.</p>}

      {bindings.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Application</th>
              <th>Kind</th>
              <th>Repository</th>
              <th>Coordinate template</th>
              <th>Short commit</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {bindings.map((binding) => (
              <tr key={`${binding.applicationId}-${binding.connectorId}-${binding.kind}`}>
                <td>{nameOf(binding.applicationId)}</td>
                <td>{binding.kind}</td>
                <td className="mono">{binding.system}</td>
                <td className="mono">{binding.coordinateTemplate}</td>
                <td>{binding.shortCommitLength}</td>
                <td>
                  <button type="button" className="button-link" onClick={() => void remove(binding)}>
                    Unbind
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form" onSubmit={(event) => void save(event)}>
        <label>
          Application
          <select
            value={applicationId}
            onChange={(event) => setApplicationId(event.target.value)}
            required
          >
            <option value="">Choose…</option>
            {applications.map((application) => (
              <option key={application.id} value={application.id}>
                {application.name}
              </option>
            ))}
          </select>
        </label>
        {/* Free text rather than a fixed list: a Tower that decided which kinds
            exist would be wrong for the first team that had a fourth. */}
        <label>
          Kind
          <input
            value={kind}
            onChange={(event) => setKind(event.target.value)}
            placeholder="image, chart, installer…"
            required
          />
        </label>
        <label>
          Repository
          <input
            value={system}
            onChange={(event) => setSystem(event.target.value)}
            placeholder="https://acme.jfrog.io/artifactory"
            required
          />
        </label>
        <label>
          Coordinate template
          <input
            value={coordinateTemplate}
            onChange={(event) => setCoordinateTemplate(event.target.value)}
            placeholder={"docker-local/acme/api:{version}-{shortCommit}"}
            required
          />
        </label>
        {/*
          Seven is what git rev-parse --short gives by default, and that default
          is not a guarantee: git lengthens it where seven would be ambiguous,
          and a pipeline may have pinned another length years ago.
        */}
        <label>
          Short commit
          <input
            type="number"
            min={4}
            max={40}
            value={shortCommitLength}
            onChange={(event) => setShortCommitLength(event.target.value)}
          />
        </label>
        <button type="submit">Bind</button>
      </form>

      <form className="inline-form" onSubmit={(event) => { event.preventDefault(); void tryIt(); }}>
        <label>
          Try it against version
          <input
            value={sampleVersion}
            onChange={(event) => setSampleVersion(event.target.value)}
            placeholder="2.5.0"
          />
        </label>
        <label>
          and commit
          <input
            value={sampleCommit}
            onChange={(event) => setSampleCommit(event.target.value)}
            placeholder="abc1234def…"
          />
        </label>
        <button type="submit" disabled={!coordinateTemplate || !sampleVersion}>
          Preview
        </button>
      </form>

      {preview !== null && preview.composed !== null && (
        <p className="hint">
          That template addresses <code className="mono">{preview.composed}</code>.
        </p>
      )}
      {preview !== null && preview.composed === null && (
        <p className="hint">
          The template needs {preview.missing.join(", ")}, which that version does not carry. Tower
          reports this rather than composing a coordinate with a hole in it — asking the repository
          about an address no build ever wrote would answer &quot;not found&quot; for the wrong
          reason.
        </p>
      )}

      {systems.map((address) => (
        <ArtifactRepositoryRow key={address} system={address} onError={onError} />
      ))}
    </div>
  );
}

// One repository address: its credential, and whether it answers.
//
// Separate from the template rows above because a credential belongs to the
// address rather than to any one artifact — the same arrangement the credential
// store itself uses (NFR-028), so one token serves the image and the chart.
function ArtifactRepositoryRow({
  system,
  onError,
}: {
  system: string;
  onError: (message: string) => void;
}) {
  const [credential, setCredential] = useState<CredentialStatus | null>(null);
  const [secret, setSecret] = useState("");
  const [test, setTest] = useState<ConnectionTest | null>(null);

  const refreshCredential = useCallback(async () => {
    try {
      setCredential(await getCredentialStatus(ARTIFACT_CONNECTOR_ID, system));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }, [system, onError]);

  useEffect(() => {
    void refreshCredential();
  }, [refreshCredential]);

  async function check() {
    try {
      setTest(await testArtifactRepositoryConnection(ARTIFACT_CONNECTOR_ID, system));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function saveSecret(event: React.FormEvent) {
    event.preventDefault();
    try {
      await storeCredential(ARTIFACT_CONNECTOR_ID, system, secret);
      // Cleared as soon as it is sent, like every other token field here.
      setSecret("");
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function forget() {
    try {
      await forgetCredential(ARTIFACT_CONNECTOR_ID, system);
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  return (
    <div className="binding">
      <div className="binding__header">
        <span className="mono">{system}</span>
        <button type="button" onClick={() => void check()}>
          Test connection
        </button>
      </div>

      {test && (
        <p className="connection-test" data-reachable={test.reachable}>
          {test.reachable ? "✓ " : "✗ "}
          {test.message}
        </p>
      )}

      <form className="inline-form" onSubmit={(event) => void saveSecret(event)}>
        <label>
          Token
          <input
            type="password"
            value={secret}
            onChange={(event) => setSecret(event.target.value)}
            placeholder={credential?.configured ? "A token is stored" : "Only if the repository is private"}
            autoComplete="off"
            required
          />
        </label>
        <button type="submit">{credential?.configured ? "Replace" : "Save"}</button>
        {credential?.configured && (
          <button type="button" className="button-link" onClick={() => void forget()}>
            Forget
          </button>
        )}
      </form>

      <p className="hint">
        {credential?.configured
          ? "A token is stored for this repository. It is encrypted at rest and is never shown again — replace it rather than reading it back."
          : "An access token on its own, or a user name and an API key written as name:key. A repository that allows anonymous read needs neither."}
      </p>
    </div>
  );
}

// --- Pipeline jobs (ADR-020) ------------------------------------------------

// Which job's runs mean an Application reached an Environment.
//
// The widest binding on this page, and it has to be: the panels above map an
// Environment to a namespace, or an Application to an image or a repository.
// This maps both at once, because that is what a run of a deployment job
// actually asserts — this Application arrived in that Environment.
//
// Only deployment jobs belong here. A build job produces a candidate version,
// not an Observation, and ADR-020 keeps the two apart precisely so that a build
// is never recorded as though it had put something into an Environment.
const CI_CONNECTOR_ID = "jenkins";

// Where in a run the version lives. A CI system used as most of them actually
// are does not record it anywhere a Connector could guess, and ADR-020 refuses
// to read the console log to find it: correctness would then depend on log
// formatting, and a wrong parse writes Observations that cannot be edited.
const VERSION_SOURCES: { value: VersionSource; label: string; hint: string }[] = [
  {
    value: "PARAMETER",
    label: "A build parameter",
    hint: "Name the parameter or environment variable the run carried, for example VERSION.",
  },
  {
    value: "RUN_NAME",
    label: "The run's own name",
    hint: "For a job whose display name is set to the version being deployed. No key is needed.",
  },
  {
    value: "JOB_PATH",
    label: "The job's path",
    hint: "For a job per version, where the path itself names it. No key is needed.",
  },
];

function PipelineJobBindingsPanel({
  environments,
  applications,
  bindings,
  onChanged,
  onError,
}: {
  environments: Environment[];
  applications: Application[];
  bindings: PipelineJobBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [environmentId, setEnvironmentId] = useState("");
  const [applicationId, setApplicationId] = useState("");
  const [system, setSystem] = useState("");
  const [job, setJob] = useState("");
  const [versionSource, setVersionSource] = useState<VersionSource>("PARAMETER");
  const [versionKey, setVersionKey] = useState("");
  const [versionPattern, setVersionPattern] = useState("");

  const chosenSource = VERSION_SOURCES.find((s) => s.value === versionSource) ?? VERSION_SOURCES[0];

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindPipelineJob({
        environmentId,
        applicationId,
        connectorId: CI_CONNECTOR_ID,
        system,
        job,
        versionSource,
        versionKey,
        versionPattern,
      });
      setJob("");
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function remove(binding: PipelineJobBinding) {
    try {
      await unbindPipelineJob(binding.environmentId, binding.applicationId, binding.connectorId);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  const environmentName = (id: string) => environments.find((e) => e.id === id)?.name ?? id;
  const applicationName = (id: string) => applications.find((a) => a.id === id)?.name ?? id;
  const sourceLabel = (source: VersionSource) =>
    VERSION_SOURCES.find((s) => s.value === source)?.label ?? source;

  // One credential and one connection test per CI server, not per job: the
  // credential store is keyed by Connector and server address, so one token
  // serves every job on the same Jenkins.
  const servers = [...new Set(bindings.map((binding) => binding.system))].sort();

  return (
    <div className="panel">
      <h3>Pipeline jobs</h3>
      <p className="hint">
        Which job&apos;s runs mean an Application reached an Environment. Tower reads runs and
        nothing else: it never starts a job, never stops one, never retries one and never changes a
        configuration.
      </p>
      <p className="hint">
        Only <strong>deployment</strong> jobs belong here. A successful run of one becomes an
        Observation carrying the instant the run reported; a run that failed, was aborted or ended
        in a state Tower does not recognise is reported and not recorded, because it is not
        evidence that anything reached an Environment.
      </p>
      <p className="hint">
        Tower will not read a console log to find the version. Correctness would then depend on log
        formatting, and a wrong parse writes Observations that cannot be edited afterwards — so
        where the version lives is configuration rather than a guess.
      </p>

      {bindings.length === 0 && <p className="hint">No pipeline job is bound yet.</p>}

      {bindings.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Environment</th>
              <th>Application</th>
              <th>CI server</th>
              <th>Job</th>
              <th>Version from</th>
              <th>Key</th>
              <th>Version pattern</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {bindings.map((binding) => (
              <tr key={`${binding.environmentId}-${binding.applicationId}-${binding.connectorId}`}>
                <td>{environmentName(binding.environmentId)}</td>
                <td>{applicationName(binding.applicationId)}</td>
                <td className="mono">{binding.system}</td>
                <td className="mono">{binding.job}</td>
                <td>{sourceLabel(binding.versionSource)}</td>
                {/* An em dash rather than a blank cell: a key is meaningless
                    unless the version comes from a parameter, and an empty cell
                    reads as something somebody forgot. */}
                <td className="mono">{binding.versionKey || "—"}</td>
                <td className="mono">{binding.versionPattern}</td>
                <td>
                  <button type="button" className="button-link" onClick={() => void remove(binding)}>
                    Unbind
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form" onSubmit={(event) => void save(event)}>
        <label>
          Environment
          <select
            value={environmentId}
            onChange={(event) => setEnvironmentId(event.target.value)}
            required
          >
            <option value="">Choose…</option>
            {environments.map((environment) => (
              <option key={environment.id} value={environment.id}>
                {environment.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Application
          <select
            value={applicationId}
            onChange={(event) => setApplicationId(event.target.value)}
            required
          >
            <option value="">Choose…</option>
            {applications.map((application) => (
              <option key={application.id} value={application.id}>
                {application.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          CI server
          <input
            value={system}
            onChange={(event) => setSystem(event.target.value)}
            placeholder="https://ci.acme.example"
            required
          />
        </label>
        {/* A job inside a folder is written with each folder in the path. How
            that becomes a URL is the Connector's business alone. */}
        <label>
          Job
          <input
            value={job}
            onChange={(event) => setJob(event.target.value)}
            placeholder="team/deploy-uat"
            required
          />
        </label>
        <label>
          Version from
          <select
            value={versionSource}
            onChange={(event) => setVersionSource(event.target.value as VersionSource)}
          >
            {VERSION_SOURCES.map((source) => (
              <option key={source.value} value={source.value}>
                {source.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Key
          <input
            value={versionKey}
            onChange={(event) => setVersionKey(event.target.value)}
            placeholder={versionSource === "PARAMETER" ? "VERSION" : "not used"}
            disabled={versionSource !== "PARAMETER"}
          />
        </label>
        <label>
          Version pattern
          <input
            value={versionPattern}
            onChange={(event) => setVersionPattern(event.target.value)}
            placeholder="^(.+)$ — the whole value"
          />
        </label>
        <button type="submit">Bind</button>
      </form>
      <p className="field-hint">{chosenSource.hint}</p>

      {servers.map((address) => (
        <PipelineServerRow
          key={address}
          system={address}
          job={bindings.find((binding) => binding.system === address)?.job ?? ""}
          onError={onError}
        />
      ))}
    </div>
  );
}

// One CI server: its credential, and whether a job on it can be read.
//
// The test takes a job as well as the server, because a credential that reaches
// Jenkins may still not see the job — a test that only asked about the server
// would pass while every read failed.
function PipelineServerRow({
  system,
  job,
  onError,
}: {
  system: string;
  job: string;
  onError: (message: string) => void;
}) {
  const [credential, setCredential] = useState<CredentialStatus | null>(null);
  const [secret, setSecret] = useState("");
  const [test, setTest] = useState<ConnectionTest | null>(null);

  const refreshCredential = useCallback(async () => {
    try {
      setCredential(await getCredentialStatus(CI_CONNECTOR_ID, system));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }, [system, onError]);

  useEffect(() => {
    void refreshCredential();
  }, [refreshCredential]);

  async function check() {
    try {
      setTest(await testPipelineConnection(CI_CONNECTOR_ID, system, job));
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function saveSecret(event: React.FormEvent) {
    event.preventDefault();
    try {
      await storeCredential(CI_CONNECTOR_ID, system, secret);
      // Cleared as soon as it is sent, like every other token field here.
      setSecret("");
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function forget() {
    try {
      await forgetCredential(CI_CONNECTOR_ID, system);
      await refreshCredential();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  return (
    <div className="binding">
      <div className="binding__header">
        <span className="mono">{system}</span>
        {/* Tested against a job this server actually has, because reaching
            Jenkins and being allowed to read a job are different permissions. */}
        <button type="button" onClick={() => void check()} disabled={!job}>
          Test connection
        </button>
      </div>

      {test && (
        <p className="connection-test" data-reachable={test.reachable}>
          {test.reachable ? "✓ " : "✗ "}
          {test.message}
        </p>
      )}

      <form className="inline-form" onSubmit={(event) => void saveSecret(event)}>
        <label>
          Token
          <input
            type="password"
            value={secret}
            onChange={(event) => setSecret(event.target.value)}
            placeholder={credential?.configured ? "A token is stored" : "user-name:api-token"}
            autoComplete="off"
            required
          />
        </label>
        <button type="submit">{credential?.configured ? "Replace" : "Save"}</button>
        {credential?.configured && (
          <button type="button" className="button-link" onClick={() => void forget()}>
            Forget
          </button>
        )}
      </form>

      <p className="hint">
        {credential?.configured
          ? "A token is stored for this server. It is encrypted at rest and is never shown again — replace it rather than reading it back."
          : "Your user name, a colon, then an API token from your Jenkins user page — not your password."}
      </p>
    </div>
  );
}

// What reading the pipelines produced (ADR-020, OQ-017).
//
// A second history beside Synchronization above, and kept apart deliberately.
// The two answer different questions and one line cannot say both: a Deployment
// Platform reads what is running, so a clean run means "everything Tower knew is
// still there". A CI system reads what happened, so a clean read means only
// "nothing was deployed by these jobs since Tower last looked".
//
// Merging them would need one of the two to say something it does not know.
// OQ-017 weighed that against a user checking two places and chose this: two
// sections on one screen, each stating what its own clean read establishes.
function PipelineSyncPanel({
  reports,
  onDone,
  onError,
}: {
  reports: PipelineSyncReport[];
  onDone: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [running, setRunning] = useState(false);
  const latest = reports[0];

  async function run() {
    setRunning(true);
    try {
      await synchronizePipelinesNow();
      await onDone();
    } catch (caught: unknown) {
      onError(describeError(caught));
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel__header">
        <h3>Pipeline runs</h3>
        <button type="button" onClick={() => void run()} disabled={running}>
          {running ? "Reading runs…" : "Read pipeline runs now"}
        </button>
      </div>

      <p className="hint">
        Kept apart from Synchronization above, because the two establish different things. That one
        reads what is running now; this reads what happened. A screen that merged them would have
        to make one of them claim something Tower does not know.
      </p>

      {!latest && <p className="hint">Tower has not read any pipeline runs yet.</p>}

      {latest && (
        <>
          <p
            className="sync-outcome"
            data-outcome={latest.readEverything ? "SUCCEEDED" : "PARTIALLY_SUCCEEDED"}
          >
            <strong>{latest.readEverything ? "read everything" : "partly read"}</strong> —{" "}
            {latest.jobsRead} job{latest.jobsRead === 1 ? "" : "s"}, {latest.runsRead} run
            {latest.runsRead === 1 ? "" : "s"}, {latest.observationsAppended} new observation
            {latest.observationsAppended === 1 ? "" : "s"} at{" "}
            {new Date(latest.finishedAt).toLocaleString()}.
          </p>

          {/*
            Narrower than the Deployment Platform's equivalent, and worded so the
            difference carries. This is the whole of ADR-020 on one line.
          */}
          {latest.confirmsNothingWasDeployed && (
            <p className="hint">
              Nothing new was recorded. These jobs deployed nothing since Tower last looked — which
              says nothing about what is running now.
            </p>
          )}

          {latest.failures.length > 0 && (
            <div className="sync-failures">
              <h4>Could not be read</h4>
              <ul>
                {latest.failures.map((failure) => (
                  <li key={failure}>{failure}</li>
                ))}
              </ul>
              <p className="hint">
                Observations recorded before this read remain valid. A job Tower could not read
                simply produced no new facts.
              </p>
            </div>
          )}

          {/*
            The part a team with an untidy CI system will look at most, and the
            reason it exists: a run silently dropped would leave somebody
            wondering why a deployment they watched happen is not here.
          */}
          {latest.notRecorded.length > 0 && (
            <div className="sync-unrecognized">
              <h4>Read, and deliberately not recorded</h4>
              <p className="hint">
                A run that did not succeed is not evidence anything was deployed. A run whose
                version could not be found, or did not match the pattern, cannot be attributed
                without guessing — and Tower will not guess.
              </p>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Job</th>
                    <th>Run</th>
                    <th>Outcome</th>
                    <th>Why</th>
                  </tr>
                </thead>
                <tbody>
                  {latest.notRecorded.map((notRecorded) => (
                    <tr key={`${notRecorded.job}#${notRecorded.runId}`}>
                      <td className="mono">{notRecorded.job}</td>
                      <td className="mono">{notRecorded.runId}</td>
                      <td>{notRecorded.outcome}</td>
                      <td>{notRecorded.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// --- Build jobs (ADR-020) ---------------------------------------------------

// Which job's runs build an Application.
//
// The sibling of the pipeline jobs above, and the difference between them is
// the whole of ADR-020: that panel names an Environment, this one does not. A
// build says what was produced, not where it went, so a run of one of these
// proposes a candidate Application Version instead of recording an Observation.
//
// Which makes this the mildest binding on the page. A wrong version pattern on a
// deployment job writes Observations that are immutable and outlive the
// correction; a wrong one here produces a candidate nobody accepts, or none at
// all, and nothing is written either way.
function BuildJobBindingsPanel({
  applications,
  bindings,
  onChanged,
  onError,
}: {
  applications: Application[];
  bindings: BuildJobBinding[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [applicationId, setApplicationId] = useState("");
  const [system, setSystem] = useState("");
  const [job, setJob] = useState("");
  const [versionSource, setVersionSource] = useState<VersionSource>("PARAMETER");
  const [versionKey, setVersionKey] = useState("");
  const [versionPattern, setVersionPattern] = useState("");

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await bindBuildJob({
        applicationId,
        connectorId: CI_CONNECTOR_ID,
        system,
        job,
        versionSource,
        versionKey,
        versionPattern,
      });
      setJob("");
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  async function remove(binding: BuildJobBinding) {
    try {
      await unbindBuildJob(binding.applicationId, binding.connectorId, binding.job);
      await onChanged();
    } catch (caught: unknown) {
      onError(describeError(caught));
    }
  }

  const applicationName = (id: string) => applications.find((a) => a.id === id)?.name ?? id;
  const sourceLabel = (source: VersionSource) =>
    VERSION_SOURCES.find((s) => s.value === source)?.label ?? source;

  return (
    <div className="panel">
      <h3>Build jobs</h3>
      <p className="hint">
        Which job&apos;s runs build an Application. No Environment here, and that is the point: a
        build says what was produced, not where it went.
      </p>
      <p className="hint">
        A successful run of one of these <strong>proposes</strong> a version on the Applications
        page, beside the ones source control offers. Nothing is registered by discovering it —
        whether a candidate becomes an Application Version stays a person&apos;s decision, taken
        through the ordinary form.
      </p>
      <p className="hint">
        More than one build job per Application is allowed here, unlike a deployment job per
        Environment: a team may build a service and its migrations separately, and both propose
        versions worth seeing.
      </p>

      {bindings.length === 0 && <p className="hint">No build job is bound yet.</p>}

      {bindings.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Application</th>
              <th>CI server</th>
              <th>Job</th>
              <th>Version from</th>
              <th>Key</th>
              <th>Version pattern</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {bindings.map((binding) => (
              <tr key={`${binding.applicationId}-${binding.connectorId}-${binding.job}`}>
                <td>{applicationName(binding.applicationId)}</td>
                <td className="mono">{binding.system}</td>
                <td className="mono">{binding.job}</td>
                <td>{sourceLabel(binding.versionSource)}</td>
                <td className="mono">{binding.versionKey || "—"}</td>
                <td className="mono">{binding.versionPattern}</td>
                <td>
                  <button type="button" className="button-link" onClick={() => void remove(binding)}>
                    Unbind
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form" onSubmit={(event) => void save(event)}>
        <label>
          Application
          <select
            value={applicationId}
            onChange={(event) => setApplicationId(event.target.value)}
            required
          >
            <option value="">Choose…</option>
            {applications.map((application) => (
              <option key={application.id} value={application.id}>
                {application.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          CI server
          <input
            value={system}
            onChange={(event) => setSystem(event.target.value)}
            placeholder="https://ci.acme.example"
            required
          />
        </label>
        <label>
          Job
          <input
            value={job}
            onChange={(event) => setJob(event.target.value)}
            placeholder="team/build-customer-api"
            required
          />
        </label>
        <label>
          Version from
          <select
            value={versionSource}
            onChange={(event) => setVersionSource(event.target.value as VersionSource)}
          >
            {VERSION_SOURCES.map((source) => (
              <option key={source.value} value={source.value}>
                {source.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Key
          <input
            value={versionKey}
            onChange={(event) => setVersionKey(event.target.value)}
            placeholder={versionSource === "PARAMETER" ? "VERSION" : "not used"}
            disabled={versionSource !== "PARAMETER"}
          />
        </label>
        <label>
          Version pattern
          <input
            value={versionPattern}
            onChange={(event) => setVersionPattern(event.target.value)}
            placeholder="^(.+)$ — the whole value"
          />
        </label>
        <button type="submit">Bind</button>
      </form>

      <p className="field-hint">
        The credential is the one saved for this CI server under Pipeline jobs above — the store is
        keyed by server, so one token serves the build jobs and the deployment jobs alike.
      </p>
    </div>
  );
}
