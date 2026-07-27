import { useState } from "react";
import {
  addReleasePackIteration,
  completeReleasePackIteration,
  deleteReleasePackIteration,
  reopenReleasePackIteration,
  updateReleasePackIterationNotes,
  type ReleasePackIteration,
  type ReleasePackView,
} from "../api/client";
import { iterationStateMeta } from "../domain/iteration";
import ErrorNote, { describeError } from "./ErrorNote";

function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

interface IterationsPanelProps {
  packId: string;
  iterations: ReleasePackIteration[];
  archived: boolean;
  onUpdated: (pack: ReleasePackView) => void;
}

// Validation Iterations track the rounds a Release Pack goes through
// before handover. State (open/complete) is conveyed the same way Stage
// is (domain/iteration.ts): colour, glyph and text label together, never
// colour alone.
export default function IterationsPanel({ packId, iterations, archived, onUpdated }: IterationsPanelProps) {
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState("");
  const [newStartedAt, setNewStartedAt] = useState(() => toLocalInputValue(new Date()));
  const [newNotes, setNewNotes] = useState("");
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  const [notesEditingId, setNotesEditingId] = useState<string | null>(null);
  const [notesDraft, setNotesDraft] = useState("");
  const [notesBusy, setNotesBusy] = useState(false);
  const [notesError, setNotesError] = useState<unknown>(null);

  const [busyId, setBusyId] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<string, unknown>>({});

  function clearRowError(id: string) {
    setRowErrors((prev) => {
      const { [id]: _drop, ...rest } = prev;
      return rest;
    });
  }

  function startCreate() {
    setCreating(true);
    setNewName("");
    setNewStartedAt(toLocalInputValue(new Date()));
    setNewNotes("");
    setCreateError(null);
  }

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreateBusy(true);
    setCreateError(null);
    addReleasePackIteration(packId, {
      name: newName.trim(),
      startedAt: new Date(newStartedAt).toISOString(),
      notes: newNotes.trim(),
    })
      .then((updated) => {
        onUpdated(updated);
        setCreating(false);
      })
      .catch((err: unknown) => setCreateError(err))
      .finally(() => setCreateBusy(false));
  }

  function complete(iteration: ReleasePackIteration) {
    setBusyId(iteration.id);
    clearRowError(iteration.id);
    completeReleasePackIteration(packId, iteration.id, new Date().toISOString())
      .then(onUpdated)
      .catch((err: unknown) => setRowErrors((prev) => ({ ...prev, [iteration.id]: err })))
      .finally(() => setBusyId(null));
  }

  function reopen(iteration: ReleasePackIteration) {
    setBusyId(iteration.id);
    clearRowError(iteration.id);
    reopenReleasePackIteration(packId, iteration.id)
      .then(onUpdated)
      .catch((err: unknown) => setRowErrors((prev) => ({ ...prev, [iteration.id]: err })))
      .finally(() => setBusyId(null));
  }

  function remove(iteration: ReleasePackIteration) {
    setBusyId(iteration.id);
    clearRowError(iteration.id);
    deleteReleasePackIteration(packId, iteration.id)
      .then(onUpdated)
      .catch((err: unknown) => setRowErrors((prev) => ({ ...prev, [iteration.id]: err })))
      .finally(() => setBusyId(null));
  }

  function startNotesEdit(iteration: ReleasePackIteration) {
    setNotesEditingId(iteration.id);
    setNotesDraft(iteration.notes);
    setNotesError(null);
  }

  function saveNotes(iteration: ReleasePackIteration) {
    setNotesBusy(true);
    setNotesError(null);
    updateReleasePackIterationNotes(packId, iteration.id, notesDraft)
      .then((updated) => {
        onUpdated(updated);
        setNotesEditingId(null);
      })
      .catch((err: unknown) => setNotesError(err))
      .finally(() => setNotesBusy(false));
  }

  const sorted = iterations.slice().sort((a, b) => b.startedAt.localeCompare(a.startedAt));

  return (
    <div className="iterations">
      {!archived && (
        <div className="iterations__toolbar">
          <button type="button" onClick={startCreate} disabled={creating}>
            + New Iteration
          </button>
        </div>
      )}

      {creating && (
        <form className="iteration-form" onSubmit={submitCreate}>
          <label className="field">
            <span>Name</span>
            <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. Round 1" required />
          </label>
          <label className="field">
            <span>Started</span>
            <input type="datetime-local" value={newStartedAt} onChange={(e) => setNewStartedAt(e.target.value)} />
          </label>
          <label className="field">
            <span>Notes</span>
            <textarea value={newNotes} onChange={(e) => setNewNotes(e.target.value)} rows={2} />
          </label>
          {createError !== null && <ErrorNote error={createError} />}
          <div className="iteration-form__actions">
            <button type="submit" disabled={createBusy || !newName.trim()}>
              {createBusy ? "Adding…" : "Add Iteration"}
            </button>
            <button type="button" onClick={() => setCreating(false)} disabled={createBusy}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {sorted.length === 0 && !creating && <p className="hint">No validation Iterations recorded yet.</p>}

      {sorted.length > 0 && (
        <ul className="iteration-list">
          {sorted.map((iteration) => {
            const meta = iterationStateMeta(iteration);
            const isNotesEditing = notesEditingId === iteration.id;
            return (
              <li className={`iteration-item iteration-state--${meta.className}`} key={iteration.id}>
                <div className="iteration-item__header">
                  <span className="iteration-item__state" title={meta.label}>
                    <span aria-hidden="true">{meta.glyph}</span> {meta.label}
                  </span>
                  <span className="iteration-item__name">{iteration.name}</span>
                  <span className="iteration-item__dates">
                    started {new Date(iteration.startedAt).toLocaleString()}
                    {iteration.completedAt && <> · completed {new Date(iteration.completedAt).toLocaleString()}</>}
                  </span>
                </div>

                {isNotesEditing ? (
                  <div className="iteration-item__notes-edit">
                    <textarea value={notesDraft} onChange={(e) => setNotesDraft(e.target.value)} rows={3} />
                    {notesError !== null && <ErrorNote error={notesError} />}
                    <div className="iteration-item__notes-actions">
                      <button type="button" onClick={() => saveNotes(iteration)} disabled={notesBusy}>
                        {notesBusy ? "Saving…" : "Save Notes"}
                      </button>
                      <button type="button" onClick={() => setNotesEditingId(null)} disabled={notesBusy}>
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="iteration-item__notes">
                    {iteration.notes.trim() ? iteration.notes : <span className="hint">No notes.</span>}
                  </p>
                )}

                {!archived && !isNotesEditing && (
                  <div className="iteration-item__actions">
                    {iteration.completedAt ? (
                      <button type="button" onClick={() => reopen(iteration)} disabled={busyId === iteration.id}>
                        Reopen
                      </button>
                    ) : (
                      <button type="button" onClick={() => complete(iteration)} disabled={busyId === iteration.id}>
                        Mark Complete
                      </button>
                    )}
                    <button type="button" onClick={() => startNotesEdit(iteration)} disabled={busyId === iteration.id}>
                      Edit Notes
                    </button>
                    <button
                      type="button"
                      className="danger"
                      onClick={() => remove(iteration)}
                      disabled={busyId === iteration.id}
                    >
                      {busyId === iteration.id ? "Working…" : "Delete"}
                    </button>
                  </div>
                )}
                {rowErrors[iteration.id] !== undefined && (
                  <div className="row-error">{describeError(rowErrors[iteration.id])}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
