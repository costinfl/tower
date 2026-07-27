import { useState } from "react";
import type { Environment } from "../api/client";
import { stageMeta } from "../domain/stage";
import ErrorNote from "./ErrorNote";

export interface PathEditorSubmission {
  name: string;
  environmentIds: string[];
}

interface PathEditorFormProps {
  title: string;
  allEnvironments: Environment[];
  initialSequence: Environment[];
  showNameField: boolean;
  initialName?: string;
  submitLabel: string;
  busy: boolean;
  error: unknown;
  onSubmit: (submission: PathEditorSubmission) => void;
  onCancel: () => void;
}

// Shared editor for "assemble an ordered sequence of Environments", used
// both to create a new Promotion Path and to publish a new version of an
// existing one (ADR-007: publishing a version is seeded from the current
// version's topology).
export default function PathEditorForm({
  title,
  allEnvironments,
  initialSequence,
  showNameField,
  initialName,
  submitLabel,
  busy,
  error,
  onSubmit,
  onCancel,
}: PathEditorFormProps) {
  const [name, setName] = useState(initialName ?? "");
  const [sequence, setSequence] = useState<Environment[]>(initialSequence);
  const [toAdd, setToAdd] = useState("");

  const availableToAdd = allEnvironments.filter((env) => !sequence.some((s) => s.id === env.id));

  function moveUp(index: number) {
    if (index === 0) return;
    setSequence((prev) => {
      const next = prev.slice();
      [next[index - 1], next[index]] = [next[index], next[index - 1]];
      return next;
    });
  }

  function moveDown(index: number) {
    setSequence((prev) => {
      if (index >= prev.length - 1) return prev;
      const next = prev.slice();
      [next[index + 1], next[index]] = [next[index], next[index + 1]];
      return next;
    });
  }

  function removeAt(index: number) {
    setSequence((prev) => prev.filter((_, i) => i !== index));
  }

  function handleAdd() {
    const env = allEnvironments.find((e) => e.id === toAdd);
    if (!env) return;
    setSequence((prev) => [...prev, env]);
    setToAdd("");
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    onSubmit({ name: name.trim(), environmentIds: sequence.map((env) => env.id) });
  }

  const canSubmit = sequence.length > 0 && (!showNameField || name.trim().length > 0);

  return (
    <form className="path-editor" onSubmit={handleSubmit}>
      <h3 className="path-editor__title">{title}</h3>

      {showNameField && (
        <label className="field">
          <span>Name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Regular" required />
        </label>
      )}

      <div className="path-editor__sequence">
        <span className="field-label">Environments, in order</span>
        {sequence.length === 0 && <p className="hint">Add at least one Environment below.</p>}
        <ol className="path-editor__list">
          {sequence.map((env, index) => {
            const meta = stageMeta(env.stage);
            return (
              <li className={`path-editor__item stage--${meta.className}`} key={`${env.id}-${index}`}>
                <span className="path-editor__item-glyph" aria-hidden="true">
                  {meta.glyph}
                </span>
                <span className="path-editor__item-name">{env.name}</span>
                <span className="path-editor__item-stage">{meta.label}</span>
                <span className="path-editor__item-actions">
                  <button
                    type="button"
                    onClick={() => moveUp(index)}
                    disabled={index === 0}
                    aria-label={`Move ${env.name} earlier in the sequence`}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    onClick={() => moveDown(index)}
                    disabled={index === sequence.length - 1}
                    aria-label={`Move ${env.name} later in the sequence`}
                  >
                    ↓
                  </button>
                  <button type="button" onClick={() => removeAt(index)} aria-label={`Remove ${env.name} from the sequence`}>
                    ✕
                  </button>
                </span>
              </li>
            );
          })}
        </ol>
      </div>

      <div className="path-editor__add">
        <select value={toAdd} onChange={(e) => setToAdd(e.target.value)} aria-label="Environment to add">
          <option value="">Add an Environment…</option>
          {availableToAdd.map((env) => (
            <option value={env.id} key={env.id}>
              {env.name} ({stageMeta(env.stage).name})
            </option>
          ))}
        </select>
        <button type="button" onClick={handleAdd} disabled={!toAdd}>
          Add
        </button>
      </div>

      {error !== undefined && error !== null && <ErrorNote error={error} />}

      <div className="path-editor__actions">
        <button type="submit" disabled={busy || !canSubmit}>
          {busy ? "Saving…" : submitLabel}
        </button>
        <button type="button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}
