import { useEffect, useState } from "react";
import { getReleasePackProgression, type ReleasePackProgressionView } from "../api/client";
import { stageMeta } from "../domain/stage";
import ErrorNote from "./ErrorNote";

interface ReleasePackProgressionPanelProps {
  packId: string;
  contentCount: number;
}

// When this release reached each Environment, derived from the Observation
// stream and stored nowhere (ADR-017).
//
// Two instants are shown rather than one, because a release arrives
// piecemeal: "first seen" is when the first of its versions turned up and
// "all here" is when the last one did, and those can be days apart. A single
// "arrived at" column would have to pick one and would mislead either way.
//
// Environments the release has never been observed in do not appear. That
// absence is not evidence the release is missing from them — it may mean
// nobody looked (Scenarios.md Scenario 4) — so this panel never renders a
// row for one, and never implies a release is "not there yet".
export default function ReleasePackProgressionPanel({ packId, contentCount }: ReleasePackProgressionPanelProps) {
  const [progression, setProgression] = useState<ReleasePackProgressionView | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  useEffect(() => {
    setProgression(null);
    setLoadError(null);
    getReleasePackProgression(packId)
      .then(setProgression)
      .catch((err: unknown) => setLoadError(err));
  }, [packId]);

  return (
    <div className="pack-progression">
      {loadError !== null && <ErrorNote error={loadError} />}
      {progression === null && loadError === null && <p className="hint">Loading progression…</p>}

      {progression !== null && contentCount === 0 && (
        <p className="hint">
          This Release Pack contains no versions yet, so there is nothing that could have arrived anywhere.
        </p>
      )}

      {progression !== null && contentCount > 0 && !progression.observed && (
        <p className="hint">
          None of this release has been observed in any Environment yet. That means Tower has not been told
          about it — not that it is absent.
        </p>
      )}

      {progression !== null && progression.observed && (
        <>
          <table className="data-table">
            <thead>
              <tr>
                <th>Environment</th>
                <th>First seen</th>
                <th>All here</th>
                <th>Of the release</th>
              </tr>
            </thead>
            <tbody>
              {progression.arrivals.map((arrival) => (
                <tr key={arrival.environmentId}>
                  <td>
                    {arrival.environmentName ?? "(deleted Environment)"}
                    {/* Colour is never load-bearing: glyph and label carry the Stage too. */}
                    {arrival.stage && (
                      <span className={`stage-chip stage--${stageMeta(arrival.stage).className}`}>
                        <span aria-hidden="true">{stageMeta(arrival.stage).glyph}</span>{" "}
                        {stageMeta(arrival.stage).name}
                      </span>
                    )}
                  </td>
                  <td>{new Date(arrival.firstObservedAt).toLocaleString()}</td>
                  <td>
                    {/*
                      Empty is not a blank cell here: "still arriving" is a
                      fact worth naming, and an em dash would read as missing
                      data rather than as an incomplete release.
                    */}
                    {arrival.completeAt ? (
                      new Date(arrival.completeAt).toLocaleString()
                    ) : (
                      <span className="badge badge--muted">Still arriving</span>
                    )}
                  </td>
                  <td>
                    {arrival.observedCount} of {arrival.packedCount}
                    {arrival.missing.length > 0 && (
                      <ul className="pack-progression__missing">
                        {arrival.missing.map((m) => (
                          <li key={m.applicationVersionId}>
                            waiting on {m.applicationName ?? "(deleted Application)"} {m.version ?? ""}
                          </li>
                        ))}
                      </ul>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="field-hint">
            Ordered by when the release first reached each Environment. Only Environments it has been observed
            in appear — an Environment missing from this list has told Tower nothing, which is different from
            having told it the release is absent.
          </p>
        </>
      )}
    </div>
  );
}
