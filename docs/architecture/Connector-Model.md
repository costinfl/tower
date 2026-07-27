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

Provides information related to development work.

Typical information includes:

- work items;
- issue identifiers;
- issue status;
- issue relationships.

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

Every Connector ultimately produces Observations.

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
