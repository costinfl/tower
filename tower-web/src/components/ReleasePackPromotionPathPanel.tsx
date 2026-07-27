import { useState } from "react";
import {
  clearReleasePackPromotionPath,
  setReleasePackPromotionPath,
  type PathView,
  type ReleasePackPromotionPath,
  type ReleasePackView,
} from "../api/client";
import ErrorNote from "./ErrorNote";
import Lane from "./Lane";

interface ReleasePackPromotionPathPanelProps {
  packId: string;
  promotionPath: ReleasePackPromotionPath | null;
  paths: PathView[];
  archived: boolean;
  onUpdated: (pack: ReleasePackView) => void;
}

// A Release Pack pins a specific Promotion Path VERSION (ADR-007), so this
// panel always shows both the path name and the pinned version number —
// the pack may follow v1 while the path itself has since moved to v3, and
// that is correct, not stale. The Lane component is reused as-is to render
// the pinned topology; only Promotion Paths not archived may be newly
// assigned (an archived path stays readable but is not offered here).
export default function ReleasePackPromotionPathPanel({
  packId,
  promotionPath,
  paths,
  archived,
  onUpdated,
}: ReleasePackPromotionPathPanelProps) {
  const [editing, setEditing] = useState(false);
  const [selectedPathId, setSelectedPathId] = useState("");
  const [selectedVersion, setSelectedVersion] = useState<number | "">("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [hoveredEnvironmentId, setHoveredEnvironmentId] = useState<string | null>(null);

  const assignablePaths = paths.filter((p) => !p.archived);
  const selectedPath = assignablePaths.find((p) => p.id === selectedPathId) ?? null;

  function startEdit() {
    setSelectedPathId(promotionPath?.pathId ?? "");
    setSelectedVersion(promotionPath?.versionNumber ?? "");
    setError(null);
    setEditing(true);
  }

  function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedPathId || selectedVersion === "") return;
    setBusy(true);
    setError(null);
    setReleasePackPromotionPath(packId, { pathId: selectedPathId, versionNumber: selectedVersion })
      .then((updated) => {
        onUpdated(updated);
        setEditing(false);
      })
      .catch((err: unknown) => setError(err))
      .finally(() => setBusy(false));
  }

  function handleClear() {
    setBusy(true);
    setError(null);
    clearReleasePackPromotionPath(packId)
      .then((updated) => {
        onUpdated(updated);
        setEditing(false);
      })
      .catch((err: unknown) => setError(err))
      .finally(() => setBusy(false));
  }

  if (editing) {
    return (
      <form className="promotion-path-picker" onSubmit={handleSave}>
        <label className="field">
          <span>Promotion Path</span>
          <select
            value={selectedPathId}
            onChange={(e) => {
              setSelectedPathId(e.target.value);
              const path = assignablePaths.find((p) => p.id === e.target.value);
              setSelectedVersion(path?.currentVersion.number ?? "");
            }}
          >
            <option value="">Select a Promotion Path…</option>
            {assignablePaths.map((p) => (
              <option value={p.id} key={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        {selectedPath && (
          <label className="field">
            <span>Version</span>
            <select value={selectedVersion} onChange={(e) => setSelectedVersion(Number(e.target.value))}>
              {selectedPath.versions
                .slice()
                .sort((a, b) => b.number - a.number)
                .map((v) => (
                  <option value={v.number} key={v.number}>
                    v{v.number}
                    {v.number === selectedPath.currentVersion.number ? " (current)" : ""}
                  </option>
                ))}
            </select>
          </label>
        )}
        {error !== null && <ErrorNote error={error} />}
        <div className="promotion-path-picker__actions">
          <button type="submit" disabled={busy || !selectedPathId || selectedVersion === ""}>
            {busy ? "Saving…" : "Save"}
          </button>
          <button type="button" onClick={() => setEditing(false)} disabled={busy}>
            Cancel
          </button>
        </div>
      </form>
    );
  }

  if (!promotionPath) {
    return (
      <div className="promotion-path-panel">
        <p className="hint">No Promotion Path assigned yet.</p>
        {error !== null && <ErrorNote error={error} />}
        {!archived && (
          <button type="button" onClick={startEdit}>
            Assign Promotion Path
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="promotion-path-panel">
      <div className="promotion-path-panel__header">
        <span className="promotion-path-panel__name">{promotionPath.pathName}</span>
        <span className="badge">v{promotionPath.versionNumber}</span>
      </div>
      <Lane
        version={{ number: promotionPath.versionNumber, createdAt: "", environments: promotionPath.environments }}
        sharedEnvironmentIds={new Set()}
        hoveredEnvironmentId={hoveredEnvironmentId}
        onHoverEnvironment={setHoveredEnvironmentId}
      />
      {error !== null && <ErrorNote error={error} />}
      {!archived && (
        <div className="promotion-path-panel__actions">
          <button type="button" onClick={startEdit} disabled={busy}>
            Change
          </button>
          <button type="button" className="danger" onClick={handleClear} disabled={busy}>
            {busy ? "Removing…" : "Remove"}
          </button>
        </div>
      )}
    </div>
  );
}
