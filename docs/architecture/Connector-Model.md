# Connector Model

**Status:** Draft

**Owner:** Architecture

**Related:**

- Context.md
- ../domain/Domain-Model.md
- Information-Architecture.md

---

# Purpose

This document defines how Tower communicates with External Systems.

Tower integrates through Connectors.

Connectors isolate vendor-specific implementations from the business domain.

The Domain Model must remain independent of every external product.

---

# Design Principles

The Connector Model follows these principles.

- Vendor neutrality.
- Read-only integration.
- Loose coupling.
- Technology independence.
- One responsibility per Connector.

A Connector is responsible only for retrieving information.

A Connector never changes External Systems.

---

# Connector Architecture

```
External System
        │
        ▼
Connector
        │
        ▼
Collector
        │
        ▼
Observation
        │
        ▼
Canonical Model
```

Connectors communicate with External Systems.

Collectors normalize retrieved information into Observations.

The Canonical Model never depends on vendor-specific representations.

---

# Connector Responsibilities

A Connector shall:

- communicate with one category of External System;
- retrieve information;
- translate vendor-specific responses;
- report synchronization failures;
- remain read-only.

A Connector shall not:

- modify external data;
- execute deployments;
- create work items;
- update documentation;
- trigger pipelines.

---

# Collector Responsibilities

Collectors receive information from Connectors.

Collectors are responsible for:

- validating retrieved information;
- converting external information into Observations;
- preserving timestamps;
- preserving source references;
- forwarding normalized information to the Canonical Model.

Collectors do not communicate directly with users.

---

# Connector Categories

## Source Control Connector

Provides information related to source code.

Typical information includes:

- repositories;
- branches;
- tags;
- commits;
- release branches.

---

## Issue Tracking Connector

Resolves work item references a Release Pack already carries (ADR-018).

Reads:

- issue identifiers;
- issue titles;
- issue status, as the tracker's own word for it;
- the address a reader can follow.

Produces no Observations. Tower does not own work items (Product-Boundaries.md): a reference is Intent, and
what this Connector returns is shown beside it and stored nowhere.

Issue relationships are out of scope. They are the tracker's model of the tracker's own domain, and
importing them would make Tower a second issue tracker.

The tracker is bound once, per Connector, rather than per Environment or Application. A work item
belongs to a release rather than to one Application, and a team has one tracker, so there is no Tower
concept on the left of this binding — the one place ADR-012's shape does not fit, recorded in ADR-018
rather than left looking like an oversight.

GitHub Issues is the first implementation. It reads one issue per identifier rather than paging and
filtering, because a filtered list cannot tell "this issue does not exist" apart from "it was not on
the page I read", and that difference is exactly what a reader needs. An identifier the tracker does
not know is omitted, never invented; an identifier from another tracker's scheme is omitted too,
rather than failing the whole read for one reference a team wrote before they bound anything.

---

## Documentation Connector

Provides information referenced by Release Packs.

Typical information includes:

- documentation pages;
- deployment guides;
- validation reports.

---

## CI/CD Connector

Provides information about build and delivery pipelines.

Typical information includes:

- pipeline executions;
- build identifiers;
- build status;
- produced artifacts.

---

## Deployment Platform Connector

Provides deployment observations.

Typical information includes:

- deployed Application Versions;
- deployment timestamps;
- deployment targets;
- Environment contents.

---

# Synchronization

Synchronization is the process by which Connectors retrieve information.

Synchronization produces Observations.

Synchronization never modifies External Systems.

Synchronization may occur:

- on demand;
- periodically;
- by future implementation mechanisms.

The synchronization strategy is outside the scope of this document.

---

# Observation Mapping

A Connector whose subject is a running system produces Observations.

This originally read "every Connector ultimately produces Observations". Building the Issue Tracking
Connector showed that it does not hold, and ADR-018 records why: an Observation is an Application Version
seen in an Environment, and a work item has no Environment, no Application and no Application Version.
Forcing one into that shape would have meant a second kind of Observation sharing a name with the first and
satisfying none of the same derivations.

The omission was already visible here. The examples below map a git tag, a deployment and a pipeline
execution; there has never been one for a work item.

A Connector reading a record in somebody else's database produces no Observations. It resolves references
a developer already stated, which are User-Owned Information — the second origin IA-05 has always allowed.

Example:

```
Git Tag
        │
        ▼
Application Version Observed
```

```
Deployment
        │
        ▼
Deployment Unit Observed
```

```
Pipeline Execution
        │
        ▼
Observation
```

Vendor-specific data never enters the Domain Model directly.

---

# Failure Handling

Connector failures shall not invalidate the Canonical Model.

If synchronization fails:

- previous Observations remain valid;
- synchronization status shall be reported;
- incomplete synchronization shall not corrupt existing data.

Recovery strategies belong to implementation.

---

# Extensibility

New Connectors shall be introduced without changing:

- the Domain Model;
- Release Packs;
- Promotion Paths;
- Environment definitions.

Only the Connector layer should require extension.

---

# Architectural Constraints

CM-01

Every Connector is read-only.

---

CM-02

Every Connector communicates with exactly one category of External System.

---

CM-03

Vendor-specific concepts terminate at the Connector boundary.

---

CM-04

Collectors produce only normalized Observations.

---

CM-05

The Canonical Model never depends on vendor APIs.

---

# Success Criteria

The Connector Architecture is successful when:

- replacing one vendor does not change the Domain Model;
- new Connectors can be added independently;
- all business concepts remain vendor neutral;
- synchronization remains isolated from business logic.
