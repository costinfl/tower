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

Answered — kept apart, and shown as what they are.

The two histories stay separate, and the Connectors screen shows both: Synchronization, then Pipeline
runs, each stating what its own clean read establishes. A Deployment Platform's clean run says
"everything Tower already knew was confirmed still present at this time". A pipeline's clean read says
"these jobs deployed nothing since Tower last looked — which says nothing about what is running now".

The alternative was rejected on what it would cost to say. Giving SyncRun a field for what a
successful run establishes is a schema change to a table that already holds history, and every row
written before it would have to be assigned a meaning nobody recorded. Worse, the merged line has to
be worded for both subjects at once, and there is no sentence that is true of "what is running" and
"what happened" together — one of the two would end up claiming something Tower does not know, which
is the failure this whole question exists to prevent.

Against that, the cost of keeping them apart turned out to be smaller than it looked when this was
written. "A user checks two places" is really "a user reads one screen with two sections on it", and
the two sections are worth distinguishing anyway: a team whose CI system is untidy reads the second
one closely and the first one hardly at all.

The one thing this leaves is a reader who wants a single chronological list of everything Tower did.
That is a different feature from a synchronization record, and if it is ever wanted it should be built
as one rather than by flattening two kinds of evidence into a shape that fits neither.

---

## OQ-018

How does a release account for the database changes it carries?

Raised because the setting this is written for does not have one answer. Some projects keep their
migrations inside the application repository and run them with Flyway, so the schema version travels
with the Application Version and a deployment is one thing. Others keep migrations in a repository of
their own, run them with Liquibase, and organise the changelogs to a convention the team invented —
so the schema moves on its own schedule, through its own pipeline, and what is deployed to an
Environment is two things that must agree.

The second shape is what makes this a question rather than a feature request. Tower's whole model is
that an Observation is an Application Version seen in an Environment (ADR-002). A schema that is
released separately is not an Application Version, is not deployed by the same job, and can be
correct or wrong independently of the code that reads it. Handover already carries a free-text
"database migrations" field (FR-028), which is where this currently lives and is deliberately just
prose.

What the decision turns on, and none of it is settled:

- Whether a schema version is a **second kind of Application Version** — cheap, reuses everything, and
  wrong if a schema is not something an Environment "runs".
- Whether it is a **property of an Application Version** — right for the Flyway shape, useless for the
  Liquibase-in-its-own-repo shape, and Tower must serve both.
- Whether it is **its own concept with its own Observations** — honest, and a large addition to a
  Domain Model that has stayed small on purpose.
- Whether Tower should read a migration tool at all, or only record what a person stated. Flyway and
  Liquibase both keep a history table in the database being migrated, which is a read against a
  production database rather than against a delivery system — a different class of credential from
  anything Tower holds today, and worth weighing against ADR-001 before anything is built.

Not to be answered by whichever shape gets implemented first. The risk here is exactly the one
ADR-020 caught between a build and a deployment: a model that fits one team's arrangement and
silently misrepresents another's.

Answered by ADR-022, after the question was sharpened by one fact it had not been written with: the
separate-repository shape addresses **several schemas from one run**. That is what settled it. A
multi-schema run does not fail as a unit, so a model that cannot say the Orders schema reached 4.2
while the Customers schema did not would be wrong in the situation a team most needs it right — which
rules out both the "property of an Application Version" and the "second kind of Application Version"
options in one stroke.

A schema that is delivered separately is an Application: something built, versioned, published and
applied to an Environment. Several schemas are several Applications, and the cost — the same version
string registered once per schema — is recorded in the ADR rather than discovered later. Tower shall
not read the migrated database: its history table is inside the database it migrates, and a database
credential cannot be made structurally read-only the way ADR-001 makes a Connector.

No new concept, and nothing built. Verified by expressing both shapes end to end (Scenario 9).

Status

Answered — a schema is delivered like anything else; a Schema concept waits for a trigger ADR-022
names

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
