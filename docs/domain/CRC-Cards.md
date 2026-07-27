# CRC Cards

**Status:** Draft

**Owner:** Architecture

**Related:**

- Glossary.md
- Domain-Model.md
- Event-Model.md

---

# Purpose

This document defines the Class–Responsibility–Collaboration (CRC) model for the Tower domain.

CRC cards identify business responsibilities and collaborations independently of implementation.

They are intended to validate the domain model before implementation begins.

---

# Release Pack

## Responsibilities

- Group Application Versions into a logical release.
- Own release metadata.
- Own Handover information.
- Own validation Iterations.
- Reference the Promotion Path it follows.
- Provide the business representation of a software release.

## Collaborators

- Application Version
- Promotion Path
- Iteration
- Handover

---

# Application

## Responsibilities

- Represent deployable software.
- Maintain identity across versions.
- Provide the logical owner of Application Versions.

## Collaborators

- Application Version

---

# Application Version

## Responsibilities

- Represent one immutable software version.
- Associate with an Application.
- Participate in Release Packs.
- Be observed inside Environments.

## Collaborators

- Application
- Deployment Unit
- Release Pack
- Observation

---

# Deployment Unit

## Responsibilities

- Represent what is deployed.
- Associate a deployed instance with an Application Version.
- Abstract future deployment metadata.

## Collaborators

- Application Version
- Environment

---

# Environment

## Responsibilities

- Represent a deployment destination.
- Maintain the current observed deployment state.
- Produce Snapshots.
- Accumulate Observations.
- Belong to a Promotion Path.

## Collaborators

- Deployment Unit
- Snapshot
- Observation
- Promotion Path

---

# Promotion Path

## Responsibilities

- Define the ordered sequence of Environments.
- Represent the expected delivery workflow.
- Remain static throughout its lifetime.

## Collaborators

- Environment
- Release Pack

---

# Lane

## Responsibilities

- Present a Promotion Path visually.
- Communicate release progression.
- Improve operational understanding.

## Collaborators

- Promotion Path

---

# Iteration

## Responsibilities

- Represent a validation cycle.
- Record validation progression.
- Associate validation activities with a Release Pack.

## Collaborators

- Release Pack

---

# Snapshot

## Responsibilities

- Preserve an immutable view of Environment state.
- Enable historical comparison.
- Capture deployment state at a point in time.

## Collaborators

- Environment
- Observation

---

# Observation

## Responsibilities

- Represent immutable facts.
- Preserve externally observed information.
- Record observation timestamps.
- Provide the foundation of Tower's knowledge.

## Collaborators

- Environment
- Application Version
- Snapshot
- Collector

---

# Intent

## Responsibilities

- Represent developer-defined information.
- Complement Observations.
- Describe release planning decisions.

## Collaborators

- Release Pack

---

# Handover

## Responsibilities

- Collect developer-owned deployment information.
- Describe deployment instructions.
- Describe rollback procedures.
- Describe operational considerations.
- Support documentation generation.

## Collaborators

- Release Pack

---

# Collector

## Responsibilities

- Retrieve information from External Systems.
- Convert external information into Observations.
- Never modify external systems.

## Collaborators

- External System
- Observation

---

# Viewer

## Responsibilities

- Present the Canonical Model.
- Visualize Release Packs.
- Visualize Promotion Paths.
- Visualize Environment state.

## Collaborators

- Store

---

# Store

## Responsibilities

- Persist Tower-owned information.
- Maintain the Canonical Model.
- Provide information to the Viewer.

## Collaborators

- Viewer
- Collector

---

# External System

## Responsibilities

- Own authoritative engineering information.
- Provide data to Collectors.

## Collaborators

- Collector

---

# Collaboration Summary

```
Collector
    │
    ▼
Observation
    │
    ▼
Environment
    │
    ▼
Snapshot

Application
    │
    ▼
Application Version
    │
    ▼
Deployment Unit
    │
    ▼
Environment

Release Pack
    │
    ├── Application Version
    ├── Iteration
    ├── Handover
    └── Promotion Path

Viewer
    │
    ▼
Store
```

---

# Design Validation

The CRC model satisfies the following architectural goals.

- Business responsibilities remain independent of implementation.
- Every primary domain concept owns clear responsibilities.
- Collaborations remain minimal and explicit.
- External systems remain isolated through Collectors.
- The Viewer never communicates directly with External Systems.
- Release Packs remain business concepts rather than deployment concepts.
- Observations remain the atomic unit of information.
