import { useEffect, useState } from "react";
import {
  DocumentTemplate,
  getReleaseDocumentMarkdown,
  listDocumentTemplates,
  releaseDocumentDocxDownloadUrl,
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
  const [templates, setTemplates] = useState<DocumentTemplate[]>([]);
  const [templateId, setTemplateId] = useState("");

  // Loaded once. A failure here is deliberately not surfaced as an error: the
  // picker is an option on top of a document that generates perfectly well
  // without one, so a list that will not load must not stop someone generating.
  useEffect(() => {
    listDocumentTemplates()
      .then((all) => setTemplates(all.filter((t) => !t.builtIn)))
      .catch(() => setTemplates([]));
  }, []);

  // Empty means no template, which is the complete document — the same request
  // Tower answered before templates existed (OQ-010).
  const chosen = templateId === "" ? undefined : templateId;
  const chosenTemplate = templates.find((t) => t.id === templateId);

  function generate() {
    setBusy(true);
    setError(null);
    setCopied(false);
    getReleaseDocumentMarkdown(releasePackId, chosen)
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

  // Changing the template invalidates what is on screen. Clearing the preview
  // is more honest than leaving one that no longer matches the picker beside
  // it — the reader would have no way to tell.
  function chooseTemplate(id: string) {
    setTemplateId(id);
    setMarkdown(null);
    setCopied(false);
  }

  return (
    <section className="panel">
      <h4>Release documentation</h4>
      <p className="hint">
        Generated from the Canonical Model each time you ask for it. Nothing is stored, so this is always
        current and never needs keeping in step by hand. Markdown is for pasting into a ticket; HTML opens
        in a browser and prints, and carries its own styles so it still reads correctly from a file share.
        Word imports into Confluence as an editable page, and converts to PDF from there.
      </p>

      <div className="inline-form">
        {/*
          A template chooses which sections the document contains and in what
          order (OQ-010, ADR-013). It supplies no markup: rendering stays in
          Tower's own code, which is what keeps a regenerated document
          byte-identical.
        */}
        <label htmlFor="document-template">Template</label>
        <select id="document-template" value={templateId} onChange={(e) => chooseTemplate(e.target.value)}>
          <option value="">Complete document</option>
          {templates.map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>

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
            <a className="button-link" href={releaseDocumentDownloadUrl(releasePackId, chosen)} download>
              Download Markdown
            </a>
            {/* noopener on a new tab, kept as a habit even for our own origin. */}
            <a
              className="button-link"
              href={releaseDocumentHtmlUrl(releasePackId, chosen)}
              target="_blank"
              rel="noopener noreferrer"
            >
              View HTML
            </a>
            <a className="button-link" href={releaseDocumentHtmlDownloadUrl(releasePackId, chosen)} download>
              Download HTML
            </a>
            <a className="button-link" href={releaseDocumentDocxDownloadUrl(releasePackId, chosen)} download>
              Download Word
            </a>
          </>
        )}

        {markdown !== null && (
          <button type="button" onClick={copy}>
            {copied ? "Copied" : "Copy"}
          </button>
        )}
      </div>

      {/*
        Said here as well as in the document itself. Someone choosing a template
        should see what it costs before they generate, not only afterwards in
        the closing note.
      */}
      {chosenTemplate !== undefined && chosenTemplate.omitted.length > 0 && (
        <p className="hint">
          <code>{chosenTemplate.name}</code> leaves out {chosenTemplate.omitted.length} of the document&apos;s
          sections. The document names them, so a reader can tell an empty section from an excluded one.
        </p>
      )}

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
