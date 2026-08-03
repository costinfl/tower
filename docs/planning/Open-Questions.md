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

Answer

None yet. Synchronization is on demand only, and stays that way until the Connectors have been proven
against real systems.

Status

Postponed — on demand only

---

## OQ-006

Should synchronization be manual, scheduled or event driven?

Answer

Manual for now. Scheduling is postponed deliberately rather than merely unbuilt.

A scheduler would be the first thing in Tower that reaches an External System without a person asking.
That is not a conflict with ADR-001 — it would still only read — but it changes who is watching when a
Connector misbehaves. Automating a Connector nobody has yet watched work would turn a visible failure
into a background one, and the Sync Run record would be the only witness.

Tower also has one user, who is the person pressing the button. Frequency solves a problem that does not
exist yet.

The condition for revisiting is not a date but evidence: the Milestone 2 exit checks passed against a
real cluster, and each Connector observed behaving correctly over a period of ordinary use. When that
holds, scheduling should be recorded in an ADR rather than added quietly, because "Tower acts on its own
timetable" is a claim about what Tower is.

Status

Postponed — manual until the Connectors are proven

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

Answer

The question is largely answered already, from the other end. ADR-011 records that a Collector appends an
Observation only when what it read differs from what Tower already holds, so a full read is already
incremental in its effect: reading everything and recording nothing is the ordinary case.

Whether a Connector should also read less — asking an External System only for what changed — stays open,
and is worth revisiting only if a real synchronization proves slow. It has not.

Status

Open — deferred until a real synchronization proves slow

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

## OQ-016

How often should a vendored API description be refreshed, and does the drift check belong in the build?

ADR-019 commits slices of vendors' own API descriptions and checks Connector fixtures against them. A stale
slice quietly stops being evidence: it goes on agreeing with fixtures the vendor no longer produces.

`scripts/check-openapi-drift.sh` exists and is deliberately kept out of CI, because a build that goes red
because GitHub edited a document is a build that went red for something the diff did not do. That leaves the
refresh depending on somebody remembering.

The unresolved part is which failure is worse: a suite that silently rots, or a build that breaks for
reasons outside the repository. A scheduled job that opens an issue rather than failing a build is the
obvious third answer, and it needs the scheduler that is itself postponed.

Status

Open

---

## OQ-017

Should one synchronization history describe both a Deployment Platform read and a CI/CD read?

A SyncRun records that a Connector ran, what it read and what it appended, and the Connectors screen
renders it. One line of that type does not survive the CI/CD Connector: `confirmsLiveness()` is true of
any run that succeeded, and the screen turns it into "everything Tower already knew was confirmed still
present at this time".

That is true of a Deployment Platform, which reads what is running now. It is false of a CI system,
which reads what happened. A successful read of a job with no new runs confirms only that nothing was
deployed by that job since Tower last looked — narrower, and about a different subject.

So the CI/CD Collector returns a PipelineSyncReport of its own rather than a SyncRun, which is correct
and leaves two histories where a user wants one. The question is which way to close that: give SyncRun
a way to say what a successful run establishes, or keep the two apart and show them as what they are.
The first is a schema change to a table with history in it; the second means a user checks two places.

Not decided under time pressure while the Connector was being built, which is why it is written down
here instead.

Status

Open

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
