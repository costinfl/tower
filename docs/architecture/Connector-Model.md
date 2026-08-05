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

# How a Connector Is Verified

A Connector's claims are checked three ways, and each establishes something the others cannot (ADR-019).

**The conformance suite** holds every Connector in a category to what its service provider interface
promises: absence is not an error, a status is never normalised, a credential never reaches a message.
Extending it is how a Connector declares itself one of that category. It establishes that a Connector behaves
as the interface says, and nothing about the vendor.

**The vendor's own description** judges the canned responses a Connector is tested against. A fixture written
by hand carries the same beliefs as the Connector that reads it, so a test built on one can only confirm that
the two agree. The vendor's published OpenAPI description, sliced to the operations Tower reads and committed
under `specs/`, is the cheapest independent opinion available. Where a real response can be recorded instead
it is, because a recording outranks a description.

**A live read against a real system** is the only one that establishes anything about the system. A
description is not a server, and vendors' descriptions drift from their implementations. The live checks are
tracked in CHECKLIST.md and are never closed by an automated suite going green.

The order matters. A Connector can pass the first two and still be wrong about the vendor; that is the
residue the third exists to remove.

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

GitHub Issues and Jira are the two implementations. Neither is privileged: the SPI names no vendor, and
what a locator is — a repository for one, a site address for the other — is known only inside the
Connector that reads it.

GitHub Issues reads one issue per identifier rather than paging and filtering, because a filtered list cannot tell "this issue does not exist" apart from "it was not on
the page I read", and that difference is exactly what a reader needs. An identifier the tracker does
not know is omitted, never invented; an identifier from another tracker's scheme is omitted too,
rather than failing the whole read for one reference a team wrote before they bound anything.

Jira follows the same rules and differs where Jira does. Whether an item is finished comes from Jira's
own status category rather than the status name, because the site's administrator decides which of a
team's statuses count as done and Tower has no standing to decide it for them. A 404 means both "no
such issue" and "the credential cannot see it" — Jira answers the same way for each, deliberately, so
that a stranger cannot learn which keys are real — and Tower omits the item rather than claiming to
know which happened.

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

A run of a **deployment** pipeline that succeeded produces an Observation, on the footing ADR-006
established for manual entry: a fact from an identified source, carrying its provenance. A run that
failed, was aborted or finished in a state Tower does not recognise is reported and not recorded — it
is not evidence that anything reached an Environment.

A run of a **build** pipeline produces a candidate Application Version instead, proposed and stored
nowhere, on the footing ADR-014 established for a git ref. Source control and build jobs are two
sources of the same kind of proposal, so what they offer is merged into one list and each candidate
says which system offered it — ADR-020 put that as "the discovery screen gains a source rather than a
mode".

Each source fills only what it knows. A ref carries a branch, a tag and a commit and no build
identifier; a build run carries the identifier and no commit. Deriving either from the other would be
a guess, and the field is left absent instead — the same restraint ADR-014 applied when it refused to
invent a build identifier from a commit.

What such an Observation claims is narrower than what a Deployment Platform Connector claims, and
ADR-020 records the difference: a pipeline says what was done at an instant, a platform says what is
true now. Provenance and supersession keep the two apart without any special machinery — a later
platform reading overrides an earlier pipeline record, and every Observation shows where it came from.

The category's contribution is precision about *when* a deployment happened, which polling can only
approximate, and reach into Environments Tower has no credentials for but the CI system does.

---

## Artifact Repository Connector

Confirms that the binaries an Application Version names are where they should be (ADR-021).

Reads:

- whether an artifact exists at a composed coordinate;
- its immutable digest;
- when the repository received it.

Produces no Observations, and the reason is worth stating because the repository invites the opposite
conclusion. An image in a repository named `docker-prod` is a file in a folder whose name says "prod";
nothing about it says anything is running. Promotion between repositories is not promotion between
Environments, and folding the two would put versions into Environments on the strength of a copy.

Stores nothing it reads. The coordinate is composed from the Application Version — for a team that
tags an image and a chart with the version and the short commit, it is a function of what Tower
already holds — and the answer is shown beside what Tower holds rather than recorded.

One thing is stored, and it is not something this Connector read. A tag is mutable, so a document
prints the digest a **person accepted** after looking at one, exactly as ADR-018 requires for a work
item's title. Where the repository later reports different bytes under the same name, the difference
is shown and nothing is corrected — a handover already given to another team does not change because
somebody re-published an image. That comparison is the single most useful thing this category does:
a re-pushed tag is a fact a team almost never learns any other way.

The binding carries a template rather than a pattern, which is ADR-012's shape running the other way:
a version pattern extracts a version from a string the vendor produced, a coordinate template composes
a string the vendor will recognise from a version Tower holds.

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
Deployment Pipeline Run (successful)
        │
        ▼
Observation
```

```
Build Pipeline Run
        │
        ▼
Candidate Application Version
```

The last of those is not an Observation, and this section said otherwise until ADR-020. It read
"Pipeline Execution → Observation", undifferentiated, which would have recorded a build as though it
had put a version into an Environment. A build says what was produced; only a deployment says where
something went.

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
