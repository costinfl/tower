# Domain Model

**Status:** Draft

**Owner:** Architecture

**Related:**

- Glossary.md
- CRC-Cards.md
- Event-Model.md
- ../vision/Vision.md

---

# Purpose

This document defines the canonical business domain of Tower.

The domain model describes business concepts and their relationships.

It intentionally avoids implementation details such as databases, programming languages, APIs or user interface concerns.

The Domain Model is the foundation upon which every architectural decision is built.

---

# Design Philosophy

Tower models software delivery from the perspective of development teams.

The domain is intentionally small.

Every concept included in the model must directly contribute to answering one or more of Tower's core questions.

The model favors clarity over completeness.

---

# Primary Domain Objects

The following concepts constitute the core business domain.

- Release Pack
- Application
- Application Version
- Deployment Unit
- Environment
- Promotion Path
- Lane
- Iteration
- Snapshot
- Handover
- Observation
- Intent

No additional primary business concepts shall be introduced without updating this document.

---

# Domain Relationships

## Release Pack

A Release Pack groups together one or more Application Versions that are intended to be delivered together.

A Release Pack:

- contains Applications;
- references Application Versions;
- owns Handover information;
- owns Iterations;
- follows one Promotion Path.

A Release Pack does not own deployment state.

---

## Application

An Application represents deployable software.

An Application:

- has many Application Versions;
- may belong to multiple Release Packs over time;
- may exist simultaneously in multiple Environments.

---

## Application Version

An Application Version represents one immutable software version.

An Application Version:

- belongs to one Application;
- may be deployed into multiple Environments;
- may participate in multiple Snapshots;
- may belong to one or more Release Packs.

Tower never modifies an Application Version.

---

## Deployment Unit

A Deployment Unit represents what is actually deployed.

For Milestone 1:

Deployment Unit == Application Version.

The abstraction exists to support future evolution without changing the business model.

---

## Environment

An Environment represents a deployment destination.

An Environment:

- belongs to one Promotion Path;
- contains Deployment Units;
- produces Snapshots;
- accumulates Observations.

An Environment never owns Applications.

It owns only the currently observed deployment state.

---

## Promotion Path

A Promotion Path defines an ordered sequence of Environments.

Examples include:

Regular

Dev1 → SIT1 → UAT → PreProd → Production

Hotfix

Dev-Hotfix → SIT → UAT → Production

Promotion Paths are static.

Release Packs move through Promotion Paths.

Promotion Paths never move.

---

## Lane

A Lane is the visual representation of a Promotion Path.

A Lane contains no business rules.

Its purpose is presentation.

---

## Iteration

An Iteration represents a validation cycle performed against a Release Pack.

Typical examples:

- SIT Iteration 1
- SIT Iteration 2
- UAT Iteration

Iterations provide traceability between Release Packs and validation activities.

---

## Snapshot

A Snapshot captures the observed state of one or more Environments at a specific moment.

A Snapshot is immutable.

Snapshots provide historical context without changing previous observations.

---

## Observation

Observation is the atomic fact within the Tower domain.

Everything Tower knows originates from Observations.

Observations are:

- immutable;
- timestamped;
- read-only;
- externally sourced.

Tower never edits Observations.

---

## Intent

Intent represents information explicitly provided by developers.

Intent complements Observation.

Examples include:

- Release Pack membership;
- deployment objectives;
- promotion path selection;
- release notes.

Intent is owned by Tower.

---

## Handover

Handover represents the information prepared by developers before a Release Pack is transferred to another team.

Typical contents include:

- deployment instructions;
- shell commands;
- database migrations;
- rollback procedures;
- operational notes.

---

# Canonical Relationships

```
Application
    │
    ├── has many
    ▼
Application Version
    │
    ├── represented as
    ▼
Deployment Unit
    │
    ├── observed in
    ▼
Environment
    │
    ├── belongs to
    ▼
Promotion Path
```

```
Release Pack
    │
    ├── contains
    ▼
Application Version

Release Pack
    │
    ├── owns
    ▼
Iteration

Release Pack
    │
    ├── owns
    ▼
Handover
```

```
Environment
    │
    ├── produces
    ▼
Snapshot

Environment
    │
    ├── accumulates
    ▼
Observation
```

---

# Business Rules

The following rules define the current domain.

BR-01

Application Versions are immutable.

---

BR-02

Observations are immutable.

---

BR-03

Promotion Paths are static.

---

BR-04

Release Packs progress through Promotion Paths.

Promotion Paths do not change as a consequence of Release Pack movement.

---

BR-05

Tower never executes deployments.

Tower only observes deployments.

---

BR-06

Tower never owns External Systems.

Tower only correlates information obtained from them.

---

BR-07

Tower-generated documentation originates from the Canonical Model.

Documentation is not the source of truth.

---

# Out of Scope

The Domain Model intentionally excludes:

- databases;
- APIs;
- message queues;
- REST endpoints;
- programming languages;
- frameworks;
- deployment automation;
- infrastructure management.

These belong to later architectural or implementation phases.
