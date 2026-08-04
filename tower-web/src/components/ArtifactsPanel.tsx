import { useEffect, useState } from "react";
import {
  acceptArtifactDigest,
  confirmArtifacts,
  withdrawArtifactDigest,
  type ArtifactConfirmation,
  type ConfirmedArtifact,
} from "../api/client";
import { describeError } from "./ErrorNote";

interface ArtifactsPanelProps {
  applicationVersionId: string;
  version: string;
}

// Whether the binaries this version names are where it says they should be
// (ADR-021).
//
// The same arrangement WorkItemsPanel uses, for the same reason. Two sources
// side by side: what the repository holds right now, read on request and stored
// nowhere, and the digest somebody accepted, which is Tower's own and is what a
// release document prints. Where they differ the difference is shown and never
// silently corrected — a handover already given to another team does not change
// because somebody re-published an image.
//
// Read on request rather than with the page. Confirming reaches a repository
// over the network, and doing that for every version on every visit would make
// the Applications page slow for an answer most visits do not need — the same
// judgement source control discovery makes a few lines above it.
export default function ArtifactsPanel({ applicationVersionId, version }: ArtifactsPanelProps) {
  const [confirmation, setConfirmation] = useState<ArtifactConfirmation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [rowBusy, setRowBusy] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<string, unknown>>({});

  // A confirmation belongs to the version it was read for, and nothing else.
  useEffect(() => {
    setConfirmation(null);
  }, [applicationVersionId]);

  function confirm() {
    setBusy(true);
    setError(null);
    confirmArtifacts(applicationVersionId)
      .then(setConfirmation)
      .catch((e: unknown) => setError(e))
      .finally(() => setBusy(false));
  }

  // Re-reads afterwards rather than patching the row in place. Accepting
  // changes what Tower holds, and what the screen should then show is the fresh
  // comparison between the two — not this one with a field overwritten.
  function run(kind: string, action: Promise<unknown>) {
    setRowBusy(kind);
    setRowErrors((prev) => {
      const { [kind]: _drop, ...rest } = prev;
      return rest;
    });
    action
      .then(() => confirmArtifacts(applicationVersionId))
      .then(setConfirmation)
      .catch((e: unknown) => setRowErrors((prev) => ({ ...prev, [kind]: e })))
      .finally(() => setRowBusy(null));
  }

  return (
    <div className="artifacts">
      <button type="button" onClick={confirm} disabled={busy}>
        {busy ? "Reading the repository…" : `Confirm artifacts for ${version}`}
      </button>

      {error !== null && <div className="row-error">{describeError(error)}</div>}

      {confirmation !== null && confirmation.artifacts.length === 0 && (
        <p className="hint">
          No artifact templates are bound to this Application. Adding one on the Connectors page is
          what tells Tower where to look.
        </p>
      )}

      {confirmation !== null && confirmation.artifacts.length > 0 && (
        <ul className="artifact-list">
          {confirmation.artifacts.map((artifact) => (
            <li className="artifact" key={artifact.kind}>
              <div className="artifact__header">
                <span className="artifact__kind">{artifact.kind}</span>
                <StateBadge artifact={artifact} />
                {artifact.coordinate !== null && (
                  <code className="artifact__coordinate">{artifact.coordinate}</code>
                )}
                {artifact.url !== null && (
                  <a className="artifact__link" href={artifact.url} target="_blank" rel="noreferrer">
                    open in repository
                  </a>
                )}
              </div>

              {/*
                Why, for the states that need one. A wrong template produces a
                truthful "not found" that tells a reader nothing on its own, so
                the reason is carried beside it.
              */}
              {artifact.detail !== null && <p className="artifact__detail hint">{artifact.detail}</p>}

              <Digests artifact={artifact} />

              {/*
                The controls that change what a document will say. Accepting
                only appears when there is something to accept, and the digest
                it sends is the one on the screen: a person accepts what they
                looked at, not whatever the repository answers at the moment of
                the click.
              */}
              <div className="artifact__actions">
                {artifact.digest !== null && artifact.digest !== artifact.acceptedDigest && (
                  <button
                    type="button"
                    className="link-button"
                    onClick={() =>
                      run(
                        artifact.kind,
                        acceptArtifactDigest(
                          applicationVersionId,
                          artifact.kind,
                          artifact.coordinate ?? "",
                          artifact.digest ?? "",
                        ),
                      )
                    }
                    disabled={rowBusy === artifact.kind}
                  >
                    {artifact.acceptedDigest === null
                      ? "Accept this digest"
                      : "Accept the repository's newer digest"}
                  </button>
                )}

                {artifact.acceptedDigest !== null && (
                  <button
                    type="button"
                    className="link-button"
                    onClick={() =>
                      run(artifact.kind, withdrawArtifactDigest(applicationVersionId, artifact.kind))
                    }
                    disabled={rowBusy === artifact.kind}
                  >
                    Withdraw
                  </button>
                )}
              </div>

              {rowErrors[artifact.kind] !== undefined && (
                <div className="row-error">{describeError(rowErrors[artifact.kind])}</div>
              )}
            </li>
          ))}
        </ul>
      )}

      {confirmation !== null && confirmation.artifacts.length > 0 && (
        <p className="field-hint">
          Nothing read from a repository is stored. What a release document prints is the accepted
          digest below each coordinate, which is why accepting one is a deliberate act.
        </p>
      )}
    </div>
  );
}

// Five states, rendered as five different things.
//
// ABSENT and UNREAD are the pair most worth keeping apart: "the repository was
// read and has nothing there" and "Tower could not ask" send a reader in
// opposite directions, and an empty row would look the same for both.
function StateBadge({ artifact }: { artifact: ConfirmedArtifact }) {
  switch (artifact.state) {
    case "PRESENT":
      return <span className="badge badge--muted">in the repository</span>;
    case "DIVERGED":
      return <span className="badge">the tag was pushed over</span>;
    case "ABSENT":
      return <span className="badge">not in the repository</span>;
    case "NOT_ADDRESSABLE":
      return <span className="badge">cannot be addressed</span>;
    case "UNREAD":
    default:
      return <span className="badge">not read</span>;
  }
}

// What Tower holds, beside what the repository says. Kept visually subordinate
// in the same way a tracker's title is: the accepted digest is what documents
// print, and this is a comparison rather than a correction.
function Digests({ artifact }: { artifact: ConfirmedArtifact }) {
  if (artifact.digest === null && artifact.acceptedDigest === null) {
    return null;
  }

  return (
    <div className="artifact__digests">
      {artifact.acceptedDigest !== null && (
        <span className="artifact__digest">
          accepted <code>{artifact.acceptedDigest}</code>
        </span>
      )}
      {artifact.digest !== null && artifact.digest !== artifact.acceptedDigest && (
        <span className="artifact__digest artifact__digest--reported">
          repository <code>{artifact.digest}</code>
        </span>
      )}
      {artifact.storedAt !== null && (
        <span className="artifact__attr">received {artifact.storedAt.slice(0, 10)}</span>
      )}
      {artifact.sizeBytes > 0 && (
        <span className="artifact__attr">{Math.round(artifact.sizeBytes / 1024)} KiB</span>
      )}
    </div>
  );
}
