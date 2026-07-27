import { useState } from "react";
import {
  addReleasePackVersion,
  removeReleasePackVersion,
  type Application,
  type ApplicationVersion,
  type ReleasePackContent,
  type ReleasePackView,
} from "../api/client";
import ErrorNote, { describeError } from "./ErrorNote";

interface ReleasePackContentsProps {
  packId: string;
  contents: ReleasePackContent[];
  applications: Application[];
  versions: ApplicationVersion[];
  archived: boolean;
  onUpdated: (pack: ReleasePackView) => void;
}

// A Release Pack holds at most one Application Version per Application
// (ADR-004/domain rule). Swapping is remove-then-add: the "add" selector
// only offers Applications not already present, so replacing a version
// means removing the old row first — there is no separate "swap" control,
// which keeps the UI honest about what's actually happening server-side.
export default function ReleasePackContents({
  packId,
  contents,
  applications,
  versions,
  archived,
  onUpdated,
}: ReleasePackContentsProps) {
  const [selectedApp, setSelectedApp] = useState("");
  const [selectedVersion, setSelectedVersion] = useState("");
  const [addBusy, setAddBusy] = useState(false);
  const [addError, setAddError] = useState<unknown>(null);

  const [removeBusyId, setRemoveBusyId] = useState<string | null>(null);
  const [removeErrors, setRemoveErrors] = useState<Record<string, unknown>>({});

  const containedApplicationIds = new Set(contents.map((c) => c.applicationId));
  const availableApplications = applications.filter((a) => !containedApplicationIds.has(a.id));
  const availableVersions = versions.filter((v) => v.applicationId === selectedApp);

  function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedVersion) return;
    setAddBusy(true);
    setAddError(null);
    addReleasePackVersion(packId, selectedVersion)
      .then((updated) => {
        onUpdated(updated);
        setSelectedApp("");
        setSelectedVersion("");
      })
      .catch((err: unknown) => setAddError(err))
      .finally(() => setAddBusy(false));
  }

  function handleRemove(content: ReleasePackContent) {
    setRemoveBusyId(content.versionId);
    setRemoveErrors((prev) => {
      const { [content.versionId]: _drop, ...rest } = prev;
      return rest;
    });
    removeReleasePackVersion(packId, content.versionId)
      .then(onUpdated)
      .catch((err: unknown) => setRemoveErrors((prev) => ({ ...prev, [content.versionId]: err })))
      .finally(() => setRemoveBusyId(null));
  }

  return (
    <div className="pack-contents">
      {contents.length === 0 && <p className="hint">This Release Pack has no Application Versions yet.</p>}

      {contents.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Application</th>
              <th>Version</th>
              <th>Details</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {contents.map((c) => (
              <tr key={c.versionId}>
                <td>{c.applicationName}</td>
                <td>{c.version}</td>
                <td className="pack-contents__attrs">
                  {c.branch && <span className="version-item__attr">branch {c.branch}</span>}
                  {c.tag && <span className="version-item__attr">tag {c.tag}</span>}
                  {c.commit && <span className="version-item__attr">commit {c.commit}</span>}
                  {c.buildIdentifier && <span className="version-item__attr">build {c.buildIdentifier}</span>}
                  {!c.branch && !c.tag && !c.commit && !c.buildIdentifier && <span className="hint">—</span>}
                </td>
                <td className="data-table__actions">
                  {!archived && (
                    <button
                      type="button"
                      className="danger"
                      onClick={() => handleRemove(c)}
                      disabled={removeBusyId === c.versionId}
                    >
                      {removeBusyId === c.versionId ? "Removing…" : "Remove"}
                    </button>
                  )}
                  {removeErrors[c.versionId] !== undefined && (
                    <div className="row-error">{describeError(removeErrors[c.versionId])}</div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {!archived && (
        <form className="inline-form inline-form--compact" onSubmit={handleAdd}>
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
              {availableApplications.map((a) => (
                <option value={a.id} key={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Version</span>
            <select value={selectedVersion} onChange={(e) => setSelectedVersion(e.target.value)} disabled={!selectedApp}>
              <option value="">Select a Version…</option>
              {availableVersions.map((v) => (
                <option value={v.id} key={v.id}>
                  {v.version}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={addBusy || !selectedVersion}>
            {addBusy ? "Adding…" : "Add to Pack"}
          </button>
        </form>
      )}
      {addError !== null && <ErrorNote error={addError} />}
    </div>
  );
}
