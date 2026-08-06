import { useEffect, useState } from "react";
import {
  getDashboard,
  type ConvergingStanding,
  type DashboardView,
  type SetupStepView,
} from "../api/client";
import type { Tab } from "../App";
import ErrorNote from "../components/ErrorNote";
import { releasePackStateMeta } from "../domain/releasePackState";
import { stageMeta } from "../domain/stage";

// One operational view across every active release (Milestone 5, issue #7).
//
// The question this page exists for is not "where is this release" — the
// Release Pack page answers that — but "which releases are competing for UAT
// right now", asked across every release at once, so the business team can
// decide which one proceeds.
//
// Tower shows them the situation. It does not rank the releases, recommend
// one, or imply an order (ADR-001; Guardrails.md lists "Visualizes" and does
// not list "Decides"). The server orders converging releases BY NAME for that
// reason, and this page renders them in the order it receives them — sorting
// by progress here would quietly reintroduce the ranking the server refused
// to make.
//
// Nothing on this page writes. There is deliberately no promote, deploy or
// approve control: this is the screen where such a button would feel most
// natural and would do the most damage to what Tower is.
export default function DashboardPage({ onNavigate }: { onNavigate: (tab: Tab) => void }) {
  const [dashboard, setDashboard] = useState<DashboardView | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  useEffect(() => {
    getDashboard()
      .then(setDashboard)
      .catch((err: unknown) => setLoadError(err));
  }, []);

  return (
    <section className="page">
      <h2>Dashboard</h2>
      <p className="page__intro">
        Everything in flight, in one place. Every figure here is derived from Observations when you ask for
        it — nothing is stored, and nothing on this page changes anything.
      </p>

      {loadError !== null && <ErrorNote error={loadError} />}
      {dashboard === null && loadError === null && <p className="hint">Loading the dashboard…</p>}

      {dashboard !== null && (
        <>
          {/*
            Only while there is something left to do. A permanent checklist on a
            working Tower is clutter, and `complete` ignores the optional step so
            a team that records Observations by hand is not told it is unfinished.
          */}
          {!dashboard.setup.complete && (
            <SetupPath steps={dashboard.setup.steps} onNavigate={onNavigate} />
          )}

          <div className="summary-tiles">
            <SummaryTile label="Active releases" value={dashboard.summary.activeReleasePacks} />
            <SummaryTile
              label="Environments two or more releases are heading for"
              value={dashboard.summary.contestedEnvironments}
              emphasis={dashboard.summary.contestedEnvironments > 0}
            />
            <SummaryTile
              label="Releases nothing has been reported about"
              value={dashboard.summary.packsNotObservedAnywhere}
            />
            <SummaryTile
              label="Environments Tower has been told nothing about"
              value={dashboard.summary.environmentsNeverObserved}
            />
            <SummaryTile label="Archived releases" value={dashboard.summary.archivedReleasePacks} muted />
          </div>
          {/*
            Two of these five count silence. Saying so once here is cheaper than
            letting a reader take a zero for reassurance.
          */}
          <p className="field-hint">
            The last two count what Tower has <em>not</em> been told. They are not counts of anything being
            wrong or missing — only of nobody having said.
          </p>

          <section className="pack-section">
            <h3>Environments</h3>
            <p className="field-hint">
              Which releases are heading for each Environment, and how much of each has been seen there. A
              release appears here because its own pinned Promotion Path names the Environment.
            </p>

            {dashboard.environments.length === 0 && (
              <p className="hint">No Environments have been defined yet.</p>
            )}

            <div className="dashboard-environments">
              {dashboard.environments.map((environment) => (
                <article
                  className={environment.contested ? "env-card env-card--contested" : "env-card"}
                  key={environment.environmentId}
                >
                  <header className="env-card__header">
                    <span className="env-card__name">{environment.name}</span>
                    <span className={`stage-chip stage--${stageMeta(environment.stage).className}`}>
                      <span aria-hidden="true">{stageMeta(environment.stage).glyph}</span>{" "}
                      {stageMeta(environment.stage).name}
                    </span>
                    {/*
                      Stated, not implied by the number of rows below: this is the
                      thing a reader is scanning the page for.
                    */}
                    {environment.contested && (
                      <span className="badge">{environment.converging.length} releases heading here</span>
                    )}
                  </header>

                  {environment.hasBeenObserved ? (
                    <p className="env-card__observed">
                      {environment.deployedCount} Application{environment.deployedCount === 1 ? "" : "s"}{" "}
                      currently observed · last observed{" "}
                      {environment.lastObservedAt
                        ? new Date(environment.lastObservedAt).toLocaleString()
                        : "—"}
                    </p>
                  ) : (
                    <p className="env-card__observed env-card__observed--never">
                      Tower has not been told anything about this Environment.
                    </p>
                  )}

                  {environment.converging.length === 0 ? (
                    <p className="hint">No active release is heading here.</p>
                  ) : (
                    <ul className="converging-list">
                      {environment.converging.map((pack) => (
                        <li className="converging" key={pack.releasePackId}>
                          <span className="converging__name">{pack.name}</span>
                          <span className={`badge standing--${pack.standing.toLowerCase()}`}>
                            {standingLabel(pack.standing)}
                          </span>
                          <span className="converging__counts">
                            {pack.observedCount} of {pack.packedCount} seen here
                          </span>
                          {pack.firstObservedAt && (
                            <span className="converging__when">
                              first seen {new Date(pack.firstObservedAt).toLocaleString()}
                            </span>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </article>
              ))}
            </div>
            <p className="field-hint">
              Releases are listed by name. That is not a running order — Tower does not rank releases or
              suggest which should proceed.
            </p>
          </section>

          <section className="pack-section">
            <h3>Active releases</h3>
            {dashboard.releasePacks.length === 0 && (
              <p className="hint">No active Release Packs. Archived ones are not shown here.</p>
            )}
            {dashboard.releasePacks.length > 0 && (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Release Pack</th>
                    <th>Observed state</th>
                    <th>Promotion Path</th>
                    <th>Contents</th>
                    <th>Seen in</th>
                    <th>Furthest</th>
                    <th>Last seen</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.releasePacks.map((pack) => (
                    <tr key={pack.releasePackId}>
                      <td>{pack.name}</td>
                      <td>
                        <span className={`pack-state-chip pack-state--${releasePackStateMeta(pack.state).className}`}>
                          <span aria-hidden="true">{releasePackStateMeta(pack.state).glyph}</span>{" "}
                          {releasePackStateMeta(pack.state).name}
                        </span>
                      </td>
                      <td>{pack.promotionPath ?? <span className="hint">none assigned</span>}</td>
                      <td>{pack.contentCount}</td>
                      <td>
                        {pack.environmentsReached === 0 ? (
                          <span className="hint">nowhere yet</span>
                        ) : (
                          `${pack.environmentsReached} Environment${pack.environmentsReached === 1 ? "" : "s"}`
                        )}
                      </td>
                      <td>
                        {pack.furthestEnvironments.length === 0 ? (
                          <span className="hint">—</span>
                        ) : (
                          /* All of them: a release equally far along in two
                             Environments of the same Stage is in both. */
                          pack.furthestEnvironments.join(", ")
                        )}
                      </td>
                      <td>
                        {pack.lastObservedAt ? (
                          new Date(pack.lastObservedAt).toLocaleString()
                        ) : (
                          <span className="hint">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="field-hint">
              “Seen in” counts the Environments any of a release's contents has been observed in. A release
              seen nowhere has not necessarily gone nowhere — it may be that nobody has said.
            </p>
          </section>
        </>
      )}
    </section>
  );
}

// The order things have to be defined in, shown against what exists.
//
// Every step stays visible, done ones included, because the sequence is the
// point — a shrinking list of leftovers teaches nothing about why Environments
// come before a Promotion Path. The server decides what is done and what is
// optional; this renders that and nothing more.
function SetupPath({
  steps,
  onNavigate,
}: {
  steps: SetupStepView[];
  onNavigate: (tab: Tab) => void;
}) {
  return (
    <section className="setup-path">
      <h3 className="setup-path__title">Setting Tower up</h3>
      <p className="field-hint">
        This order is not a preference — a Promotion Path is a sequence of Environments, and a Release Pack
        holds Application Versions, so each step needs the one above it. It disappears once the required
        steps are done.
      </p>
      <ol className="setup-path__steps">
        {steps.map((step, index) => (
          <li
            className={step.done ? "setup-step setup-step--done" : "setup-step"}
            key={step.id}
          >
            <span className="setup-step__mark" aria-hidden="true">
              {step.done ? "✓" : index + 1}
            </span>
            <div className="setup-step__body">
              <button
                type="button"
                className="link-button setup-step__title"
                onClick={() => onNavigate(step.id as Tab)}
              >
                {step.title}
              </button>
              {step.optional && <span className="badge">optional</span>}
              <p className="setup-step__detail">{step.detail}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

// Worded as what Tower knows, matching the API's own naming. "Not reported
// here" must never shorten to "not deployed".
function standingLabel(standing: ConvergingStanding): string {
  switch (standing) {
    case "FULLY_OBSERVED":
      return "All of it seen here";
    case "PARTLY_OBSERVED":
      return "Partly seen here";
    default:
      return "Not reported here";
  }
}

function SummaryTile({
  label,
  value,
  emphasis = false,
  muted = false,
}: {
  label: string;
  value: number;
  emphasis?: boolean;
  muted?: boolean;
}) {
  const classes = ["summary-tile"];
  if (emphasis) classes.push("summary-tile--emphasis");
  if (muted) classes.push("summary-tile--muted");
  return (
    <div className={classes.join(" ")}>
      <span className="summary-tile__value">{value}</span>
      <span className="summary-tile__label">{label}</span>
    </div>
  );
}
