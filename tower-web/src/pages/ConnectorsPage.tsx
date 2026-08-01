import { useCallback, useEffect, useState } from "react";
import {
  ApplicationBinding,
  Application,
  ConnectionTest,
  CredentialStatus,
  Environment,
  EnvironmentBinding,
  IssueTrackerBinding,
  IssueTrackerConnectionReport,
  RefSelection,
  RepositoryBinding,
  SyncRun,
  bindApplication,
  bindIssueTracker,
  bindRepository,
  bindEnvironment,
  forgetCredential,
  getApplications,
  getCredentialStatus,
  getEnvironments,
  listApplicationBindings,
  listEnvironmentBindings,
  listIssueTrackerBindings,
  listSyncRuns,
  previewVersion,
  storeCredential,
  synchronizeNow,
  testConnection,
  listRepositoryBindings,
  testIssueTrackerConnection,
  testRepositoryConnection,
  unbindApplication,
  unbindIssueTracker,
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
  const [runs, setRuns] = useState<SyncRun[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [envs, apps, envBindings, appBindings, repoBindings, trackers, history] =
        await Promise.all([
          getEnvironments(),
          getApplications(),
          listEnvironmentBindings(),
          listApplicationBindings(),
          listRepositoryBindings(),
          listIssueTrackerBindings(),
          listSyncRuns(10),
        ]);
      setEnvironments(envs);
      setApplications(apps);
      setEnvironmentBindings(envBindings);
      setApplicationBindings(appBindings);
      setRepositoryBindings(repoBindings);
      setTrackerBindings(trackers);
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
