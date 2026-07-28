# Milestones

**Status:** Draft

**Owner:** Product / Architecture

---

# Purpose

This document defines the incremental delivery strategy for Tower.

Milestones are organized around business value rather than technical implementation.

Each milestone should produce a usable product increment.

Implementation planning is expected to decompose these milestones into epics, user stories and development tasks.

---

# Guiding Principles

Milestones shall:

- deliver business value;
- remain independently demonstrable;
- build upon previous milestones;
- avoid architectural rework;
- preserve the Canonical Model as the single source of truth.

---

# Milestone 1 — Release Visibility

## Goal

Provide complete visibility into Release Packs and Environment contents.

## Scope

- Promotion Paths
- Environments
- Release Packs
- Application Versions
- Handover information
- Validation Iterations
- Environment visualization
- Manual Observation management
- Documentation generation

## Success Criteria

The development team can answer:

- Which Release Packs exist?
- Which Applications belong to a Release Pack?
- Which Application Version is deployed in every Environment?
- Which Promotion Path is followed?
- What documentation accompanies the release?

This milestone delivers the minimum viable product.

---

# Milestone 2 — Automated Synchronization

## Goal

Replace manual Environment updates with automatic synchronization.

## Scope

- Connector framework
- Source Control Connector
- Deployment Platform Connector
- Observation synchronization
- Canonical Model updates

## Success Criteria

Environment contents remain synchronized with External Systems.

Developers no longer maintain deployment information manually.

---

# Milestone 3 — Documentation Automation

## Goal

Extend generated documentation with templates and additional output formats.

Generation from the Canonical Model itself is delivered in Milestone 1 (Backlog Epic 4, FR-024 to FR-030). Milestone 3 covers what Milestone 1 deliberately left out: customisable templates and formats beyond Markdown (OQ-009, OQ-010).

## Scope

- Release documentation
- Deployment instructions
- Validation summaries
- Handover generation
- Export capabilities

## Success Criteria

Release documentation is reproducible and requires minimal manual editing.

---

# Milestone 4 — Historical Visibility

## Goal

Provide insight into deployment history.

Snapshots arrive here. Event-Model.md ends its synchronization flow in Snapshot Created, which should be read as the eventual shape of that flow rather than a Milestone 1 obligation: until Milestone 4, Observations are retained individually and Environment state is derived from them directly.

## Scope

- Snapshots
- Environment history
- Release progression history
- Historical comparisons

## Success Criteria

Developers can determine:

- what changed;
- when it changed;
- which Release Pack introduced the change.

---

# Milestone 5 — Operational Dashboard

## Goal

Provide a unified operational view of software delivery.

## Scope

- Cross-release dashboard
- Promotion overview
- Environment overview
- Active Release Packs
- Summary indicators

## Success Criteria

Tower becomes the primary command center for release visibility.

---

# Milestone Dependencies

```
Milestone 1
        │
        ▼
Milestone 2
        │
        ▼
Milestone 3
        │
        ▼
Milestone 4
        │
        ▼
Milestone 5
```

Each milestone extends the previous one.

No milestone should invalidate architectural decisions established in earlier phases.

---

# Definition of Done

A milestone is considered complete when:

- all planned capabilities have been implemented;
- documentation has been updated;
- architectural principles remain satisfied;
- no ADR has been violated;
- acceptance criteria have been demonstrated.

---

# Planning Guidance

Future implementation planning should decompose milestones into:

1. Epics
2. User Stories
3. Technical Tasks
4. Test Scenarios
5. Release Planning

The implementation plan should preserve the order defined in this document unless a new Architectural Decision Record explicitly justifies a change.
