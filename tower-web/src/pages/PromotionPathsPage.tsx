import { useEffect, useMemo, useState } from "react";
import {
  addPromotionPathVersion,
  archivePromotionPath,
  createPromotionPath,
  deletePromotionPath,
  getEnvironments,
  getPromotionPaths,
  renamePromotionPath,
  restorePromotionPath,
  type Environment,
  type PathVersion,
  type PathView,
} from "../api/client";
import ErrorNote, { describeError } from "../components/ErrorNote";
import Lane from "../components/Lane";
import PathEditorForm, { type PathEditorSubmission } from "../components/PathEditorForm";
import StageLegend from "../components/StageLegend";

function displayedVersionOf(path: PathView, selected: number | undefined): PathVersion {
  if (selected === undefined) return path.currentVersion;
  return path.versions.find((v) => v.number === selected) ?? path.currentVersion;
}

export default function PromotionPathsPage() {
  const [environments, setEnvironments] = useState<Environment[] | null>(null);
  const [paths, setPaths] = useState<PathView[] | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  const [hoveredEnvironmentId, setHoveredEnvironmentId] = useState<string | null>(null);
  const [showArchived, setShowArchived] = useState(true);
  const [selectedVersion, setSelectedVersion] = useState<Record<string, number>>({});

  const [creatingNew, setCreatingNew] = useState(false);
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  const [editingVersionsFor, setEditingVersionsFor] = useState<string | null>(null);
  const [newVersionBusy, setNewVersionBusy] = useState(false);
  const [newVersionError, setNewVersionError] = useState<unknown>(null);

  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renameBusy, setRenameBusy] = useState(false);
  const [renameError, setRenameError] = useState<unknown>(null);

  const [busyPathId, setBusyPathId] = useState<string | null>(null);
  const [actionErrors, setActionErrors] = useState<Record<string, unknown>>({});

  function load() {
    setLoadError(null);
    Promise.all([getEnvironments(), getPromotionPaths()])
      .then(([envs, pathList]) => {
        setEnvironments(envs);
        setPaths(pathList);
      })
      .catch((error: unknown) => setLoadError(error));
  }

  useEffect(load, []);

  function replacePath(updated: PathView) {
    setPaths((prev) => prev?.map((p) => (p.id === updated.id ? updated : p)) ?? prev);
  }

  function clearActionError(id: string) {
    setActionErrors((prev) => {
      const { [id]: _drop, ...rest } = prev;
      return rest;
    });
  }

  const visiblePaths = useMemo(
    () => (paths ?? []).filter((p) => showArchived || !p.archived),
    [paths, showArchived],
  );

  // An Environment counts as "shared" for highlighting purposes when it
  // appears in the currently displayed topology of more than one visible
  // Lane. This is what makes ADR-005 convergence (Regular and Hotfix both
  // passing through UAT and Production) recognisable at a glance.
  const sharedEnvironmentIds = useMemo(() => {
    const owningPaths = new Map<string, Set<string>>();
    for (const path of visiblePaths) {
      const version = displayedVersionOf(path, selectedVersion[path.id]);
      for (const env of version.environments) {
        const owners = owningPaths.get(env.id) ?? new Set<string>();
        owners.add(path.id);
        owningPaths.set(env.id, owners);
      }
    }
    const shared = new Set<string>();
    for (const [envId, owners] of owningPaths) {
      if (owners.size > 1) shared.add(envId);
    }
    return shared;
  }, [visiblePaths, selectedVersion]);

  function handleCreate(submission: PathEditorSubmission) {
    setCreateBusy(true);
    setCreateError(null);
    createPromotionPath({ name: submission.name, environmentIds: submission.environmentIds })
      .then((created) => {
        setPaths((prev) => (prev ? [...prev, created] : [created]));
        setCreatingNew(false);
      })
      .catch((error: unknown) => setCreateError(error))
      .finally(() => setCreateBusy(false));
  }

  function startNewVersion(path: PathView) {
    setEditingVersionsFor(path.id);
    setNewVersionError(null);
  }

  function submitNewVersion(path: PathView, submission: PathEditorSubmission) {
    setNewVersionBusy(true);
    setNewVersionError(null);
    addPromotionPathVersion(path.id, { environmentIds: submission.environmentIds })
      .then((updated) => {
        replacePath(updated);
        setSelectedVersion((prev) => {
          const { [path.id]: _drop, ...rest } = prev;
          return rest;
        });
        setEditingVersionsFor(null);
      })
      .catch((error: unknown) => setNewVersionError(error))
      .finally(() => setNewVersionBusy(false));
  }

  function startRename(path: PathView) {
    setRenamingId(path.id);
    setRenameValue(path.name);
    setRenameError(null);
  }

  function saveRename(path: PathView) {
    if (!renameValue.trim()) return;
    setRenameBusy(true);
    setRenameError(null);
    renamePromotionPath(path.id, renameValue.trim())
      .then((updated) => {
        replacePath(updated);
        setRenamingId(null);
      })
      .catch((error: unknown) => setRenameError(error))
      .finally(() => setRenameBusy(false));
  }

  function archive(path: PathView) {
    setBusyPathId(path.id);
    clearActionError(path.id);
    archivePromotionPath(path.id)
      .then(replacePath)
      .catch((error: unknown) => setActionErrors((prev) => ({ ...prev, [path.id]: error })))
      .finally(() => setBusyPathId(null));
  }

  function restore(path: PathView) {
    setBusyPathId(path.id);
    clearActionError(path.id);
    restorePromotionPath(path.id)
      .then(replacePath)
      .catch((error: unknown) => setActionErrors((prev) => ({ ...prev, [path.id]: error })))
      .finally(() => setBusyPathId(null));
  }

  function remove(path: PathView) {
    setBusyPathId(path.id);
    clearActionError(path.id);
    deletePromotionPath(path.id)
      .then(() => setPaths((prev) => prev?.filter((p) => p.id !== path.id) ?? prev))
      .catch((error: unknown) => {
        // A 409 means Release Pack history references this Promotion Path;
        // ADR-007 says archive instead of delete in that case. Show the
        // server's message plainly so the user knows why.
        setActionErrors((prev) => ({ ...prev, [path.id]: error }));
      })
      .finally(() => setBusyPathId(null));
  }

  return (
    <section className="page">
      <h2>Promotion Paths</h2>
      <p className="page__intro">
        Each Lane below is the visual representation of one Promotion Path — an ordered sequence of Environments a
        release progresses through. Environments are shared across paths: hover or focus an Environment to see every
        other Lane it also appears in.
      </p>

      <StageLegend />

      <div className="lanes-toolbar">
        <label className="checkbox-field">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived paths
        </label>
        <button type="button" onClick={() => setCreatingNew(true)} disabled={creatingNew || environments === null}>
          + New Promotion Path
        </button>
      </div>

      {creatingNew && environments !== null && (
        <PathEditorForm
          title="New Promotion Path"
          allEnvironments={environments}
          initialSequence={[]}
          showNameField
          submitLabel="Create Path"
          busy={createBusy}
          error={createError}
          onSubmit={handleCreate}
          onCancel={() => {
            setCreatingNew(false);
            setCreateError(null);
          }}
        />
      )}

      {loadError !== null && <ErrorNote error={loadError} />}
      {paths === null && loadError === null && <p className="hint">Loading Promotion Paths…</p>}
      {paths !== null && visiblePaths.length === 0 && <p className="hint">No Promotion Paths to show.</p>}

      <div className="lanes">
        {visiblePaths.map((path) => {
          const displayed = displayedVersionOf(path, selectedVersion[path.id]);
          const isCurrent = displayed.number === path.currentVersion.number;
          const sortedVersions = path.versions.slice().sort((a, b) => b.number - a.number);

          return (
            <article className={`path-card ${path.archived ? "path-card--archived" : ""}`} key={path.id}>
              <header className="path-card__header">
                {renamingId === path.id ? (
                  <span className="path-card__rename">
                    <input
                      value={renameValue}
                      onChange={(e) => setRenameValue(e.target.value)}
                      aria-label="Promotion Path name"
                    />
                    <button type="button" onClick={() => saveRename(path)} disabled={renameBusy || !renameValue.trim()}>
                      {renameBusy ? "Saving…" : "Save"}
                    </button>
                    <button type="button" onClick={() => setRenamingId(null)} disabled={renameBusy}>
                      Cancel
                    </button>
                  </span>
                ) : (
                  <>
                    <h3 className="path-card__name">{path.name}</h3>
                    {!path.archived && (
                      <button type="button" className="link-button" onClick={() => startRename(path)}>
                        Rename
                      </button>
                    )}
                  </>
                )}
                {path.archived && <span className="badge badge--muted">Archived</span>}
                <span className="badge">
                  v{displayed.number}
                  {!isCurrent ? ` of ${path.currentVersion.number} (viewing history)` : ""}
                </span>
              </header>
              {renameError !== null && renamingId === path.id && <ErrorNote error={renameError} />}

              <Lane
                version={displayed}
                sharedEnvironmentIds={sharedEnvironmentIds}
                hoveredEnvironmentId={hoveredEnvironmentId}
                onHoverEnvironment={setHoveredEnvironmentId}
              />

              <footer className="path-card__footer">
                {sortedVersions.length > 1 && (
                  <label className="version-picker">
                    <span>Version</span>
                    <select
                      value={displayed.number}
                      onChange={(e) =>
                        setSelectedVersion((prev) => ({ ...prev, [path.id]: Number(e.target.value) }))
                      }
                    >
                      {sortedVersions.map((v) => (
                        <option value={v.number} key={v.number}>
                          v{v.number}
                          {v.number === path.currentVersion.number ? " (current)" : ""} —{" "}
                          {new Date(v.createdAt).toLocaleString()}
                        </option>
                      ))}
                    </select>
                  </label>
                )}

                <div className="path-card__actions">
                  {!path.archived && (
                    <button type="button" onClick={() => startNewVersion(path)}>
                      Publish New Version
                    </button>
                  )}
                  {!path.archived ? (
                    <button type="button" onClick={() => archive(path)} disabled={busyPathId === path.id}>
                      Archive
                    </button>
                  ) : (
                    <button type="button" onClick={() => restore(path)} disabled={busyPathId === path.id}>
                      Restore
                    </button>
                  )}
                  <button
                    type="button"
                    className="danger"
                    onClick={() => remove(path)}
                    disabled={busyPathId === path.id}
                  >
                    Delete
                  </button>
                </div>
                {actionErrors[path.id] !== undefined && (
                  <div className="row-error">{describeError(actionErrors[path.id])}</div>
                )}
              </footer>

              {editingVersionsFor === path.id && environments !== null && (
                <PathEditorForm
                  title={`Publish new version of ${path.name}`}
                  allEnvironments={environments}
                  initialSequence={path.currentVersion.environments}
                  showNameField={false}
                  submitLabel="Publish Version"
                  busy={newVersionBusy}
                  error={newVersionError}
                  onSubmit={(submission) => submitNewVersion(path, submission)}
                  onCancel={() => {
                    setEditingVersionsFor(null);
                    setNewVersionError(null);
                  }}
                />
              )}
            </article>
          );
        })}
      </div>
    </section>
  );
}
