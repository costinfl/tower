# ADR-015 — DOCX Is The Fourth Format, And Is Written Directly

**Status**

Accepted

---

## Context

OQ-009 left PDF and DOCX open after Markdown and HTML shipped. Issue #5 names both as candidates and does not choose.

The question that decides it is not which format looks better. It is what happens to a release document after Tower hands it over.

A release document is given to another team. That team pastes it into a ticket, attaches it to a change record, or — most often in the organisations Tower is built for — puts it in Confluence.

Confluence imports a Word document and turns it into an editable page. It does not do this for PDF. A PDF can be attached and previewed, but its content cannot be brought back into an editable page, because PDF is a fixed-layout format and recovering structure from it is lossy conversion rather than import.

The relationship is not symmetric in the other direction either, and that is what settles it. DOCX converts to PDF trivially — Word, LibreOffice and Google Docs all do it, and so do the print pipelines already in place wherever PDFs are produced today. PDF does not convert back to an editable document in any way worth relying on.

So a team that wants PDF can have it from DOCX. A team that wants an editable handover cannot have it from PDF. Choosing PDF would deliver the output and throw away the round trip.

There is a second question, and it is the one that decides *how* rather than *which*.

NFR-025 requires regenerating an unchanged Release Pack to produce byte-identical output. The Markdown and HTML renderers hold to it because they are string assembly with no template engine and no generation timestamp.

A DOCX is a ZIP of XML parts, and both halves of that are hostile to reproducibility. ZIP entries carry modification timestamps. Word-processing libraries write a `docProps/core.xml` stamped with a created and a modified date, and some write a random document identifier as well. A DOCX produced by the obvious means would differ on every generation, in bytes nobody would think to look at.

That is precisely the failure ADR-013 refused to accept for templates: a guarantee lost quietly, in a way that turns every regeneration into a spurious diff.

---

## Decision

**DOCX shall be the fourth output format.** PDF shall not be implemented, and a team that wants one shall produce it from the DOCX.

**The DOCX shall be written directly** — the ZIP container and the WordprocessingML parts assembled by Tower's own code — rather than through a word-processing library.

Every ZIP entry shall carry a fixed timestamp. No part shall record a creation date, a modification date, a generation date or a random identifier. `docProps/core.xml` shall be omitted rather than written empty, because the part that does not exist cannot acquire a date later.

The document shall use real Word heading styles rather than direct formatting that merely looks like headings. This is not cosmetic: Confluence's import maps Word heading styles to Confluence headings, and a document whose headings are bold paragraphs imports as one flat wall of text — losing exactly the structure the format was chosen for.

Text taken from the model shall be escaped for XML, and characters XML cannot represent shall be removed. Handover text is written by people and routinely holds shell commands and SQL; a control character in it would produce a file that Word refuses to open, which is a worse failure than a missing character because it destroys the whole document rather than one line of it.

---

## Consequences

The same reproducibility guarantee now covers all three generated formats. A regenerated DOCX is byte-identical, which means it can be diffed, checksummed and stored like the other two.

Tower gains no dependency for this. Markdown, HTML and DOCX are all written by code in `tower-docgen`, which keeps the rule ADR-013 relies on: every byte of a release document is Tower's own.

The cost is that Tower now contains a small amount of OOXML, a format with a large specification. What is written here is the subset a release document needs — paragraphs, headings, tables, monospaced blocks — and nothing else. A feature that needed images, footnotes or numbering would have to add to it, and at that point a library becomes worth reconsidering, provided the determinism question is answered first rather than after.

The output is deliberately plain. It is a document to read, print or paste into a wiki, and one that arrived heavily styled would fight whatever template the receiving system applies.

A visitor to the published demo cannot download a DOCX. The demo has no backend, and unlike Markdown its output is not something a browser shim can assemble convincingly. This is consistent with how the demo already treats the other downloads.

PDF stays unimplemented rather than deferred-with-intent. If it is ever revisited, the first question is not which library renders best but whether it produces identical bytes for identical input — most do not, because they embed a creation date, and that is the question this decision record exists to make unavoidable.
