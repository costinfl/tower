# Functional Requirements

**Status:** Draft

**Owner:** Architecture

**Related:**

- Context.md
- Information-Architecture.md
- ../domain/Domain-Model.md

---

# Purpose

This document defines the functional capabilities required for Milestone 1.

FR-055 to FR-061 extend it to Milestone 2, and FR-062 to FR-067 to Milestone 3; each group is under its own heading.

Each requirement is atomic, uniquely identified and traceable.

The requirements intentionally avoid implementation details.

---

# Environment Management

## FR-041

The system shall allow users to define Environments.

---

## FR-042

The system shall allow users to modify an Environment's name and Stage.

---

## FR-043

The system shall require every Environment to carry a Stage classification.

---

## FR-044

The system shall refuse to delete an Environment referenced by any Promotion Path version.

---

# Application Registry

## FR-045

The system shall allow users to register Applications.

---

## FR-046

The system shall allow users to register Application Versions against an Application.

---

## FR-047

The system shall not permit an Application Version to be modified after registration.

---

## FR-048

The system shall refuse to delete an Application Version contained in any Release Pack.

---

# Release Pack Management

## FR-001

The system shall allow users to create Release Packs.

---

## FR-002

The system shall allow users to modify Release Pack metadata.

---

## FR-003

The system shall allow Application Versions to be associated with a Release Pack.

---

## FR-004

The system shall allow Application Versions to be removed from a Release Pack.

---

## FR-005

The system shall allow a Promotion Path to be assigned to a Release Pack.

---

## FR-006

The system shall maintain Handover information for every Release Pack.

---

## FR-049

The system shall allow users to delete a Release Pack that carries no validation history.

---

## FR-050

The system shall allow users to create, update and remove validation Iterations against a Release Pack.

---

## FR-051

The system shall allow a Release Pack to be archived and restored, independently of where it has been observed.

---

# Promotion Paths

## FR-007

The system shall allow users to define Promotion Paths.

---

## FR-008

The system shall allow Promotion Paths to contain an ordered sequence of Environments.

---

## FR-009

The system shall present Promotion Paths visually as Lanes.

---

## FR-052

The system shall allow a Promotion Path to be edited, publishing a new version rather than altering an existing one.

---

## FR-053

The system shall allow a Promotion Path to be archived, and shall refuse to delete one that any Release Pack references.

---

# Environment Visibility

## FR-010

The system shall display the currently observed contents of every Environment.

---

## FR-011

The system shall display every Application Version observed within an Environment.

---

## FR-012

The system shall display the Observation timestamp associated with Environment information.

---

## FR-013

The system shall indicate the source of every Observation.

---

# Release Visibility

## FR-014

The system shall display the Applications belonging to a Release Pack.

---

## FR-015

The system shall display the Application Versions belonging to a Release Pack.

---

## FR-016

The system shall display the current observed position of a Release Pack within its Promotion Path.

---

## FR-017

The system shall display Iterations associated with a Release Pack.

---

## FR-018

The system shall display Handover information associated with a Release Pack.

---

# Observation

## FR-019

The system shall collect Observations from External Systems through Connectors.

---

## FR-020

The system shall normalize retrieved information into the Canonical Model.

---

## FR-021

The system shall preserve Observation timestamps.

---

## FR-022

The system shall preserve the origin of every Observation.

---

## FR-023

The system shall never modify Observations after they have been stored.

---

# Documentation Generation

## FR-024

The system shall generate release documentation from the Canonical Model.

---

## FR-025

Generated documentation shall include Release Pack contents.

---

## FR-026

Generated documentation shall include Application Versions.

---

## FR-027

Generated documentation shall include Promotion Path information.

---

## FR-028

Generated documentation shall include Handover information.

---

## FR-029

Generated documentation shall include deployment instructions.

---

## FR-030

Generated documentation shall include validation Iterations.

---

# Traceability

## FR-031

Every displayed Application Version shall be traceable to an Observation.

---

## FR-032

Every generated document shall be traceable to the Canonical Model.

---

## FR-033

Every Environment shall expose its current observed state.

---

## FR-034

Every Release Pack shall expose its current observed state.

---

# Architectural Constraints

## FR-035

The system shall never execute deployments.

---

## FR-036

The system shall never modify External Systems.

---

## FR-037

The system shall remain vendor neutral.

---

## FR-038

Every visualization shall originate from the Canonical Model.

---

## FR-039

Every Connector shall operate in read-only mode.

