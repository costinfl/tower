import { useEffect, useState } from "react";
import { getReleasePackState, type ReleasePackStateView, type ReleasePackView } from "../api/client";
import { releasePackStateMeta } from "../domain/releasePackState";
import ErrorNote from "./ErrorNote";

interface ReleasePackStatePanelProps {
  pack: ReleasePackView;
}

// Surfaces the derived Release Pack state (ADR-008): the highest Stage at
// which any content version has been observed, plus the sightings that
// produced it. Rendered as two independent indicators — the derived
// state and the `archived` lifecycle flag — never combined into one,
// because a pack can be archived and have been observed in Production at
// the same time; both facts are true and both are shown.
export default function ReleasePackStatePanel({ pack }: ReleasePackStatePanelProps) {
  const [state, setState] = useState<ReleasePackStateView | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  useEffect(() => {
    setState(null);
    setLoadError(null);
    getReleasePackState(pack.id)
      .then(setState)
      .catch((err: unknown) => setLoadError(err));
  }, [pack.id]);

  return (
    <div className="pack-state">
      {loadError !== null && <ErrorNote error={loadError} />}
      {state === null && loadError === null && <p className="hint">Loading state…</p>}

      {state !== null && (
        <>
          <div className="pack-state__indicators">
            <span className={`pack-state-chip pack-state--${releasePackStateMeta(state.state).className}`}>
              <span aria-hidden="true">{releasePackStateMeta(state.state).glyph}</span>{" "}
              {releasePackStateMeta(state.state).name}
            </span>
            {/* Lifecycle flag (ADR-008) — a human decision, independent of the derived state above. */}
            <span className={pack.archived ? "badge badge--muted" : "badge"}>
              {pack.archived ? "Archived" : "Not archived"}
            </span>
          </div>

          {state.sightings.length === 0 && (
            <p className="hint">This Release Pack has not been observed in any Environment yet.</p>
          )}

          {state.sightings.length > 0 && (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Environment</th>
                  <th>Application</th>
                  <th>Version</th>
                  <th>Observed</th>
                </tr>
              </thead>
              <tbody>
                {state.sightings
                  .slice()
                  .sort((a, b) => b.observedAt.localeCompare(a.observedAt))
                  .map((s, i) => (
                    <tr key={`${s.environment.id}-${s.applicationVersion.id}-${i}`}>
                      <td>{s.environment.name}</td>
                      <td>{s.application.name}</td>
                      <td>{s.applicationVersion.version}</td>
                      <td>{new Date(s.observedAt).toLocaleString()}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
}
