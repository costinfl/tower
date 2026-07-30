import { useState } from "react";
import {
  getHandoverHistory,
  updateReleasePackHandover,
  type HandoverRevision,
  type ReleasePackHandoverInput,
  type ReleasePackView,
} from "../api/client";
import ErrorNote from "./ErrorNote";

interface HandoverField {
  key: keyof ReleasePackHandoverInput;
  label: string;
  hint: string;
}

// Order mirrors the REST contract's handover payload. Labels and hints are
// written for the person who has to actually carry out a deployment from
// this text, not for the person who wrote it.
const FIELDS: HandoverField[] = [
  { key: "deploymentInstructions", label: "Deployment Instructions", hint: "How to deploy this Release Pack, step by step." },
  { key: "shellCommands", label: "Shell Commands", hint: "Exact commands to run, ready to copy and paste." },
  { key: "databaseMigrations", label: "Database Migrations", hint: "Schema or data migrations that must run, and when." },
  { key: "rollbackProcedure", label: "Rollback Procedure", hint: "How to undo this release if something goes wrong." },
  { key: "validationNotes", label: "Validation Notes", hint: "What to check to confirm the release behaves correctly." },
  { key: "operationalNotes", label: "Operational Notes", hint: "Anything else an operator should know — alerts, dashboards, contacts." },
];

function isEmptyHandover(handover: ReleasePackHandoverInput): boolean {
  return FIELDS.every((f) => handover[f.key].trim().length === 0);
}

interface HandoverEditorProps {
  packId: string;
  handover: ReleasePackHandoverInput;
  archived: boolean;
  onUpdated: (pack: ReleasePackView) => void;
}

// The Handover is what gets handed to another team to carry out a
// deployment (ADR-004), so it is edited as one comfortable multi-line form
// rather than field-by-field, and its empty state is stated plainly rather
// than left to look broken.
export default function HandoverEditor({ packId, handover, archived, onUpdated }: HandoverEditorProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<ReleasePackHandoverInput>(handover);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  // Handover history (issue #8, ADR-016). Fetched on request rather than with
  // the pack: it is the question asked after something went wrong, not on every
  // visit, and a Release Pack listing should not pay for it.
  const [history, setHistory] = useState<HandoverRevision[] | null>(null);
  const [historyBusy, setHistoryBusy] = useState(false);

  function loadHistory() {
    setHistoryBusy(true);
    setError(null);
    getHandoverHistory(packId)
      .then(setHistory)
      .catch((e: unknown) => setError(e))
      .finally(() => setHistoryBusy(false));
  }

  function startEdit() {
    setDraft(handover);
    setError(null);
    setEditing(true);
  }

  function cancel() {
    setEditing(false);
    setError(null);
  }

  function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    updateReleasePackHandover(packId, draft)
      .then((updated) => {
        onUpdated(updated);
        setEditing(false);
      })
      .catch((err: unknown) => setError(err))
      .finally(() => setBusy(false));
  }

  if (!editing) {
    const empty = isEmptyHandover(handover);
    return (
      <div className="handover">
        <div className="handover__toolbar">
          {!archived && (
            <button type="button" onClick={startEdit}>
              {empty ? "Prepare Handover" : "Edit Handover"}
            </button>
          )}
          <button type="button" onClick={loadHistory} disabled={historyBusy}>
            {historyBusy ? "Loading…" : history === null ? "History" : "Refresh history"}
          </button>
        </div>
        {empty ? (
          <p className="hint">Nothing has been prepared for this Release Pack's handover yet.</p>
        ) : (
          <dl className="handover__fields">
            {FIELDS.map((f) => (
              <div className="handover__field" key={f.key}>
                <dt>{f.label}</dt>
                <dd>{handover[f.key].trim() ? handover[f.key] : <span className="hint">Not prepared yet.</span>}</dd>
              </div>
            ))}
          </dl>
        )}

        {error !== null && <ErrorNote error={error} />}
        {history !== null && <HandoverHistory revisions={history} />}
      </div>
    );
  }

  return (
    <form className="handover handover--editing" onSubmit={save}>
      {FIELDS.map((f) => (
        <label className="field handover__input" key={f.key}>
          <span>{f.label}</span>
          <span className="field-hint">{f.hint}</span>
          <textarea
            value={draft[f.key]}
            onChange={(e) => setDraft((prev) => ({ ...prev, [f.key]: e.target.value }))}
            rows={4}
          />
        </label>
      ))}
      {error !== null && <ErrorNote error={error} />}
      <div className="handover__actions">
        <button type="submit" disabled={busy}>
          {busy ? "Saving…" : "Save Handover"}
        </button>
        <button type="button" onClick={cancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}

// Every version the Handover has had (issue #8, ADR-016).
//
// The question this answers is asked after a deployment went wrong: what did we
// actually hand over. Until Tower kept these, an edit destroyed the answer.
//
// Read-only, and there is no "restore" button. Putting an old Handover back is
// an edit like any other — copy the text into the editor and save it, which
// appends a new revision rather than rewriting history.
function HandoverHistory({ revisions }: { revisions: HandoverRevision[] }) {
  const [open, setOpen] = useState<string | null>(null);

  if (revisions.length === 0) {
    // Distinct from "nothing was prepared": this Release Pack predates
    // versioning, or its Handover has genuinely never been saved.
    return <p className="hint">No edits recorded yet. The next save will be revision 1.</p>;
  }

  return (
    <div className="handover-history">
      <h5>History</h5>
      <p className="hint">
        Every edit is kept. Nothing here can be changed or removed — to go back to an earlier
        version, copy its text into the editor and save, which records that as a new revision.
      </p>
      <ol className="handover-history__list">
        {revisions.map((revision) => (
          <li className="handover-history__item" key={revision.id}>
            <button
              type="button"
              className="button-link"
              onClick={() => setOpen(open === revision.id ? null : revision.id)}
            >
              Revision {revision.revisionNumber}
            </button>
            <span className="handover-history__when">{revision.recordedAt}</span>
            {revision.current && <span className="handover-history__badge">current</span>}
            {revision.empty && <span className="hint">nothing prepared</span>}

            {open === revision.id && (
              <dl className="handover__fields">
                {FIELDS.map((f) => (
                  <div className="handover__field" key={f.key}>
                    <dt>{f.label}</dt>
                    <dd>
                      {revision[f.key].trim() ? revision[f.key] : <span className="hint">Not prepared.</span>}
                    </dd>
                  </div>
                ))}
              </dl>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
