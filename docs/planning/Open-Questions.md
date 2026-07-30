# Open Questions

**Status:** Draft

**Owner:** Architecture

---

# Purpose

This document captures architectural and product questions intentionally deferred beyond Milestone 1.

Items listed here are not blockers for implementation.

They represent future decisions that may influence later milestones.

---

# Product

## OQ-001

Can a Promotion Path be modified after Release Packs already reference it?

Status

Answered by ADR-007. Yes: editing publishes a new immutable version, and a Release Pack keeps referencing the version it was assigned.

---

## OQ-002

Can a Release Pack change Promotion Paths?

Status

Deferred

---

## OQ-003

Should Release Packs support explicit lifecycle states in addition to derived observed state?

Status

Answered by ADR-008. Archived is an explicit lifecycle flag owned by developers, kept separate from the state derived from Observations.

---

## OQ-004

Should archived Release Packs remain searchable?

Status

Deferred

---

# Observation

## OQ-005

What synchronization frequency should Connectors use?

Status

Implementation Decision

---

## OQ-006

Should synchronization be manual, scheduled or event driven?

Status

Implementation Decision

---

## OQ-007

How long should historical Snapshots be retained?

Answer

The question does not arise. ADR-017 derives a Snapshot from the Observation stream rather than storing one, so
there is nothing to retain.

Retention of the Observation stream itself is a separate question, and the only one that matters. ADR-011
left it open and it stays open.

Status

Answered — no Snapshot is stored

---

## OQ-008

Should Tower detect Environment drift automatically?

Status

Future Milestone

---

# Documentation

## OQ-009

Which output formats should be supported?

Examples

- Markdown
- HTML
- PDF
- DOCX

Answer

Markdown (Milestone 1), HTML and DOCX (Milestone 3). PDF is declined.

All three are written by Tower's own code from the assembled document, with no template engine, no library
and no generation timestamp, so all three satisfy NFR-025.

PDF is declined rather than deferred, and the reason is what happens to a document after Tower hands it
over. Confluence imports a Word document as an editable page and cannot do that with a PDF, while DOCX
converts to PDF trivially. The relationship is one-way, so DOCX is the format that keeps both options and
PDF is the one that throws the round trip away. A team that wants a PDF produces it from the DOCX.

Recorded in ADR-015, which also records why the DOCX is written directly rather than through a library: a
DOCX is a ZIP of XML, ZIP entries carry timestamps and word-processing libraries stamp a created date, so
the obvious approach would have broken NFR-025 silently.

Status

Answered

---

## OQ-010

Should documentation templates be customizable?

Answer

Yes, by selecting sections rather than by authoring markup.

A Document Template chooses which sections a release document contains and in what order. It carries no
markup, no expressions and no user-authored text beyond its own name, so every byte of a rendered document
is still written by Tower — which is what keeps NFR-025 true. A template that leaves a section out is named
in the document, together with what it does not include.

The complete document remains available and cannot be edited or deleted, so there is always one template
that tells the whole truth about a release.

Recorded in ADR-013. Implemented in Milestone 3 (FR-062 to FR-065).

Status

Answered

---

# User Experience

## OQ-011

Should Release Packs support timeline visualization?

Status

Future Milestone

---

## OQ-012

Should dashboard widgets be customizable?

Status

Future Milestone

---

# Integration

## OQ-013

Should Connectors support incremental synchronization?

Status

Implementation Decision

---

## OQ-014

Should synchronization failures generate notifications?

Status

Future Milestone

---

## OQ-015

Should Connectors expose health information?

Status

Future Milestone

---

# Future Capabilities

The following ideas are intentionally outside Milestone 1.

- Environment drift detection
- Rollback visualization
- Dependency visualization
- Deployment history
- Notifications
- Analytics
- Release metrics
- Trend analysis
- Multi-project dashboards
- Cross-team reporting

---

# Governance

Questions recorded here shall be resolved through future ADRs.

No implementation shall introduce architectural decisions that contradict existing ADRs without creating a new ADR.
