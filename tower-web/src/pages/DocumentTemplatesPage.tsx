import { useEffect, useState } from "react";
import {
  createDocumentTemplate,
  deleteDocumentTemplate,
  listDocumentSections,
  listDocumentTemplates,
  updateDocumentTemplate,
  type DocumentSectionInfo,
  type DocumentTemplate,
} from "../api/client";
import ErrorNote from "../components/ErrorNote";

// Document Templates (OQ-010, ADR-013).
//
// A template chooses which sections a release document contains and in what
// order. It holds no markup, no expressions and no user-authored text — that is
// the decision ADR-013 records, and the reason NFR-025 survives it: every byte
// of a release document is still written by Tower's own renderers, so
// regenerating an unchanged Release Pack still produces identical output.
//
// The complete document is not editable and is not listed for editing. It is
// what Tower generates when nobody has expressed a preference, so there is
// always one template that tells the whole truth about a release.
export default function DocumentTemplatesPage() {
  const [sections, setSections] = useState<DocumentSectionInfo[]>([]);
  const [templates, setTemplates] = useState<DocumentTemplate[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const [editing, setEditing] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [chosen, setChosen] = useState<string[]>([]);

  useEffect(() => {
    listDocumentSections()
      .then(setSections)
      .catch((e: unknown) => setError(e));
    refresh();
  }, []);

  function refresh() {
    listDocumentTemplates()
      .then((all) => setTemplates(all.filter((t) => !t.builtIn)))
      .catch((e: unknown) => setError(e));
  }

  function reset() {
    setEditing(null);
    setName("");
    setChosen([]);
  }

  function edit(template: DocumentTemplate) {
    setEditing(template.id);
    setName(template.name);
    setChosen([...template.sections]);
    setError(null);
  }

  // Appended at the end when ticked, so ticking sections in the order you want
  // them read is enough for most templates; the arrows are there for the rest.
  function toggle(sectionName: string) {
    setChosen((current) =>
      current.includes(sectionName)
        ? current.filter((s) => s !== sectionName)
        : [...current, sectionName],
    );
  }

  function move(index: number, by: number) {
    const target = index + by;
    if (target < 0 || target >= chosen.length) return;
    const next = [...chosen];
    [next[index], next[target]] = [next[target], next[index]];
    setChosen(next);
  }

  function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const input = { name: name.trim(), sections: chosen };
    const request = editing === null
      ? createDocumentTemplate(input)
      : updateDocumentTemplate(editing, input);

    request
      .then(() => {
        reset();
        refresh();
      })
      .catch((err: unknown) => setError(err))
      .finally(() => setBusy(false));
  }

  function remove(template: DocumentTemplate) {
    setError(null);
    // Deleting a template loses a preference, not a document: a generated
    // document is disposable (IA-03) and was never stored in the first place.
    deleteDocumentTemplate(template.id)
      .then(() => {
        if (editing === template.id) reset();
        refresh();
      })
      .catch((err: unknown) => setError(err));
  }

  const heading = (sectionName: string) =>
    sections.find((s) => s.name === sectionName)?.heading ?? sectionName;

  return (
    <section className="page">
      <h2>Documentation templates</h2>
      <p className="hint">
        A template chooses which sections a release document contains and in what order. It carries no
        layout of its own: Tower renders every document itself, which is what keeps two generations of an
        unchanged release identical rather than merely similar.
      </p>
      <p className="hint">
        The complete document — every section, in Tower's own order — is always available and cannot be
        changed or removed. A template that leaves a section out says so in the document, so a reader can
        tell an empty section from an excluded one.
      </p>

      {error !== null && <ErrorNote error={error} />}

      <section className="panel">
        <h4>{editing === null ? "Define a template" : "Edit template"}</h4>
        <form onSubmit={save}>
          <div className="inline-form">
            <label htmlFor="template-name">Name</label>
            <input
              id="template-name"
              value={name}
              placeholder="Handover only"
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <fieldset className="section-picker">
            <legend>Sections</legend>
            {sections.map((section) => (
              <label key={section.name} className="checkbox-row">
                <input
                  type="checkbox"
                  checked={chosen.includes(section.name)}
                  onChange={() => toggle(section.name)}
                />
                <span>
                  <strong>{section.heading}</strong>
                  <br />
                  <span className="hint">{section.description}</span>
                </span>
              </label>
            ))}
          </fieldset>

          {chosen.length > 0 && (
            <div>
              <p className="hint">Order in the document:</p>
              <ol className="ordered-sections">
                {chosen.map((sectionName, index) => (
                  <li key={sectionName}>
                    {heading(sectionName)}
                    <button type="button" onClick={() => move(index, -1)} disabled={index === 0}>
                      ↑
                    </button>
                    <button
                      type="button"
                      onClick={() => move(index, 1)}
                      disabled={index === chosen.length - 1}
                    >
                      ↓
                    </button>
                  </li>
                ))}
              </ol>
            </div>
          )}

          <div className="inline-form">
            <button type="submit" disabled={busy || name.trim() === "" || chosen.length === 0}>
              {editing === null ? "Create template" : "Save changes"}
            </button>
            {editing !== null && (
              <button type="button" onClick={reset}>
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>

      <section className="panel">
        <h4>Templates</h4>
        {templates.length === 0 ? (
          <p className="hint">
            No templates defined. Every release document is the complete document until one is.
          </p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Sections, in order</th>
                <th>Leaves out</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {templates.map((template) => (
                <tr key={template.id}>
                  <td>{template.name}</td>
                  <td>{template.sections.map(heading).join(" → ")}</td>
                  <td>{template.omitted.length === 0 ? "—" : template.omitted.map(heading).join(", ")}</td>
                  <td>
                    <button type="button" onClick={() => edit(template)}>
                      Edit
                    </button>
                    <button type="button" onClick={() => remove(template)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </section>
  );
}
