import { useEffect, useState } from "react";
import {
  getEnvironmentObservations,
  getEnvironmentState,
  type Application,
  type ApplicationVersion,
  type Environment,
  type EnvironmentStateView,
  type ObservationView,
} from "../api/client";
import ErrorNote from "./ErrorNote";
import ObservationSourceTag from "./ObservationSourceTag";
import RecordObservationForm from "./RecordObservationForm";

interface EnvironmentStatePanelProps {
  environment: Environment;
  applications: Application[];
  versions: ApplicationVersion[];
}

// Shows an Environment's current derived state, lets a developer record a
// new Observation, and shows the full Observation history underneath.
//
// Environment state is derived, never stored (ADR-002): for each
// Application the most recent Observation wins. When `hasBeenObserved` is
// false, that is stated plainly — "Tower has not been told anything about
// this Environment" — never rendered as an empty-and-therefore-fine table
// (Scenarios.md Scenario 4): an unobserved Environment and an Environment
// observed to currently hold nothing are different facts.
export default function EnvironmentStatePanel({ environment, applications, versions }: EnvironmentStatePanelProps) {
  const [state, setState] = useState<EnvironmentStateView | null>(null);
  const [history, setHistory] = useState<ObservationView[] | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  function load() {
    setLoadError(null);
    Promise.all([getEnvironmentState(environment.id), getEnvironmentObservations(environment.id)])
      .then(([s, h]) => {
        setState(s);
        setHistory(h);
      })
      .catch((err: unknown) => setLoadError(err));
  }

  useEffect(() => {
    setState(null);
    setHistory(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [environment.id]);

  // The /state endpoint is the authority on which Observation is current
  // for each Application (most-recent-wins). History is append-only and
  // newest-first: an entry not present in state.deployed is superseded,
  // but it is still shown here, marked rather than hidden or struck
  // through, so the record stays visibly append-only.
  const currentObservationIds = new Set(state?.deployed.map((d) => d.observationId) ?? []);

  return (
    <div className="env-state">
      {loadError !== null && <ErrorNote error={loadError} />}
      {state === null && loadError === null && <p className="hint">Loading Environment state…</p>}

      {state !== null && (
        <>
          {state.hasBeenObserved ? (
            <p className="env-state__last-observed">
              Last observed{" "}
              <strong>{state.lastObservedAt ? new Date(state.lastObservedAt).toLocaleString() : "—"}</strong>
            </p>
          ) : (
            <p className="env-state__never-observed">
              Tower has not been told anything about this Environment. No Observation has ever been recorded for it.
            </p>
          )}

          {state.hasBeenObserved && (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Application</th>
                  <th>Version</th>
                  <th>Details</th>
                  <th>Observed</th>
                  <th>Source</th>
                </tr>
              </thead>
              <tbody>
                {state.deployed.length === 0 && (
                  <tr>
                    <td colSpan={5} className="hint">
                      Tower has observed this Environment, but no Application is currently deployed there.
                    </td>
                  </tr>
                )}
                {state.deployed.map((d) => (
                  <tr key={d.observationId}>
                    <td>{d.application.name}</td>
                    <td>{d.applicationVersion.version}</td>
                    <td className="pack-contents__attrs">
                      {d.applicationVersion.branch && (
                        <span className="version-item__attr">branch {d.applicationVersion.branch}</span>
                      )}
                      {d.applicationVersion.tag && (
                        <span className="version-item__attr">tag {d.applicationVersion.tag}</span>
                      )}
                      {d.applicationVersion.commit && (
                        <span className="version-item__attr">commit {d.applicationVersion.commit}</span>
                      )}
                      {d.applicationVersion.buildIdentifier && (
                        <span className="version-item__attr">build {d.applicationVersion.buildIdentifier}</span>
                      )}
                      {!d.applicationVersion.branch &&
                        !d.applicationVersion.tag &&
                        !d.applicationVersion.commit &&
                        !d.applicationVersion.buildIdentifier && <span className="hint">—</span>}
                    </td>
                    <td>{new Date(d.observedAt).toLocaleString()}</td>
                    <td>
                      <ObservationSourceTag source={d.source} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}

      <section className="pack-section">
        <h4>Record what is deployed</h4>
        <p className="field-hint">
          State that a version was already seen running here. This records a fact — it does not deploy or change
          anything.
        </p>
        <RecordObservationForm
          environmentId={environment.id}
          applications={applications}
          versions={versions}
          onRecorded={load}
        />
      </section>

      <section className="pack-section">
        <h4>Observation history</h4>
        {history === null && loadError === null && <p className="hint">Loading history…</p>}
        {history !== null && history.length === 0 && (
          <p className="hint">No Observations recorded for this Environment yet.</p>
        )}
        {history !== null && history.length > 0 && (
          <ul className="observation-list">
            {history.map((obs) => {
              const superseded = !currentObservationIds.has(obs.id);
              return (
                <li
                  className={superseded ? "observation-item observation-item--superseded" : "observation-item"}
                  key={obs.id}
                >
                  <div className="observation-item__header">
                    <span className="observation-item__app">{obs.application.name}</span>
                    <span className="observation-item__version">{obs.applicationVersion.version}</span>
                    {superseded && <span className="badge badge--muted">Superseded</span>}
                  </div>
                  <div className="observation-item__meta">
                    <span>{new Date(obs.observedAt).toLocaleString()}</span>
                    <ObservationSourceTag source={obs.source} />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