---

## FR-040

The system shall preserve segregation of duties by limiting itself to observation, correlation and documentation.

---

# Milestone 2 — Automated Synchronization

FR-019 to FR-022 require Observations to be collected through Connectors.

ADR-006 records that Milestone 1 satisfies them with a manual source and Milestone 2 satisfies them with external Connectors.

The requirements below cover what Milestone 2 adds in order to do so.

---

## FR-055

The system shall allow an Environment to be bound to a Deployment Platform locator.

---

## FR-056

The system shall allow an Application to be bound to an image reference and a pattern that yields the Application Version.

---

## FR-057

The system shall synchronize a Connector on user request.

---

## FR-058

The system shall append an Observation only when the observed state differs from the newest state already held.

---

## FR-059

The system shall record the outcome of every synchronization run and make it available to the user.

---

## FR-060

The system shall report workloads that it could not attribute to a bound Application.

---

## FR-061

The system shall verify Connector connectivity without modifying the External System.

---

# Milestone 3 — Documentation Formats

## FR-062

The system shall allow a user to define a Document Template that selects which sections a release document contains and in what order.

A Document Template shall carry no markup and no user-authored text beyond its name (ADR-013).

---

## FR-063

The system shall provide a complete document containing every section, which shall be produced when no Document Template is chosen and which shall not be editable or deletable.

---

## FR-064

A release document produced from a Document Template shall name that template and the sections it does not include.

---

## FR-065

The system shall report the sections a Document Template may select from, so that no client depends on a list of its own.

---

## FR-067

The system shall record every version of a Release Pack's Handover information and make the history available to the user.

No operation shall modify or delete a recorded version. Restoring an earlier version shall be an ordinary edit that appends a new one.

---

## FR-066

The system shall generate a release document as a Word document, using heading styles that survive import
into a wiki.

PDF is not generated. ADR-015 records that a Word document imports as editable content and converts to PDF,
while a PDF does neither in reverse.

---

# Milestone 4 — Snapshots and Historical Comparison

## FR-068

The system shall report the observed state of an Environment as it stood at any instant, derived from
Observations rather than from stored Snapshots (ADR-017).

An instant nobody captured shall be answerable, because the question is usually asked about the moment an
incident began.

---

## FR-069

The system shall report the difference between two observed states, whether one Environment at two instants
or two Environments at one instant.

A difference shall distinguish a version that changed from one that arrived and one that is gone, and shall
cite the Observation behind each side.

---

## FR-070

The system shall report, for each Environment a Release Pack's contents have been observed in, when the first
of those contents was seen there and when the last of them was.

A release that has only partly arrived shall be reported as such and shall name the Application Versions still
outstanding, not merely count them.

An Environment in which none of the release has been observed shall not appear. Its absence is not evidence
that the release is absent from it.

---

# Milestone 5 — Operational Dashboard

## FR-071

The system shall provide a single operational view across all active Release Packs, so that a developer need
not open each one to see where things stand.

Archived Release Packs shall be excluded. Archiving records that a team stopped working a release (ADR-008),
and a release nobody is working is not in flight.

---

## FR-072

For each Environment, the system shall report which active Release Packs are heading for it, and how much of
each has been observed there.

A Release Pack is heading for an Environment when the pack's own pinned Promotion Path version names that
Environment (ADR-007). The expectation comes from the team's topology and never from Tower inferring where a
release ought to go.

An Environment more than one active Release Pack is heading for shall be identified as such, because that is
the situation the dashboard exists to surface.

---

## FR-073

The system shall not rank Release Packs, recommend one, or present them in an order derived from their
progress.

Converging releases shall be ordered by name. Deciding which release proceeds belongs to the team; Tower
shows the situation and stops there (ADR-001; Guardrails.md lists "Visualizes" and does not list "Decides").

---

## FR-074

The dashboard shall offer no operation that promotes, deploys, approves or triggers anything.

The resource shall accept a read and refuse every other method. This restates ADR-001 at the point where a
control would be most tempting to add.

---

## FR-075

Summary indicators shall distinguish what Tower has been told from what is true.

A count of Release Packs observed nowhere, or of Environments nothing has been recorded against, is a count
of silence. It shall be labelled as such and never presented as a count of failure or of absence
(Scenarios.md Scenario 4).

---

# CI/CD Connector

## FR-076

Tower shall read runs of a pipeline through a CI/CD Connector, without modifying the CI system.

