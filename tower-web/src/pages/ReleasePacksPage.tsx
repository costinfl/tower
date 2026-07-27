import { useEffect, useMemo, useState } from "react";
import {
  archiveReleasePack,
  createReleasePack,
  deleteReleasePack,
  getAllApplicationVersions,
  getApplications,
  getPromotionPaths,
  getReleasePacks,
  restoreReleasePack,
  updateReleasePack,
  type Application,
  type ApplicationVersion,
  type PathView,
  type ReleasePackView,
} from "../api/client";
import ErrorNote, { describeError } from "../components/ErrorNote";
import HandoverEditor from "../components/HandoverEditor";
import IterationsPanel from "../components/IterationsPanel";
import ReleasePackContents from "../components/ReleasePackContents";
import ReleasePackPromotionPathPanel from "../components/ReleasePackPromotionPathPanel";

export default function ReleasePacksPage() {
  const [packs, setPacks] = useState<ReleasePackView[] | null>(null);
  const [applications, setApplications] = useState<Application[]>([]);
  const [versions, setVersions] = useState<ApplicationVersion[]>([]);
  const [paths, setPaths] = useState<PathView[]>([]);
  const [loadError, setLoadError] = useState<unknown>(null);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showArchived, setShowArchived] = useState(true);

  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  const [editingHeader, setEditingHeader] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editBusy, setEditBusy] = useState(false);
  const [editError, setEditError] = useState<unknown>(null);

  const [lifecycleBusy, setLifecycleBusy] = useState(false);
  const [lifecycleError, setLifecycleError] = useState<unknown>(null);

  function load() {
    setLoadError(null);
    Promise.all([getReleasePacks(), getApplications(), getAllApplicationVersions(), getPromotionPaths()])
      .then(([packList, apps, allVersions, pathList]) => {
        setPacks(packList);
        setApplications(apps);
        setVersions(allVersions);
        setPaths(pathList);
      })
      .catch((error: unknown) => setLoadError(error));
  }

  useEffect(load, []);

  const visiblePacks = useMemo(() => (packs ?? []).filter((p) => showArchived || !p.archived), [packs, showArchived]);
  const selectedPack = useMemo(() => packs?.find((p) => p.id === selectedId) ?? null, [packs, selectedId]);

  function replacePack(updated: ReleasePackView) {
    setPacks((prev) => prev?.map((p) => (p.id === updated.id ? updated : p)) ?? prev);
  }

  function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreateBusy(true);
    setCreateError(null);
    createReleasePack({ name: newName.trim(), description: newDescription.trim() })
      .then((created) => {
        setPacks((prev) => (prev ? [...prev, created] : [created]));
        setSelectedId(created.id);
        setNewName("");
        setNewDescription("");
        setCreating(false);
      })
      .catch((error: unknown) => setCreateError(error))
      .finally(() => setCreateBusy(false));
  }

  function startEditHeader(pack: ReleasePackView) {
    setEditName(pack.name);
    setEditDescription(pack.description);
    setEditError(null);
    setEditingHeader(true);
  }

  function saveHeader(pack: ReleasePackView) {
    if (!editName.trim()) return;
    setEditBusy(true);
    setEditError(null);
    updateReleasePack(pack.id, { name: editName.trim(), description: editDescription.trim() })
      .then((updated) => {
        replacePack(updated);
        setEditingHeader(false);
      })
      .catch((error: unknown) => setEditError(error))
      .finally(() => setEditBusy(false));
  }

  function archive(pack: ReleasePackView) {
    setLifecycleBusy(true);
    setLifecycleError(null);
    archiveReleasePack(pack.id)
      .then(replacePack)
      .catch((error: unknown) => setLifecycleError(error))
      .finally(() => setLifecycleBusy(false));
  }

  function restore(pack: ReleasePackView) {
    setLifecycleBusy(true);
    setLifecycleError(null);
    restoreReleasePack(pack.id)
      .then(replacePack)
      .catch((error: unknown) => setLifecycleError(error))
      .finally(() => setLifecycleBusy(false));
  }

  function remove(pack: ReleasePackView) {
    setLifecycleBusy(true);
    setLifecycleError(null);
    deleteReleasePack(pack.id)
      .then(() => {
        setPacks((prev) => prev?.filter((p) => p.id !== pack.id) ?? prev);
        setSelectedId((prev) => (prev === pack.id ? null : prev));
      })
      .catch((error: unknown) => {
        // A 409 means the pack still has Iterations (or other dependent
        // state) — surface the server's message verbatim rather than a
        // generic failure.
        setLifecycleError(error);
      })
      .finally(() => setLifecycleBusy(false));
  }

  return (
    <section className="page">
      <h2>Release Packs</h2>
      <p className="page__intro">
        The Release Pack is Tower's central concept: a logical grouping of Application Versions delivered together,
        with the Handover information and validation Iterations that accompany them. A pack pins a specific version
        of a Promotion Path, not just the path itself.
      </p>

      <div className="release-packs-toolbar">
        <label className="checkbox-field">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived packs
        </label>
        <button type="button" onClick={() => setCreating(true)} disabled={creating}>
          + New Release Pack
        </button>
      </div>

      {creating && (
        <form className="inline-form" onSubmit={handleCreate}>
          <label className="field">
            <span>Name</span>
            <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. 2026.08 Release" required />
          </label>
          <label className="field">
            <span>Description</span>
            <input value={newDescription} onChange={(e) => setNewDescription(e.target.value)} placeholder="optional" />
          </label>
          <button type="submit" disabled={createBusy || !newName.trim()}>
            {createBusy ? "Creating…" : "Create Release Pack"}
          </button>
          <button
            type="button"
            onClick={() => {
              setCreating(false);
              setCreateError(null);
            }}
            disabled={createBusy}
          >
            Cancel
          </button>
        </form>
      )}
      {createError !== null && <ErrorNote error={createError} />}

      {loadError !== null && <ErrorNote error={loadError} />}
      {packs === null && loadError === null && <p className="hint">Loading Release Packs…</p>}
      {packs !== null && visiblePacks.length === 0 && <p className="hint">No Release Packs to show.</p>}

      {packs !== null && visiblePacks.length > 0 && (
        <div className="release-packs-layout">
          <ul className="pack-list">
            {visiblePacks.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  className={
                    "pack-list__item" +
                    (p.id === selectedId ? " pack-list__item--active" : "") +
                    (p.archived ? " pack-list__item--archived" : "")
                  }
                  onClick={() => {
                    setSelectedId(p.id);
                    setEditingHeader(false);
                  }}
                >
                  <span className="pack-list__name">{p.name}</span>
                  {p.archived && <span className="badge badge--muted">Archived</span>}
                </button>
              </li>
            ))}
          </ul>

          <div className="pack-detail">
            {!selectedPack && <p className="hint">Select a Release Pack to see its details.</p>}

            {selectedPack && (
              <>
                <header className="pack-detail__header">
                  {editingHeader ? (
                    <div className="pack-detail__edit">
                      <input
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        aria-label="Release Pack name"
                      />
                      <input
                        value={editDescription}
                        onChange={(e) => setEditDescription(e.target.value)}
                        aria-label="Release Pack description"
                        placeholder="description"
                      />
                      <button type="button" onClick={() => saveHeader(selectedPack)} disabled={editBusy || !editName.trim()}>
                        {editBusy ? "Saving…" : "Save"}
                      </button>
                      <button type="button" onClick={() => setEditingHeader(false)} disabled={editBusy}>
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <>
                      <h3 className="pack-detail__name">{selectedPack.name}</h3>
                      {selectedPack.archived && <span className="badge badge--muted">Archived</span>}
                      <button type="button" className="link-button" onClick={() => startEditHeader(selectedPack)}>
                        Edit
                      </button>
                    </>
                  )}
                </header>
                {editingHeader && editError !== null && <ErrorNote error={editError} />}
                {!editingHeader && selectedPack.description && (
                  <p className="pack-detail__description">{selectedPack.description}</p>
                )}

                <div className="pack-detail__lifecycle">
                  {!selectedPack.archived ? (
                    <button type="button" onClick={() => archive(selectedPack)} disabled={lifecycleBusy}>
                      Archive
                    </button>
                  ) : (
                    <button type="button" onClick={() => restore(selectedPack)} disabled={lifecycleBusy}>
                      Restore
                    </button>
                  )}
                  <button type="button" className="danger" onClick={() => remove(selectedPack)} disabled={lifecycleBusy}>
                    Delete
                  </button>
                </div>
                {lifecycleError !== null && <div className="row-error">{describeError(lifecycleError)}</div>}

                <section className="pack-section">
                  <h4>Contents</h4>
                  <ReleasePackContents
                    packId={selectedPack.id}
                    contents={selectedPack.contents}
                    applications={applications}
                    versions={versions}
                    archived={selectedPack.archived}
                    onUpdated={replacePack}
                  />
                </section>

                <section className="pack-section">
                  <h4>Promotion Path</h4>
                  <ReleasePackPromotionPathPanel
                    packId={selectedPack.id}
                    promotionPath={selectedPack.promotionPath}
                    paths={paths}
                    archived={selectedPack.archived}
                    onUpdated={replacePack}
                  />
                </section>

                <section className="pack-section">
                  <h4>Handover</h4>
                  <HandoverEditor
                    packId={selectedPack.id}
                    handover={selectedPack.handover}
                    archived={selectedPack.archived}
                    onUpdated={replacePack}
                  />
                </section>

                <section className="pack-section">
                  <h4>Iterations</h4>
                  <IterationsPanel
                    packId={selectedPack.id}
                    iterations={selectedPack.iterations}
                    archived={selectedPack.archived}
                    onUpdated={replacePack}
                  />
                </section>
              </>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
