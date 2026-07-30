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

Capabilities beyond these requirements shall be evaluated in future milestones.
