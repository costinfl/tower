# ADR-013 — Documentation Templates Select Sections, Not Markup

**Status**

Accepted

---

## Context

OQ-010 asks whether documentation templates should be customisable, and has stood Deferred since planning.

Milestone 3 answers OQ-009 by adding an HTML renderer beside the Markdown one, which makes the question live: two formats now exist, and the shape of a release document has become something a team can reasonably have an opinion about.

The opinion teams actually voice is narrow.

It is almost always "we do not want the Validation Iterations section in what we hand to operations", or "put Handover at the top, because that is the part anyone reads at three in the morning".

It is very rarely "let us write our own document".

NFR-025 states that regenerating an unchanged Release Pack shall produce byte-identical output.

That requirement is what makes a generated document disposable rather than an artifact to be managed (IA-03), and it is the reason neither existing renderer stamps a generation date.

A template engine is the ordinary way that guarantee is lost.

An engine that stamps a generation date, or iterates a map in hash order, or resolves a helper differently between two versions of a library, turns every regeneration into a spurious diff — and it does so silently, so nobody notices for months.

Once user-authored markup exists, the reproducibility guarantee stops being Tower's to make.

There is a second consequence that matters more than reproducibility.

Both renderers state absent information rather than omitting it: an empty Handover produces "No Handover information has been prepared for this Release Pack" rather than no section at all.

That rule exists because a document that silently leaves out an empty section reads as though none was needed, and someone deciding whether a release is ready would draw the wrong conclusion from it.

A customisation mechanism that could remove a section would break that rule at exactly the moment it matters, unless the document says what was removed.

A user-authored template cannot be made to say that, because Tower would not know what the author left out — only what they wrote.

---

## Decision

A **Document Template** shall select which sections a release document contains and in what order.

It shall not carry markup, expressions, or any user-authored text beyond its own name.

The set of sections a template may choose from shall be defined by Tower and served to clients rather than hardcoded in them.

Every byte of a rendered release document shall continue to be written by Tower's own renderers.

A template that leaves a section out shall be named in the document's closing note, together with the sections it does not include.

The title and the closing provenance note shall not be selectable sections.

The title says which release the page describes, and the note says the page is a view of the Canonical Model and not a source of truth (BR-07).

Neither is a preference, so neither is offered as a choice.

Tower shall define one built-in template — the **complete document** — containing every section in Tower's own order.

The complete document is what Tower generates when no template is chosen, and it shall not be editable, renameable or deletable.

There shall always be one template that tells the whole truth about a release, whatever anyone has configured.

Document Templates are user-owned configuration rather than business facts.

They live in the application layer beside External Bindings, not in the Domain Model, and like External Bindings they are excluded from export (ADR-010).

---

## Consequences

A team can produce the handover page an operator needs without the planning material around it, which is the request OQ-010 was actually about.

Reproducibility survives unchanged: rendering stays in Tower's code, so NFR-025 continues to hold for a templated document exactly as it does for a complete one.

Ordering is stored as a list and read back by position, so the same template renders identically on every machine.

Someone who wants a genuinely different document — their own headings, their own prose, their own layout — will not get it from Tower.

That is the cost, and it is accepted.

Tower's answer to that need is the Markdown output, which is a format designed to be edited by whoever receives it.

A template that omits sections produces a document that is honest about being partial.

The closing note names the template and lists what it does not include, so a reader can distinguish "no validation Iterations have been recorded" from "Iterations were not included in this document" — two statements that lead to opposite decisions.

Deleting a template loses a preference and nothing else.

Documents already generated with it are unaffected, because a generated document is disposable (IA-03) and was never stored.

Adding a section to release documentation in a later milestone adds it to the complete document automatically, and leaves existing templates unchanged.

An existing template will then omit the new section, and will say so — which is the correct behaviour, since nobody chose to include something that did not exist when they saved it.

If a future need genuinely requires user-authored markup, this decision will have to be revisited rather than extended.

That revision must decide what happens to NFR-025 first, because a template engine cannot be added without answering it.
