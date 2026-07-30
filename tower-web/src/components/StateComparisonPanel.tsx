import { useState } from "react";
import {
  getEnvironmentStateComparison,
  type DifferenceKind,
  type Environment,
  type StateComparisonView,
} from "../api/client";
import { toInstant } from "../domain/instant";
import ErrorNote from "./ErrorNote";

interface StateComparisonPanelProps {
  environment: Environment;
  environments: Environment[];
  /** The instant the state panel is currently showing, ISO, or "" for now. */
  at: string;
}

// Compares one Environment's derived state against another Environment, or
// against itself at an earlier instant (Milestone 4, ADR-017).
//
// Both sides are folded from the Observation stream on request; nothing is
// stored, so any instant can be compared and not only ones somebody
// captured.
//
// The three kinds are kept apart deliberately. CHANGED means Tower saw one
// version on one side and a different one on the other. ARRIVED and GONE
// mean it has an Observation on one side and none on the other — GONE in
// particular is NOT a claim that anything was undeployed. Collapsing these
// into "added/removed" would turn "nobody looked" into "it is not there",
// which is the confusion Scenario 4 exists to prevent.
export default function StateComparisonPanel({ environment, environments, at }: StateComparisonPanelProps) {
  const [against, setAgainst] = useState("");
  const [againstAt, setAgainstAt] = useState("");
  const [comparison, setComparison] = useState<StateComparisonView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  // What the shown comparison was actually asked for, captured at request
  // time so the caption cannot drift from the result while the inputs are
  // edited afterwards.
  const [asked, setAsked] = useState<{ left: string; right: string } | null>(null);

  const others = environments.filter((e) => e.id !== environment.id);

  function describe(environmentName: string, instant: string) {
    return instant ? `${environmentName} on ${new Date(instant).toLocaleString()}` : `${environmentName} now`;
  }

  function compare() {
    setBusy(true);
    setError(null);
    const rightName = against ? (others.find((e) => e.id === against)?.name ?? "the other Environment")
      : environment.name;
    // The picker reads wall-clock; the API takes instants.
    const rightInstant = toInstant(againstAt);
    getEnvironmentStateComparison(environment.id, {
      at: at || undefined,
      against: against || undefined,
      againstAt: rightInstant,
    })
      .then((result) => {
        setComparison(result);
        setAsked({
          left: describe(environment.name, at),
          right: describe(rightName, rightInstant ?? ""),
        });
      })
      .catch((err: unknown) => {
        setError(err);
        setComparison(null);
        setAsked(null);
      })
      .finally(() => setBusy(false));
  }

  return (
    <div className="state-comparison">
      <p className="field-hint">
        Compare what is shown above with this Environment at another time, or with a different Environment.
        Both sides are derived from Observations when you ask — nothing was snapshotted in advance.
      </p>

      <div className="inline-form">
        <label className="field">
          <span>Against</span>
          <select value={against} onChange={(e) => setAgainst(e.target.value)}>
            <option value="">{environment.name} (itself)</option>
            {others.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>As it stood</span>
          <input
            type="datetime-local"
            value={againstAt}
            onChange={(e) => setAgainstAt(e.target.value)}
            aria-label="The instant to compare against"
          />
          <span className="field-hint">Leave blank for now.</span>
        </label>
        <button type="button" onClick={compare} disabled={busy}>
          {busy ? "Comparing…" : "Compare"}
        </button>
      </div>

      {error !== null && <ErrorNote error={error} />}

      {comparison !== null && asked !== null && (
        <>
          <p className="state-comparison__caption">
            <strong>{asked.left}</strong> compared with <strong>{asked.right}</strong>
          </p>

          {/*
            Stated rather than left to be read off an empty table: "they
            agree" and "the comparison found nothing to say" look identical
            otherwise.
          */}
          {comparison.identical && (
            <p className="hint">
              These two agree. Every Application observed on one side is observed at the same version on the
              other.
            </p>
          )}

          {comparison.differences.length > 0 && (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Application</th>
                  <th>What changed</th>
                  <th>{asked.left}</th>
                  <th>{asked.right}</th>
                  <th>In Release Pack</th>
                </tr>
              </thead>
              <tbody>
                {comparison.differences.map((d) => (
                  <tr key={d.applicationId}>
                    <td>{d.applicationName ?? "(deleted Application)"}</td>
                    <td>
                      <span className="badge">{kindLabel(d.kind)}</span>
                    </td>
                    <td>{d.leftVersion ?? <span className="hint">not observed</span>}</td>
                    <td>{d.rightVersion ?? <span className="hint">not observed</span>}</td>
                    {/*
                      "In", not "introduced by": the pack contains the version,
                      which is not the same as having put it there. Two packs
                      may hold it and neither deployed it.
                    */}
                    <td>
                      {d.releasePacks.length === 0 ? (
                        <span className="hint">no Release Pack</span>
                      ) : (
                        d.releasePacks.join(", ")
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {comparison.unchanged.length > 0 && (
            <details className="state-comparison__unchanged">
              <summary>{comparison.unchanged.length} unchanged</summary>
              <ul className="observation-list">
                {comparison.unchanged.map((u) => (
                  <li className="observation-item" key={u.applicationId}>
                    <span className="observation-item__app">{u.applicationName ?? "(deleted Application)"}</span>{" "}
                    <span className="observation-item__version">{u.version}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}

          <p className="field-hint">
            “Not observed” means Tower has been told nothing about that Application on that side. It is not a
            statement that the Application is absent.
          </p>
        </>
      )}
    </div>
  );
}

// Worded as what Tower knows, not as what happened to the Environment.
function kindLabel(kind: DifferenceKind): string {
  switch (kind) {
    case "CHANGED":
      return "Different version";
    case "ARRIVED":
      return "Only observed on the right";
    default:
      return "Only observed on the left";
  }
}
