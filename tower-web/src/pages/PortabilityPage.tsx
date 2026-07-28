import { useEffect, useState } from "react";
import {
  applyImport,
  getTowerInstance,
  previewImport,
  towerExportDownloadUrl,
  type ConflictStrategy,
  type ImportReport,
  type TowerExportFile,
} from "../api/client";
import { importOutcomeMeta } from "../domain/importOutcome";
import ErrorNote from "../components/ErrorNote";

const STRATEGIES: { value: ConflictStrategy; label: string; explanation: string }[] = [
  { value: "SKIP", label: "Skip", explanation: "Keep what is here. Incoming records that collide are discarded." },
  { value: "REPLACE", label: "Replace", explanation: "Take the incoming record, overwriting the local one." },
  {
    value: "DUPLICATE",
    label: "Duplicate",
    explanation: "Keep both, giving the incoming record a fresh identity and a distinguishable name.",
  },
];

// Export and import of Tower-owned information (ADR-010).
//
// Every developer runs their own instance (ADR-009), so this is how a team
// shares Release Packs, Promotion Paths and Handover information until a shared
// deployment exists.
//
// Import is always previewed first. ADR-010 forbids automatic merging, and the
// person running an import should see the consequences before accepting them
// rather than discovering them afterwards.
export default function PortabilityPage() {
  const [instance, setInstance] = useState<string | null>(null);
  const [includeObservations, setIncludeObservations] = useState(true);

  const [file, setFile] = useState<TowerExportFile | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [strategy, setStrategy] = useState<ConflictStrategy>("SKIP");
  const [report, setReport] = useState<ImportReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    getTowerInstance()
      .then((i) => setInstance(i.name))
      .catch((e: unknown) => setError(e));
  }, []);

  function chooseFile(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0];
    setReport(null);
    setError(null);
    setFile(null);
    setFileName(null);
    if (!selected) return;

    selected
      .text()
      .then((text) => {
        const parsed = JSON.parse(text) as TowerExportFile;
        if (typeof parsed.schemaVersion !== "number" || !Array.isArray(parsed.releasePacks)) {
          throw new Error("This does not look like a Tower export file.");
        }
        setFile(parsed);
        setFileName(selected.name);
      })
      .catch((err: unknown) => setError(err));
  }

  function run(apply: boolean) {
    if (file === null) return;
    setBusy(true);
    setError(null);
    const call = apply ? applyImport : previewImport;
    call(file, strategy)
      .then(setReport)
      .catch((e: unknown) => setError(e))
      .finally(() => setBusy(false));
  }

  const selectedStrategy = STRATEGIES.find((s) => s.value === strategy);

  return (
    <section className="page">
      <h2>Export &amp; Import</h2>
      <p className="page__intro">
        Tower runs on your machine, so Release Packs, Promotion Paths and Handover information live
        here and nowhere else. Export shares them with a colleague; import brings theirs in.
        {instance !== null && (
          <>
            {" "}
            This instance is <strong>{instance}</strong>.
          </>
        )}
      </p>

      {error !== null && <ErrorNote error={error} />}

      <section className="pack-section">
        <h3>Export</h3>
        <p className="hint">
          Contains Release Packs, Promotion Paths, Handover information and validation Iterations,
          along with the Environments, Applications and versions they reference so the file can
          actually be imported. It never contains credentials or configuration.
        </p>
        <label className="checkbox-field">
          <input
            type="checkbox"
            checked={includeObservations}
            onChange={(e) => setIncludeObservations(e.target.checked)}
          />
          <span>
            Include Observations — where these releases have been seen. Leave this off to share the
            plan without the sightings.
          </span>
        </label>
        <div className="inline-form">
          <a className="button-link" href={towerExportDownloadUrl(includeObservations)} download>
            Download export
          </a>
        </div>
      </section>

      <section className="pack-section">
        <h3>Import</h3>
        <p className="hint">
          Nothing is written until you choose to apply. Observations keep naming the instance that
          actually observed them, so importing a colleague&rsquo;s file never makes it look as though
          you saw something yourself.
        </p>

        <div className="inline-form">
          <label className="field">
            <span>Export file</span>
            <input type="file" accept="application/json,.json" onChange={chooseFile} />
          </label>
        </div>

        {file !== null && (
          <p className="hint">
            <code>{fileName}</code> — schema version {file.schemaVersion}, exported from{" "}
            <strong>{file.exportedFrom}</strong>. Contains {file.releasePacks.length} Release Pack(s),{" "}
            {file.promotionPaths.length} Promotion Path(s), {file.applicationVersions.length} version(s)
            and {file.observations.length} Observation(s).
          </p>
        )}

        <fieldset className="strategy-choice" disabled={file === null}>
          <legend>When a record already exists here</legend>
          {STRATEGIES.map((option) => (
            <label key={option.value} className="radio-field">
              <input
                type="radio"
                name="strategy"
                value={option.value}
                checked={strategy === option.value}
                onChange={() => {
                  setStrategy(option.value);
                  setReport(null);
                }}
              />
              <span>
                <strong>{option.label}</strong> — {option.explanation}
              </span>
            </label>
          ))}
        </fieldset>

        <div className="inline-form">
          <button type="button" onClick={() => run(false)} disabled={file === null || busy}>
            {busy ? "Working…" : "Preview"}
          </button>
          <button
            type="button"
            onClick={() => run(true)}
            disabled={file === null || busy || report === null || report.applied}
            title={report === null ? "Preview first, so you can see what will happen" : undefined}
          >
            Apply import
          </button>
        </div>

        {report !== null && (
          <>
            <p className={report.applied ? "notice notice--applied" : "notice"}>
              {report.applied
                ? `Imported from ${report.sourceInstance}. ${report.entries.length} record(s) processed.`
                : `Preview only — nothing has been written. ${report.entries.length} record(s) would be processed`
                  + (selectedStrategy ? ` using “${selectedStrategy.label}”.` : ".")}
            </p>

            <table className="data-table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Record</th>
                  <th>Outcome</th>
                  <th>Detail</th>
                </tr>
              </thead>
              <tbody>
                {report.entries.map((entry) => {
                  const meta = importOutcomeMeta(entry.outcome);
                  return (
                    <tr key={`${entry.type}-${entry.id}`}>
                      <td>{entry.type}</td>
                      <td>{entry.name}</td>
                      <td>
                        <span className={`badge outcome--${meta.className}`} title={meta.meaning}>
                          <span aria-hidden="true">{meta.glyph}</span> {meta.label}
                        </span>
                      </td>
                      <td className="hint">{entry.detail ?? meta.meaning}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </>
        )}
      </section>
    </section>
  );
}
