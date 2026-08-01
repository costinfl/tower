import { useEffect, useState } from "react";
import {
  acceptWorkItemTitle,
  linkWorkItem,
  resolveWorkItems,
  unlinkWorkItem,
  type ReleasePackView,
  type ReleasePackWorkItem,
  type ResolvedWorkItem,
  type WorkItemResolution,
} from "../api/client";
import ErrorNote, { describeError } from "./ErrorNote";

interface WorkItemsPanelProps {
  packId: string;
  workItems: ReleasePackWorkItem[];
  archived: boolean;
  onUpdated: (pack: ReleasePackView) => void;
}

// What a release delivers, in the vocabulary the people receiving a handover
// use (ADR-018).
//
// Two sources side by side, and the distinction between them is the whole
// point. What Tower holds is Intent — an identifier and a title somebody
// accepted — and it is what generated documents print. What the tracker says is
// read on request and stored nowhere.
//
// Where the two differ the difference is shown, never silently corrected: a
// handover already given to another team does not change because a ticket was
// edited. Accepting the newer wording is a deliberate act, one button, and it
// is the only thing here that changes what a document will say.
export default function WorkItemsPanel({ packId, workItems, archived, onUpdated }: WorkItemsPanelProps) {
  const [identifier, setIdentifier] = useState("");
  const [title, setTitle] = useState("");
  const [addBusy, setAddBusy] = useState(false);
  const [addError, setAddError] = useState<unknown>(null);

  const [rowBusy, setRowBusy] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<string, unknown>>({});

  const [resolution, setResolution] = useState<WorkItemResolution | null>(null);
  const [resolveBusy, setResolveBusy] = useState(false);

  // Cleared whenever the release's own list changes, so a stale comparison
  // cannot outlive the thing it was comparing.
  useEffect(() => {
    setResolution(null);
  }, [packId, workItems]);

  function resolved(id: string): ResolvedWorkItem | undefined {
    return resolution?.items.find((item) => item.identifier.toLowerCase() === id.toLowerCase());
  }

  function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!identifier.trim()) return;
    setAddBusy(true);
    setAddError(null);
    linkWorkItem(packId, identifier.trim(), title.trim())
      .then((updated) => {
        onUpdated(updated);
        setIdentifier("");
        setTitle("");
      })
      .catch((err: unknown) => setAddError(err))
      .finally(() => setAddBusy(false));
  }

  function run(id: string, action: Promise<ReleasePackView>) {
    setRowBusy(id);
    setRowErrors((prev) => {
      const { [id]: _drop, ...rest } = prev;
      return rest;
    });
    action
      .then(onUpdated)
      .catch((err: unknown) => setRowErrors((prev) => ({ ...prev, [id]: err })))
      .finally(() => setRowBusy(null));
  }

  function check() {
    setResolveBusy(true);
    resolveWorkItems(packId)
      .then(setResolution)
      .catch((err: unknown) => setAddError(err))
      .finally(() => setResolveBusy(false));
  }

  return (
    <div className="work-items">
      {workItems.length === 0 && (
        <p className="hint">No work items have been linked to this Release Pack.</p>
      )}

      {workItems.length > 0 && (
        <ul className="work-item-list">
          {workItems.map((item) => {
            const live = resolved(item.identifier);
            return (
              <li className="work-item" key={item.identifier}>
                <div className="work-item__header">
                  <span className="work-item__id">{item.identifier}</span>
                  {/*
                    Said rather than left blank: an identifier with no accepted
                    title is a deliberate state, and a document prints it as
                    "no title accepted" too.
                  */}
                  {item.titleAccepted ? (
                    <span className="work-item__title">{item.title}</span>
                  ) : (
                    <span className="hint">no title accepted</span>
                  )}
                  {!archived && (
                    <button
                      type="button"
                      className="danger work-item__remove"
                      onClick={() => run(item.identifier, unlinkWorkItem(packId, item.identifier))}
                      disabled={rowBusy === item.identifier}
                    >
                      {rowBusy === item.identifier ? "Removing…" : "Remove"}
                    </button>
                  )}
                </div>

                {live && <TrackerLine item={live} />}

                {/*
                  The one control that changes what a document will say, and it
                  only appears when there is something to accept.
                */}
                {live?.diverged && !archived && (
                  <button
                    type="button"
                    className="link-button"
                    onClick={() =>
                      run(item.identifier,
                        acceptWorkItemTitle(packId, item.identifier, live.trackerTitle ?? ""))
                    }
                    disabled={rowBusy === item.identifier}
                  >
                    Accept the tracker's wording
                  </button>
                )}

                {rowErrors[item.identifier] !== undefined && (
                  <div className="row-error">{describeError(rowErrors[item.identifier])}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {workItems.length > 0 && (
        <div className="work-items__check">
          <button type="button" onClick={check} disabled={resolveBusy}>
            {resolveBusy ? "Reading the tracker…" : "Check against the tracker"}
          </button>
          {/*
            A tracker that could not be read is stated, and the list above is
            unaffected — a release does not become less true because a tracker
            is down.
          */}
          {resolution !== null && !resolution.reachedTracker && (
            <p className="hint">{resolution.failure}</p>
          )}
          {resolution !== null && resolution.reachedTracker && (
            <p className="field-hint">
              Read from {resolution.connectorId}. Titles below the identifiers are the tracker's; what
              Tower holds is what documents print.
            </p>
          )}
        </div>
      )}

      {!archived && (
        <form className="inline-form inline-form--compact" onSubmit={handleAdd}>
          <label className="field">
            <span>Work item</span>
            <input
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="e.g. PROJ-123"
              required
            />
            <span className="field-hint">As the tracker writes it.</span>
          </label>
          <label className="field">
            <span>Title</span>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="optional"
            />
            <span className="field-hint">Leave blank to link it without one.</span>
          </label>
          <button type="submit" disabled={addBusy || !identifier.trim()}>
            {addBusy ? "Linking…" : "Link work item"}
          </button>
        </form>
      )}
      {addError !== null && <ErrorNote error={addError} />}
    </div>
  );
}

// What the tracker currently says. Four states, rendered as four different
// things — NOT_FOUND and UNRESOLVED especially, since "the tracker does not
// have this" and "Tower could not ask" are different facts.
function TrackerLine({ item }: { item: ResolvedWorkItem }) {
  if (item.state === "UNRESOLVED") {
    return <div className="work-item__tracker hint">Not checked against a tracker.</div>;
  }

  if (item.state === "NOT_FOUND") {
    return (
      <div className="work-item__tracker">
        <span className="badge badge--muted">The tracker does not have this item</span>
      </div>
    );
  }

  return (
    <div className="work-item__tracker">
      {item.diverged && <span className="badge">Tracker says something different</span>}
      <span className="work-item__tracker-title">{item.trackerTitle}</span>
      {item.status && (
        // The tracker's own word, not a Tower one: deciding which of a team's
        // states counts as finished is not Tower's judgement to make.
        <span className={item.closed ? "badge badge--muted" : "badge"}>{item.status}</span>
      )}
      {item.url && (
        <a className="work-item__link" href={item.url} target="_blank" rel="noreferrer">
          open in tracker
        </a>
      )}
    </div>
  );
}
