import { useState } from "react";
import {
  getReleaseDocumentMarkdown,
  releaseDocumentDownloadUrl,
  releaseDocumentHtmlDownloadUrl,
  releaseDocumentHtmlUrl,
} from "../api/client";
import ErrorNote from "./ErrorNote";
import { DEMO_MODE } from "../demo/install";

interface ReleaseDocumentPanelProps {
  releasePackId: string;
  packName: string;
}

// Release documentation generated from the Canonical Model (FR-024).
//
// The Markdown is shown verbatim in a <pre> rather than rendered to HTML. What
// gets handed to another team is the Markdown itself, so previewing the exact
// bytes is more honest than previewing a prettier interpretation of them — and
// it means what you read here is what you download.
//
// Generation is a read: nothing is stored, and the same model always produces
// the same bytes, so this can be regenerated freely (IA-03, Scenario 8).
export default function ReleaseDocumentPanel({ releasePackId, packName }: ReleaseDocumentPanelProps) {
  const [markdown, setMarkdown] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [copied, setCopied] = useState(false);

  function generate() {
    setBusy(true);
    setError(null);
    setCopied(false);
    getReleaseDocumentMarkdown(releasePackId)
      .then(setMarkdown)
      .catch((e: unknown) => setError(e))
      .finally(() => setBusy(false));
  }

  function copy() {
    if (markdown === null) return;
    navigator.clipboard
      .writeText(markdown)
      .then(() => setCopied(true))
      .catch((e: unknown) => setError(e));
  }

  return (
    <section className="panel">
      <h4>Release documentation</h4>
      <p className="hint">
        Generated from the Canonical Model each time you ask for it. Nothing is stored, so this is always
        current and never needs keeping in step by hand. Markdown is for pasting into a ticket; HTML opens
        in a browser and prints, and carries its own styles so it still reads correctly from a file share.
      </p>

      <div className="inline-form">
        <button type="button" onClick={generate} disabled={busy}>
          {busy ? "Generating…" : markdown === null ? "Generate" : "Regenerate"}
        </button>
        {/*
          Downloads and the HTML view are plain links, so the browser navigates
          rather than calling fetch — which the demonstration's backend shim
          cannot intercept. On the published demo they would land on the SPA
          fallback and look like the app reloading, so they are not offered
          there. The Markdown preview below still works, because that is a
          fetch.
        */}
        {!DEMO_MODE && (
          <>
            <a className="button-link" href={releaseDocumentDownloadUrl(releasePackId)} download>
              Download Markdown
            </a>
            {/* noopener on a new tab, kept as a habit even for our own origin. */}
            <a
              className="button-link"
              href={releaseDocumentHtmlUrl(releasePackId)}
              target="_blank"
              rel="noopener noreferrer"
            >
              View HTML
            </a>
            <a className="button-link" href={releaseDocumentHtmlDownloadUrl(releasePackId)} download>
              Download HTML
            </a>
          </>
        )}

        {markdown !== null && (
          <button type="button" onClick={copy}>
            {copied ? "Copied" : "Copy"}
          </button>
        )}
      </div>

      {DEMO_MODE && (
        <p className="hint">
          Downloading and the HTML view need the backend that generates them, which this
          demonstration does not have. The Markdown preview below is real.
        </p>
      )}

      {error !== null && <ErrorNote error={error} />}

      {markdown !== null && (
        <>
          <p className="hint">
            Preview of <code>{packName}</code> — {markdown.length} characters. This is exactly what the
            download contains.
          </p>
          <pre className="document-preview">{markdown}</pre>
        </>
      )}
    </section>
  );
}