Reading a run is a read like every other Connector operation (ADR-001, CM-01). Tower shall never start, stop,
retry or configure a pipeline.

---

## FR-077

A successful run of a pipeline bound as a deployment shall produce an Observation.

The Observation shall carry the instant the run reported, not the instant Tower read it, and shall identify the
CI/CD Connector as its source (ADR-006, ADR-020).

---

## FR-078

A run that did not succeed shall be reported and not recorded.

A failed, aborted or cancelled run is not evidence that anything reached an Environment. A run whose outcome
Tower does not recognise shall be treated the same way, rather than assumed successful (ADR-020, and the
treatment FR-060 gives an unattributable workload).

---

## FR-079

A pipeline job shall be bound to the Application and Environment its runs concern, and to where in a run the
Application Version appears.

The binding shall name the version's source — a build parameter, the run's own name, or the job's path — and
carry a version pattern in the sense of FR-056. A Connector shall not infer any of these (ADR-012, ADR-020).

---

## FR-080

Tower shall report a run it cannot attribute rather than discarding it.

A run whose bound version source holds nothing, or whose pattern does not match what it holds, shall be
reported with the reason. A user whose pattern is wrong needs to see what it failed on; a silently shorter
list gives them nothing to work from (FR-060).

---

## FR-081

Reading the same run twice shall record nothing the second time.

Runs are identified by their job and run identifier. Where a re-read yields what Tower already holds, no
Observation is appended, which is ADR-011 applied unchanged.

---

# Artifact Repository Connector

## FR-082

Tower shall confirm through an Artifact Repository Connector that the binaries an Application Version names are
present, without modifying the repository.

Confirming is a read like every other Connector operation (ADR-001, CM-01). Tower shall never upload, copy,
promote, re-tag or delete an artifact, and shall never treat the repository an artifact sits in as an
Environment (ADR-021).

---

## FR-083

An artifact's coordinate shall be composed from a template bound to the Application, one template per artifact
kind.

The template composes the vendor's locator from what Tower already holds — the version and the commit of an
Application Version — where FR-056's version pattern extracts a version from what the vendor produced. Both are
External Bindings in the sense of ADR-012; they run in opposite directions, and ADR-021 records why.

The kind is the team's own word for what the template addresses — an image, a chart, an installer. Tower shall
not interpret it, in the same way that a work item's status is the tracker's word and not Tower's (ADR-018).

---

## FR-084

Nothing an Artifact Repository Connector reads shall be stored.

An artifact has no Environment, so it is not an Observation; nobody stated it, so it is not User-Owned
Information. It shall be shown beside what Tower holds and recorded nowhere (ADR-021, IA-05).

---

## FR-085

An artifact the repository does not hold shall be reported as absent, and distinguished from a repository that
could not be read.

An artifact that is not there yet is an ordinary situation. A repository that refused the credential or did not
answer is a different one, and Tower shall never present either as the other (the treatment ADR-018 gives an
unknown work item identifier).

---

## FR-086

A digest a release document prints shall be one a person accepted.

A tag is mutable, and the bytes beneath it can change. A document that printed whatever the repository said at
the moment of rendering would stop regenerating byte-identically (NFR-025), so what a document carries shall be
an accepted digest, held by Tower and changed only by a person accepting a newer one — the rule ADR-018 sets
for a work item's title, applied unchanged.

Where the repository now reports a different digest under a coordinate Tower has an accepted digest for, that
difference shall be shown. It is the one way a team learns that a tag was pushed over.

---

# Deferred

## FR-054

IA-02 requires User-Owned Information to be versioned by Tower.

Promotion Paths are versioned (ADR-007), Observation history is preserved, and Handover information is now versioned (ADR-016): every edit appends an immutable revision and none is ever modified or deleted.

Release Pack metadata remains unversioned. A rename overwrites the previous name, and ADR-016 records that as a deliberate remaining gap rather than an unmet requirement: it is a fact about Tower's own bookkeeping rather than about what a team was told to do.

---

# Scope

FR-001 to FR-054 define the functional scope of Milestone 1.

FR-055 to FR-061 define what Milestone 2 adds.

FR-062 to FR-067 define what Milestone 3 adds.

FR-068 to FR-070 define what Milestone 4 adds.

FR-071 to FR-075 define what Milestone 5 adds.

FR-076 to FR-081 define the CI/CD Connector, and FR-082 to FR-086 the Artifact Repository Connector.

Capabilities beyond these requirements shall be evaluated in future milestones.
