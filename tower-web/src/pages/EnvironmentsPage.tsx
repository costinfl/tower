import { useEffect, useState } from "react";
import {
  createEnvironment,
  deleteEnvironment,
  getAllApplicationVersions,
  getApplications,
  getEnvironments,
  updateEnvironment,
  type Application,
  type ApplicationVersion,
  type Environment,
  type Stage,
} from "../api/client";
import { stageMeta } from "../domain/stage";
import EnvironmentStatePanel from "../components/EnvironmentStatePanel";
import ErrorNote, { describeError } from "../components/ErrorNote";
import StageSelect from "../components/StageSelect";

export default function EnvironmentsPage() {
  const [environments, setEnvironments] = useState<Environment[] | null>(null);
  const [applications, setApplications] = useState<Application[]>([]);
  const [versions, setVersions] = useState<ApplicationVersion[]>([]);
  const [loadError, setLoadError] = useState<unknown>(null);

  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [newName, setNewName] = useState("");
  const [newStage, setNewStage] = useState<Stage>("DEVELOPMENT");
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editStage, setEditStage] = useState<Stage>("DEVELOPMENT");
  const [editBusy, setEditBusy] = useState(false);
  const [editError, setEditError] = useState<unknown>(null);

  const [deleteBusyId, setDeleteBusyId] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<string, unknown>>({});

  function load() {
    setLoadError(null);
    Promise.all([getEnvironments(), getApplications(), getAllApplicationVersions()])
      .then(([envs, apps, allVersions]) => {
        setEnvironments(envs);
        setApplications(apps);
        setVersions(allVersions);
      })
      .catch((error: unknown) => setLoadError(error));
  }

  useEffect(load, []);

  const selectedEnvironment = environments?.find((env) => env.id === selectedId) ?? null;

  function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreateBusy(true);
    setCreateError(null);
    createEnvironment({ name: newName.trim(), stage: newStage })
      .then((env) => {
        setEnvironments((prev) => (prev ? [...prev, env] : [env]));
        setNewName("");
        setNewStage("DEVELOPMENT");
      })
      .catch((error: unknown) => setCreateError(error))
      .finally(() => setCreateBusy(false));
  }

  function startEdit(env: Environment) {
    setEditingId(env.id);
    setEditName(env.name);
    setEditStage(env.stage);
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
    updateEnvironment(id, { name: editName.trim(), stage: editStage })
      .then((updated) => {
        setEnvironments((prev) => prev?.map((env) => (env.id === id ? updated : env)) ?? prev);
        setEditingId(null);
      })
      .catch((error: unknown) => setEditError(error))
      .finally(() => setEditBusy(false));
  }

  function handleDelete(id: string) {
    setDeleteBusyId(id);
    setRowErrors((prev) => {
      const { [id]: _drop, ...rest } = prev;
      return rest;
    });
    deleteEnvironment(id)
      .then(() => {
        setEnvironments((prev) => prev?.filter((env) => env.id !== id) ?? prev);
        setSelectedId((prev) => (prev === id ? null : prev));
      })
      .catch((error: unknown) => {
        // A 409 means a Promotion Path still references this Environment.
        // Show the server's message plainly rather than a generic failure.
        setRowErrors((prev) => ({ ...prev, [id]: error }));
      })
      .finally(() => setDeleteBusyId(null));
  }

  return (
    <section className="page">
      <h2>Environments</h2>
      <p className="page__intro">
        Environments are shared deployment destinations. A Stage classifies each Environment independently of its
        name, and one Environment may be referenced by several Promotion Paths at once.
      </p>

      <form className="inline-form" onSubmit={handleCreate}>
        <label className="field">
          <span>Name</span>
          <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. UAT" required />
        </label>
        <label className="field">
          <span>Stage</span>
          <StageSelect value={newStage} onChange={setNewStage} />
        </label>
        <button type="submit" disabled={createBusy || !newName.trim()}>
          {createBusy ? "Adding…" : "Add Environment"}
        </button>
      </form>
      {createError !== null && <ErrorNote error={createError} />}

      {loadError !== null && <ErrorNote error={loadError} />}
      {environments === null && loadError === null && <p className="hint">Loading Environments…</p>}

      {environments !== null && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Stage</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {environments.length === 0 && (
              <tr>
                <td colSpan={3} className="hint">
                  No Environments yet.
                </td>
              </tr>
            )}
            {environments.map((env) => {
              const isEditing = editingId === env.id;
              const meta = stageMeta(env.stage);
              return (
                <tr key={env.id}>
                  {isEditing ? (
                    <>
                      <td>
                        <input value={editName} onChange={(e) => setEditName(e.target.value)} />
                      </td>
                      <td>
                        <StageSelect value={editStage} onChange={setEditStage} />
                      </td>
                      <td className="data-table__actions">
                        <button type="button" onClick={() => saveEdit(env.id)} disabled={editBusy || !editName.trim()}>
                          {editBusy ? "Saving…" : "Save"}
                        </button>
                        <button type="button" onClick={cancelEdit} disabled={editBusy}>
                          Cancel
                        </button>
                        {editError !== null && <div className="row-error">{describeError(editError)}</div>}
                      </td>
                    </>
                  ) : (
                    <>
                      <td>{env.name}</td>
                      <td>
                        <span className={`stage-chip stage--${meta.className}`}>
                          <span aria-hidden="true">{meta.glyph}</span> {meta.name}
                        </span>
                      </td>
                      <td className="data-table__actions">
                        <button
                          type="button"
                          onClick={() => setSelectedId((prev) => (prev === env.id ? null : env.id))}
                          aria-expanded={selectedId === env.id}
                        >
                          {selectedId === env.id ? "Hide state" : "View state"}
                        </button>
                        <button type="button" onClick={() => startEdit(env)}>
                          Edit
                        </button>
                        <button
                          type="button"
                          className="danger"
                          onClick={() => handleDelete(env.id)}
                          disabled={deleteBusyId === env.id}
                        >
                          {deleteBusyId === env.id ? "Deleting…" : "Delete"}
                        </button>
                        {rowErrors[env.id] !== undefined && (
                          <div className="row-error">{describeError(rowErrors[env.id])}</div>
                        )}
                      </td>
                    </>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {selectedEnvironment && (
        // Keyed on the Environment so switching selection remounts the panel
        // rather than briefly showing the previous Environment's Observations
        // under the new heading.
        <EnvironmentStatePanel
          key={selectedEnvironment.id}
          environment={selectedEnvironment}
          applications={applications}
          versions={versions}
        />
      )}
    </section>
  );
}
