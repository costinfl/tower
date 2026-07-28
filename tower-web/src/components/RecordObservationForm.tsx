import { useState } from "react";
import { createObservation, type Application, type ApplicationVersion, type ObservationView } from "../api/client";
import ErrorNote from "./ErrorNote";

interface RecordObservationFormProps {
  environmentId: string;
  applications: Application[];
  versions: ApplicationVersion[];
  onRecorded: (observation: ObservationView) => void;
}

// Records an Observation: a statement that a version was already seen
// running in this Environment (ADR-001 — Tower observes, it never acts).
// Wording throughout says "record", never "deploy" or "promote" — this
// form does not cause anything, it states something that already
// happened. observedAt is optional and defaults to now on the server; a
// future-dated value is rejected with 409, whose message is shown as-is.
export default function RecordObservationForm({
  environmentId,
  applications,
  versions,
  onRecorded,
}: RecordObservationFormProps) {
  const [selectedApp, setSelectedApp] = useState("");
  const [selectedVersion, setSelectedVersion] = useState("");
  const [observedAt, setObservedAt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const availableVersions = versions.filter((v) => v.applicationId === selectedApp);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedVersion) return;
    setBusy(true);
    setError(null);
    createObservation({
      environmentId,
      applicationVersionId: selectedVersion,
      observedAt: observedAt ? new Date(observedAt).toISOString() : undefined,
    })
      .then((observation) => {
        onRecorded(observation);
        setSelectedApp("");
        setSelectedVersion("");
        setObservedAt("");
      })
      .catch((err: unknown) => setError(err))
      .finally(() => setBusy(false));
  }

  return (
    <>
      <form className="inline-form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Application</span>
          <select
            value={selectedApp}
            onChange={(e) => {
              setSelectedApp(e.target.value);
              setSelectedVersion("");
            }}
          >
            <option value="">Select an Application…</option>
            {applications.map((a) => (
              <option value={a.id} key={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Version</span>
          <select
            value={selectedVersion}
            onChange={(e) => setSelectedVersion(e.target.value)}
            disabled={!selectedApp}
          >
            <option value="">Select a Version…</option>
            {availableVersions.map((v) => (
              <option value={v.id} key={v.id}>
                {v.version}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Observed at</span>
          <input type="datetime-local" value={observedAt} onChange={(e) => setObservedAt(e.target.value)} />
          <span className="field-hint">Leave blank to record it as just now.</span>
        </label>
        <button type="submit" disabled={busy || !selectedVersion}>
          {busy ? "Recording…" : "Record what is deployed"}
        </button>
      </form>
      {error !== null && <ErrorNote error={error} />}
    </>
  );
}
