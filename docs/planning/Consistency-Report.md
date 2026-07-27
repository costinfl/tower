# Documentation Consistency Report

**Status:** Draft

**Owner:** Architecture

**Related:**

- ../adr/ADR-005-Environments-Are-Shared-Across-Promotion-Paths.md
- ../adr/ADR-006-Manual-Observation-Entry.md
- ../adr/ADR-007-Promotion-Paths-Are-Versioned.md
- ../adr/ADR-008-Release-Pack-State-Derives-From-Environment-Stage.md
- Implementation-Plan.md

---

# Purpose

This document records the result of validating the Tower documentation set for internal consistency.

All twenty-five documents across Phases 1 to 5 were reviewed.

Findings are classified into three categories.

- Contradictions requiring an architectural decision.
- Editorial inconsistencies requiring documentation corrections.
- Coverage gaps requiring additional requirements.

No existing document has been modified as a result of this review.

Contradictions are resolved through proposed Architectural Decision Records rather than silent edits.

---

# Summary

Eight genuine contradictions were identified.

Six editorial inconsistencies were identified.

Six coverage gaps were identified.

The core architecture remains sound.

Observation as the atomic unit, read-only integration and the Release Pack as the central business concept hold together across every phase.

---

# Contradictions

## C1 — Environment and Promotion Path cardinality

The Domain Model states that an Environment belongs to one Promotion Path.

The Scenarios and the State Model both reuse UAT and Production across the Regular path and the Hotfix path.

The intended behaviour is that Promotion Paths converge on shared Environments and that a human decides which Release Pack proceeds.

Evidence

- Domain-Model.md, Environment section and Canonical Relationships diagram
- CRC-Cards.md, Environment responsibilities
- Scenarios.md, Scenario 1 and Scenario 3
- State-Model.md, Promotion Path examples

Resolution

ADR-005.

---

## C2 — Manual Observations conflict with externally sourced Observations

Milestone 1 includes manual Observation management.

The Glossary, ADR-002 and the Information Architecture all define an Observation as a fact collected from an External System.

EV-03 states that Observation Events originate from External Systems.

As written, Milestone 1 cannot legally produce an Observation.

Evidence

- Milestones.md, Milestone 1 scope
- Glossary.md, Observation
- ADR-002, externally sourced
- Information-Architecture.md, Observed Information
- Event-Model.md, EV-03

Resolution

ADR-006.

---

## C3 — Promotion Path immutability

SM-01 states that Promotion Paths are immutable after creation.

Backlog Epic 1 is Priority 1 and requires editing and deleting Promotion Paths.

OQ-001 defers a question that the Backlog has already answered.

Evidence

- State-Model.md, SM-01
- Backlog.md, Epic 1 capabilities
- Open-Questions.md, OQ-001

Resolution

ADR-007.

---

## C4 — Release Pack state is not derivable as specified

SM-02 states that Release Pack state is derived from Observations and is never manually assigned.

The Archived state describes a Release Pack that is no longer actively progressing, which is a human decision rather than an Observation.

Separately, the fixed state ladder assumes an Environment topology that user-defined Promotion Paths need not contain.

The Hotfix path contains no pre-production Environment, yet Pre-Production is documented as the only route into Production.

Evidence

- State-Model.md, Release Pack State, State Transitions and SM-02
- Scenarios.md, Scenario 3

Resolution

ADR-008.

---

## C5 — Connector scope

The Functional Requirements declare themselves to be the scope of Milestone 1.

FR-019 to FR-022 require Observations to be collected through Connectors.

Milestones.md defers the Connector framework to Milestone 2 and Backlog Epic 5 is Priority 2.

Evidence

- Functional-Requirements.md, purpose statement, scope statement and FR-019
- Milestones.md, Milestone 2
- Backlog.md, Epic 5

Resolution

ADR-006 records that FR-019 to FR-022 are satisfied in Milestone 1 by a manual source and in Milestone 2 by external Connectors.

---

## C6 — Documentation generation scope

Backlog Epic 4 is Priority 1 and FR-024 to FR-030 belong to Milestone 1.

Milestone 3 restates the same goal as a later milestone.

Evidence

- Backlog.md, Epic 4
- Functional-Requirements.md, FR-024
- Milestones.md, Milestone 3

Resolution

Documentation correction.

Milestone 1 delivers generation from the Canonical Model.

Milestone 3 delivers templates and additional export formats.

---

## C7 — Snapshots are deferred but structurally mandatory

The Event Model synchronization flow ends in Snapshot Created.

EV-07 requires every Snapshot to be created from Observations.

Milestones.md defers Snapshots to Milestone 4 and no Functional Requirement covers them.

Evidence

- Event-Model.md, Event Flow, Event Ordering and EV-07
- Milestones.md, Milestone 4

Resolution

Documentation correction.

Snapshot Created becomes optional within the synchronization flow until Milestone 4.

---

## C8 — Collector and Connector responsibilities overlap

The Glossary is declared authoritative and states that no document shall redefine its terminology.

The Glossary defines a Collector as the component responsible for retrieving information from External Systems.

The Connector Model assigns retrieval to the Connector and leaves normalization to the Collector.

Connector has no Glossary entry and no CRC card.

Evidence

- Glossary.md, Collector
- Connector-Model.md, Connector Responsibilities and Collector Responsibilities
- CRC-Cards.md

Resolution

Documentation correction.

The Glossary gains a Connector entry and the Collector definition is narrowed to normalization.

---

# Editorial Inconsistencies

## E1

Context.md omits Connectors from the information flow while Information-Architecture.md includes them.

---

## E2

Roadmap.md defines three milestones with different content than the five milestones defined in Milestones.md.

Roadmap.md describes itself as directional and should be marked as superseded by Milestones.md for milestone definitions.

---

## E3

Roadmap.md lists Environment observations within Milestone 1, reinforcing the ambiguity described in C5.

---

## E4

Open-Questions.md uses a second level heading for Integration while all sibling sections use first level headings.

---

## E5

The original checklist entry for ADR-004 referred to a logical grouping while the delivered record is titled Release Pack is the Central Business Concept.

The checklist has been reconciled to the delivered filenames.

---

## E6

Domain-Model.md states that a Deployment Unit equals an Application Version for Milestone 1, but no Functional Requirement references Deployment Unit.

Implementation may collapse the two behind an interface.

---

# Coverage Gaps

## G1

No requirement covers creating or managing Environments, although FR-010 to FR-013 presume they exist.

---

## G2

No requirement covers registering Applications or Application Versions, although FR-003, FR-011, FR-014 and FR-015 presume they exist.

---

## G3

No requirement covers deleting a Promotion Path or a Release Pack.

Backlog Epic 1 requires the former.

---

## G4

No requirement covers creating or maintaining validation Iterations.

FR-017 only displays them while Backlog Epic 2 requires maintaining them.

---

## G5

IA-02 requires User-Owned Information to be versioned by Tower.

No Functional Requirement and no Architectural Decision Record covers versioning, and the storage implications are significant.

---

## G6

The Vision states that developers should obtain answers within seconds.

NFR-023 and NFR-024 describe efficient retrieval without a target.

A soft budget is recommended.

---

# Governance

Contradictions C1 to C5 are resolved through ADR-005 to ADR-008.

Contradictions C6 to C8, all editorial inconsistencies and all coverage gaps are resolved through documentation corrections and additional requirements.

No documentation correction shall be applied until the proposed Architectural Decision Records have been accepted or rejected.
