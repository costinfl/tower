import { useEffect, useState } from "react";
import {
  createApplication,
  discoverSourceVersions,
  createApplicationVersion,
  deleteApplication,
  deleteApplicationVersion,
  getAllApplicationVersions,
  getApplications,
  updateApplication,
  type Application,
  type ApplicationVersion,
  type VersionDiscovery,
} from "../api/client";
import ErrorNote, { describeError } from "../components/ErrorNote";

interface NewVersionForm {
  version: string;
  branch: string;
  tag: string;
  commit: string;
  buildIdentifier: string;
}

const emptyVersionForm: NewVersionForm = { version: "", branch: "", tag: "", commit: "", buildIdentifier: "" };

export default function ApplicationsPage() {
  const [applications, setApplications] = useState<Application[] | null>(null);
  const [versions, setVersions] = useState<ApplicationVersion[] | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editBusy, setEditBusy] = useState(false);
  const [editError, setEditError] = useState<unknown>(null);

  const [appBusyId, setAppBusyId] = useState<string | null>(null);
  const [appErrors, setAppErrors] = useState<Record<string, unknown>>({});

  const [versionForms, setVersionForms] = useState<Record<string, NewVersionForm>>({});
  const [versionBusy, setVersionBusy] = useState<Record<string, boolean>>({});
  const [versionErrors, setVersionErrors] = useState<Record<string, unknown>>({});
  const [versionDeleteBusyId, setVersionDeleteBusyId] = useState<string | null>(null);

  // Source control discovery (issue #3, ADR-014). Fetched per Application on
  // request rather than with the page: it reaches a repository over the network,
  // and doing that for every Application on every visit would make the page slow
  // for a result most visits do not need.
  const [discoveries, setDiscoveries] = useState<Record<string, VersionDiscovery>>({});
  const [discoverBusy, setDiscoverBusy] = useState<Record<string, boolean>>({});

  function load() {
    setLoadError(null);
    Promise.all([getApplications(), getAllApplicationVersions()])
      .then(([apps, allVersions]) => {
        setApplications(apps);
        setVersions(allVersions);
      })
      .catch((error: unknown) => setLoadError(error));
  }

  useEffect(load, []);

  function versionsFor(applicationId: string): ApplicationVersion[] {
    return (versions ?? []).filter((v) => v.applicationId === applicationId);
  }

  function versionFormFor(applicationId: string): NewVersionForm {
    return versionForms[applicationId] ?? emptyVersionForm;
  }

  function setVersionForm(applicationId: string, patch: Partial<NewVersionForm>) {
    setVersionForms((prev) => ({ ...prev, [applicationId]: { ...versionFormFor(applicationId), ...patch } }));
  }

  function clearAppError(id: string) {
    setAppErrors((prev) => {
      const { [id]: _drop, ...rest } = prev;
      return rest;
    });
  }

  function clearVersionError(applicationId: string) {
    setVersionErrors((prev) => {
      const { [applicationId]: _drop, ...rest } = prev;
      return rest;
    });
  }

  function handleCreateApp(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreateBusy(true);
    setCreateError(null);
    createApplication({ name: newName.trim(), description: newDescription.trim() })
      .then((app) => {
        setApplications((prev) => (prev ? [...prev, app] : [app]));
        setNewName("");
        setNewDescription("");
      })
      .catch((error: unknown) => setCreateError(error))
      .finally(() => setCreateBusy(false));
  }

  function startEdit(app: Application) {
    setEditingId(app.id);
    setEditName(app.name);
    setEditDescription(app.description);
    setEditError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditError(null);
  }

  function saveEdit(id: string) {
    if (!editName.trim()) return;
    setEditBusy(true);
    setEditError(null);
    updateApplication(id, { name: editName.trim(), description: editDescription.trim() })
      .then((updated) => {
        setApplications((prev) => prev?.map((a) => (a.id === id ? updated : a)) ?? prev);
        setEditingId(null);
      })
      .catch((error: unknown) => setEditError(error))
      .finally(() => setEditBusy(false));
  }

  function removeApp(app: Application) {
    setAppBusyId(app.id);
    clearAppError(app.id);
    deleteApplication(app.id)
      .then(() => setApplications((prev) => prev?.filter((a) => a.id !== app.id) ?? prev))
      .catch((error: unknown) => setAppErrors((prev) => ({ ...prev, [app.id]: error })))
      .finally(() => setAppBusyId(null));
  }

  function registerVersion(applicationId: string, e: React.FormEvent) {
    e.preventDefault();
    const form = versionFormFor(applicationId);
    if (!form.version.trim()) return;
    setVersionBusy((prev) => ({ ...prev, [applicationId]: true }));
    clearVersionError(applicationId);
    createApplicationVersion({
      applicationId,
      version: form.version.trim(),
      branch: form.branch.trim() || undefined,
      tag: form.tag.trim() || undefined,
      commit: form.commit.trim() || undefined,
      buildIdentifier: form.buildIdentifier.trim() || undefined,
    })
      .then((created) => {
        setVersions((prev) => (prev ? [...prev, created] : [created]));
        setVersionForms((prev) => ({ ...prev, [applicationId]: emptyVersionForm }));
      })
      .catch((error: unknown) => setVersionErrors((prev) => ({ ...prev, [applicationId]: error })))
      .finally(() => setVersionBusy((prev) => ({ ...prev, [applicationId]: false })));
  }

  function discover(applicationId: string) {
    setDiscoverBusy((current) => ({ ...current, [applicationId]: true }));
    clearVersionError(applicationId);
    discoverSourceVersions(applicationId)
      .then((discovery) => setDiscoveries((current) => ({ ...current, [applicationId]: discovery })))
      .catch((e: unknown) => setVersionErrors((current) => ({ ...current, [applicationId]: e })))
      .finally(() => setDiscoverBusy((current) => ({ ...current, [applicationId]: false })));
  }

  // Fills the form rather than registering directly. The API offers no "import"
  // endpoint on purpose, so BR-01 lives in one place — and this is the better
  // interaction anyway: a discovered version is a candidate the user accepts,
  // and they see exactly what will be recorded before it is.
  function useCandidate(applicationId: string, candidate: VersionDiscovery["candidates"][number]) {
    setVersionForm(applicationId, {
      version: candidate.version,
      branch: candidate.branch ?? "",
      tag: candidate.tag ?? "",
      commit: candidate.commit,
      // Build identifier left alone: git has none, and ADR-014 keeps it absent
      // rather than deriving something plausible from a commit.
    });
  }

  function removeVersion(version: ApplicationVersion) {
    setVersionDeleteBusyId(version.id);
    clearVersionError(version.applicationId);
    deleteApplicationVersion(version.id)
      .then(() => setVersions((prev) => prev?.filter((v) => v.id !== version.id) ?? prev))
      .catch((error: unknown) => {
        // A 409 means a Release Pack still contains this version — surface
        // the server's message verbatim rather than a generic failure.
        setVersionErrors((prev) => ({ ...prev, [version.applicationId]: error }));
      })
      .finally(() => setVersionDeleteBusyId(null));
  }

  return (
    <section className="page">
      <h2>Applications</h2>
      <p className="page__intro">
        Applications are the deployment artifacts that Release Packs group together. Application Versions are
        immutable once registered — to change one, register a new version and remove the old one from any Release
        Pack that holds it.
      </p>

      <form className="inline-form" onSubmit={handleCreateApp}>
        <label className="field">
          <span>Name</span>
          <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. checkout-service" required />
        </label>
        <label className="field">
          <span>Description</span>
          <input value={newDescription} onChange={(e) => setNewDescription(e.target.value)} placeholder="optional" />
        </label>
        <button type="submit" disabled={createBusy || !newName.trim()}>
          {createBusy ? "Adding…" : "Add Application"}
        </button>
      </form>
      {createError !== null && <ErrorNote error={createError} />}

      {loadError !== null && <ErrorNote error={loadError} />}
      {applications === null && loadError === null && <p className="hint">Loading Applications…</p>}
      {applications !== null && applications.length === 0 && <p className="hint">No Applications yet.</p>}

      <div className="app-cards">
        {applications?.map((app) => {
          const isEditing = editingId === app.id;
          const appVersions = versionsFor(app.id);
          const discovery = discoveries[app.id];
          const form = versionFormFor(app.id);
          const busy = versionBusy[app.id] ?? false;

          return (
            <article className="app-card" key={app.id}>
              <header className="app-card__header">
                {isEditing ? (
                  <span className="app-card__edit">
                    <input value={editName} onChange={(e) => setEditName(e.target.value)} aria-label="Application name" />
                    <input
                      value={editDescription}
                      onChange={(e) => setEditDescription(e.target.value)}
                      aria-label="Application description"
                      placeholder="description"
                    />
                    <button type="button" onClick={() => saveEdit(app.id)} disabled={editBusy || !editName.trim()}>
                      {editBusy ? "Saving…" : "Save"}
                    </button>
                    <button type="button" onClick={cancelEdit} disabled={editBusy}>
                      Cancel
                    </button>
                  </span>
                ) : (
                  <>
                    <h3 className="app-card__name">{app.name}</h3>
                    <button type="button" className="link-button" onClick={() => startEdit(app)}>
                      Edit
                    </button>
                  </>
                )}
                <button
                  type="button"
                  className="danger"
                  onClick={() => removeApp(app)}
                  disabled={appBusyId === app.id}
                >
                  {appBusyId === app.id ? "Deleting…" : "Delete"}
                </button>
              </header>
              {isEditing && editError !== null && <ErrorNote error={editError} />}
              {!isEditing && app.description && <p className="app-card__description">{app.description}</p>}
              {appErrors[app.id] !== undefined && <div className="row-error">{describeError(appErrors[app.id])}</div>}

              <div className="app-card__versions">
                <h4>Versions</h4>
                {appVersions.length === 0 && <p className="hint">No versions registered yet.</p>}
                {appVersions.length > 0 && (
                  <ul className="version-list">
                    {appVersions.map((v) => (
                      <li className="version-item" key={v.id}>
                        <span className="version-item__version">{v.version}</span>
                        {v.branch && <span className="version-item__attr">branch {v.branch}</span>}
                        {v.tag && <span className="version-item__attr">tag {v.tag}</span>}
                        {v.commit && <span className="version-item__attr">commit {v.commit}</span>}
                        {v.buildIdentifier && <span className="version-item__attr">build {v.buildIdentifier}</span>}
                        <button
                          type="button"
                          className="danger version-item__delete"
                          onClick={() => removeVersion(v)}
                          disabled={versionDeleteBusyId === v.id}
                        >
                          {versionDeleteBusyId === v.id ? "Deleting…" : "Delete"}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                {/*
                  Source control discovery (issue #3, ADR-014). Sits above the
                  form it fills, because that is the order the work happens in.
                */}
                <div className="discovery">
                  <button
                    type="button"
                    onClick={() => discover(app.id)}
                    disabled={discoverBusy[app.id] === true}
                  >
                    {discoverBusy[app.id] === true ? "Reading repository…" : "Discover from source control"}
                  </button>
                  {discovery !== undefined && <DiscoveryResult discovery={discovery} onUse={(c) => useCandidate(app.id, c)} />}
                </div>

                <form className="inline-form inline-form--compact" onSubmit={(e) => registerVersion(app.id, e)}>
                  <label className="field">
                    <span>Version</span>
                    <input
                      value={form.version}
                      onChange={(e) => setVersionForm(app.id, { version: e.target.value })}
                      placeholder="e.g. 1.4.0"
                      required
                    />
                  </label>
                  <label className="field">
                    <span>Branch</span>
                    <input value={form.branch} onChange={(e) => setVersionForm(app.id, { branch: e.target.value })} placeholder="optional" />
                  </label>
                  <label className="field">
                    <span>Tag</span>
                    <input value={form.tag} onChange={(e) => setVersionForm(app.id, { tag: e.target.value })} placeholder="optional" />
                  </label>
                  <label className="field">
                    <span>Commit</span>
                    <input value={form.commit} onChange={(e) => setVersionForm(app.id, { commit: e.target.value })} placeholder="optional" />
                  </label>
                  <label className="field">
                    <span>Build</span>
                    <input
                      value={form.buildIdentifier}
                      onChange={(e) => setVersionForm(app.id, { buildIdentifier: e.target.value })}
                      placeholder="optional"
                    />
                  </label>
                  <button type="submit" disabled={busy || !form.version.trim()}>
                    {busy ? "Registering…" : "Register Version"}
                  </button>
                </form>
                {versionErrors[app.id] !== undefined && (
                  <div className="row-error">{describeError(versionErrors[app.id])}</div>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

// --- Source control discovery ----------------------------------------------

// Shows what a repository holds, and says plainly when it holds nothing or
// could not be read. Those three outcomes look identical in an empty list and
// lead to different next steps, which is why the API reports them separately.
function DiscoveryResult({
  discovery,
  onUse,
}: {
  discovery: VersionDiscovery;
  onUse: (candidate: VersionDiscovery["candidates"][number]) => void;
}) {
  if (discovery.failure !== null) {
    return <p className="row-error">{discovery.failure}</p>;
  }

  return (
    <>
      <p className="hint">
        Read from <code>{discovery.repositoryUrl}</code>. Nothing has been registered — choosing a
        version fills the form below, and you register it as you always would.
      </p>

      {discovery.candidates.length === 0 && (
        <p className="hint">
          The repository was read and holds nothing this binding recognises. If that is a surprise,
          the version pattern on the Connectors page is the place to look.
        </p>
      )}

      {discovery.candidates.length > 0 && (
        <ul className="candidate-list">
          {discovery.candidates.map((candidate) => (
            <li className="candidate" key={candidate.refName}>
              <span className="candidate__version">{candidate.version}</span>
              <span className="candidate__attr">
                {candidate.tag !== null ? `tag ${candidate.tag}` : `branch ${candidate.branch}`}
              </span>
              {/* Shortened for reading; the full hash is what gets registered. */}
              <span className="candidate__attr">commit {candidate.commit.slice(0, 8)}</span>
              {candidate.alreadyRegistered ? (
                <span className="candidate__known">already registered</span>
              ) : (
                <button type="button" onClick={() => onUse(candidate)}>
                  Use
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {discovery.unmatched.length > 0 && (
        <p className="hint">
          Not recognised as versions: {discovery.unmatched.join(", ")}. These are reported rather
          than dropped, so a version pattern that is wrong is visible rather than silent.
        </p>
      )}
    </>
  );
}
