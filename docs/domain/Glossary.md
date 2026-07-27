# Glossary

**Status:** Draft

**Owner:** Architecture

**Related:**

- ../vision/Vision.md
- ../vision/Principles.md
- Domain-Model.md

---

# Purpose

This document defines the ubiquitous language used throughout the Tower project.

Every architectural document, implementation, test and discussion shall use the terminology defined here.

If a new business concept is introduced, this glossary shall be updated before the concept is used elsewhere.

This document is the authoritative source for business terminology.

---

# Observation

An immutable fact collected from an external system at a specific point in time.

An Observation represents what Tower has seen.

Tower does not infer or modify Observations.

Examples include:

- an application version detected in an environment;
- a deployment detected in a Kubernetes namespace;
- a Git tag associated with an application version.

Observations are immutable.

---

# Application

A deployable software component that participates in one or more software releases.

Applications have independent development and version lifecycles.

Examples include:

- Customer API
- Orders API
- Authentication Service
- Web Frontend

---

# Application Version

A specific immutable version of an Application.

An Application Version uniquely identifies software intended for deployment.

Typical identifying attributes include:

- version
- branch
- tag
- commit identifier
- build identifier

Tower does not prescribe how external systems represent versions.

---

# Deployment Unit

The deployable representation observed inside an Environment.

For Milestone 1, a Deployment Unit corresponds directly to an Application Version.

Future milestones may extend this concept to include configuration, migrations or deployment metadata without changing the domain model.

---

# Environment

A logical deployment target where Applications execute.

Typical Environments include:

- Dev1
- Dev2
- SIT1
- SIT2
- UAT
- PreProd
- Production
- Hotfix

An Environment contains the currently observed Deployment Units.

---

# Promotion Path

A user-defined ordered sequence of Environments through which a Release Pack progresses.

Promotion Paths are static.

Tower records Promotion Paths but never executes promotions.

Example:

Regular

Dev1 → SIT1 → UAT → PreProd → Production

Example:

Hotfix

Dev-Hotfix → SIT → UAT → Production

---

# Lane

The visual representation of a Promotion Path.

A Lane communicates the intended progression of a Release Pack through software delivery.

Promotion Paths define structure.

Lanes define presentation.

---

# Release Pack

A logical grouping of Application Versions prepared for delivery together.

A Release Pack represents the developer's view of a software release.

A Release Pack may contain:

- multiple Applications;
- deployment instructions;
- release notes;
- validation iterations;
- handover information.

A Release Pack is independent of deployment status.

---

# Iteration

A validation cycle executed against a Release Pack.

Typical examples include:

- SIT Iteration
- UAT Iteration

Iterations provide evidence that a Release Pack has progressed through the software delivery lifecycle.

---

# Snapshot

An immutable representation of the observed state of one or more Environments at a specific point in time.

Snapshots preserve historical state.

Their implementation is intentionally unspecified.

---

# Handover

Developer-owned information required before a Release Pack is transferred to another team.

Examples include:

- deployment instructions;
- shell commands;
- database migrations;
- rollback notes;
- validation notes;
- operational considerations.

Tower assists in generating Handover information but does not execute it.

---

# Intent

Developer-defined information that cannot be automatically observed.

Intent complements Observations.

Examples include:

- Release Pack membership;
- Promotion Path selection;
- deployment notes;
- release objectives.

---

# Collector

A Tower component responsible for retrieving information from external systems.

Collectors observe.

Collectors never modify external systems.

---

# Viewer

The Tower user interface.

The Viewer presents information collected and correlated by Tower.

The Viewer never communicates directly with external engineering systems.

---

# Store

The persistent repository of Tower domain information.

The Store maintains Tower's canonical model.

The implementation technology is intentionally unspecified.

---

# External System

Any system integrated with Tower that remains authoritative for its own data.

Examples include:

- Source Control
- Issue Tracking
- CI/CD
- Kubernetes
- Documentation Platforms

Tower reads information from External Systems.

Tower does not own them.

---

# Canonical Model

The internal normalized representation produced after Tower correlates information collected from External Systems.

Generated documentation and visualizations originate from the Canonical Model.

---

# Ubiquitous Language

The shared vocabulary used by developers, architects and contributors.

This Glossary is the authoritative definition of that language.

No document shall redefine terminology established here.
