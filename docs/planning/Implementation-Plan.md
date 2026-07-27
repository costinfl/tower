# Implementation Plan

**Status:** Draft

**Owner:** Architecture

**Related:**

- Consistency-Report.md
- Milestones.md
- Backlog.md
- ../architecture/Functional-Requirements.md
- ../adr/ADR-009-Local-First-Deployment.md
- ../adr/ADR-010-Tower-Owned-Data-Is-Portable.md

---

# Purpose

This document translates the accepted architecture into an executable implementation plan.

It defines the repository structure, the package structure, the implementation milestones and the initial GitHub issues.

It introduces no new business concepts.

Every structural decision in this document traces to an existing requirement, business rule or Architectural Decision Record.

---

# Technology Selection

The architectural documentation deliberately avoids technology choices.

The following selections are implementation decisions and do not alter the business architecture.

| Concern | Selection |
| --- | --- |
| Language | Java 21 |
| Application framework | Spring Boot |
| Build | Maven, multi-module |
| Persistence | Embedded H2 in file mode, PostgreSQL compatibility mode, Flyway migrations |
| Secret handling | Jasypt, encrypted configuration file |
| User interface | React with TypeScript and Vite |
| Architecture testing | ArchUnit |

The domain module contains no framework dependencies.

This keeps NFR-004, NFR-005 and NFR-006 enforceable and makes the framework selection reversible.

---

# Deployment Model

Tower initially runs on each developer's machine, as decided in ADR-009.

A developer clones the repository, builds the project and runs the application.

No database server and no container runtime are prerequisites.

The application binds to the local loopback interface only and introduces no authentication in Milestone 1.

Application data lives in the .tower directory within the user's home directory.

That directory holds the embedded database file and the configuration file, so application data survives rebuilds and cannot be committed.

PostgreSQL and centrally hosted deployment are deferred.

Three constraints limit the cost of that deferral.

The embedded database runs in PostgreSQL compatibility mode.

Migrations avoid vendor-specific syntax wherever practical.

All persistence remains behind the outbound ports, so a future change of database is contained within the persistence module.

Because each developer holds an isolated database, Tower-owned information is portable through export and import, as decided in ADR-010.

---

# Credential Handling

NFR-028 states that credentials and authentication mechanisms are implementation concerns.

This section therefore records a design rather than an architectural decision.

Because each developer runs their own instance, each developer supplies their own credentials for the External Systems their team uses.

The following rules apply.

Configuration is stored in the .tower directory and never in src/main/resources.

Connector credentials are encrypted at rest using Jasypt, with the master key supplied through an environment variable.

The configuration file is written with owner-only permissions.

Credentials are write-only through the API.

Once saved, the user interface displays a masked indication that a credential is configured and the stored value is never returned.

Credentials never appear in logs, in error messages, in generated documentation or in export files.

The repository ignore rules exclude configuration and database artifacts as a defence in depth measure, even though both live outside the working tree.

Connector modules obtain credentials through the credentials outbound port and never learn where secrets are stored.

Connection testing is read-only, consistent with ADR-001, CM-01 and FR-036.

## Timing

Credential handling belongs to Milestone 2 rather than Milestone 1.

Milestone 1 runs the Manual Collector only and therefore authenticates against nothing, so a settings screen would have nothing to configure.

Milestone 0 establishes the .tower directory and configuration loading, because the database file lives there.

Milestone 2 adds encryption, the credentials port and the settings screen alongside the Connectors that need them.

---

# Repository Structure

```
tower/
├── docs/
├── pom.xml
│
├── tower-domain/
├── tower-application/
├── tower-collector/
├── tower-connector-api/
├── tower-connector-manual/
├── tower-connector-git/
├── tower-connector-k8s/
├── tower-persistence/
├── tower-config/
├── tower-docgen/
├── tower-portability/
├── tower-api/
└── tower-web/
```

---

# Module Responsibilities

## tower-domain

Contains the business model defined in Domain-Model.md.

Packages correspond to domain concepts.

```
releasepack/
application/
environment/
promotionpath/
observation/
handover/
iteration/
state/
event/
```

This module has no dependency outside the Java standard library.

No framework annotation, persistence type or transport type may appear here.

---

## tower-application

Contains use cases and ports.

```
port/in/     use case interfaces
port/out/    repository and collector interfaces
service/     use case implementations
```

Dependencies point inward toward the domain.

---

## tower-collector

Converts source payloads into Observations.

Responsible for validation, timestamp preservation and source reference preservation.

---

## tower-connector-api

Defines the Connector service provider interface.

One interface per Connector category, satisfying CM-02.

Every operation is read-only, satisfying CM-01 and ADR-001.

---

## tower-connector-manual

Implements the Manual Collector defined in ADR-006.

Delivered in Milestone 1.

---

## tower-connector-git and tower-connector-k8s

Source Control and Deployment Platform Connectors.

Delivered in Milestone 2.

---

## tower-persistence

Implements the outbound ports.

Contains the database schema and Flyway migrations.

Uses an embedded H2 database in file mode located in the .tower directory.

The database runs in PostgreSQL compatibility mode and migrations avoid vendor-specific syntax, so that the deferred move to PostgreSQL stays contained within this module.

No other module references H2 types.

---

## tower-config

Owns configuration loading and secret handling.

Reads configuration from the config file in the .tower directory.

Configuration is never placed in src/main/resources.

Connector credentials are encrypted at rest using Jasypt, with the master key supplied through an environment variable.

The configuration file is written with owner-only permissions.

This module implements the credentials outbound port, so connector modules never touch storage and never learn where secrets live.

This module is where integration with a configuration server would later be introduced, as noted in ADR-009.

---

## tower-docgen

Generates documentation from the Canonical Model.

Reads through the application layer only.

---

## tower-portability

Implements export and import of Tower-owned information, as defined in ADR-010.

Reads through the application ports and writes versioned JSON.

Preserves Observation provenance across transfer and never exports credentials or configuration.

---

## tower-api

The Spring Boot application.

Contains REST endpoints, wiring and security configuration.

---

## tower-web

The Viewer.

Communicates exclusively with tower-api.

---

# Enforced Boundaries

The following rules are enforced by automated architecture tests rather than by review.

| Rule | Source |
| --- | --- |
| tower-domain depends only on the Java standard library | NFR-004, NFR-006 |
| No vendor type escapes a connector module | CM-03, ADR-003 |
| tower-web and tower-api never reference connector modules | CRC-Cards.md, Viewer responsibilities |
| Connector modules expose no write operation | ADR-001, CM-01, FR-036 |
| The Viewer and docgen read only from the Canonical Model | IA, FR-038 |
| Only tower-config reads or writes credential material | NFR-028 |
| tower-portability never exports credentials or configuration | ADR-010 |
| No module outside tower-persistence references H2 types | ADR-009 |

Every domain type and use case carries a comment referencing the requirement it satisfies.

This satisfies NFR-030.

---

# Milestones

Milestones 1 to 5 are defined in Milestones.md and are preserved without modification.

Milestone 0 is an implementation artifact.

It adds no product scope and does not amend Milestones.md.

| Milestone | Content | Backlog epics |
| --- | --- | --- |
| M0 Foundations | Repository skeleton, continuous integration, enforced boundaries, persistence, decision records | — |
| M1 Release Visibility | Promotion Paths, Release Packs, Environment visibility, documentation generation, portability | 1, 2, 3, 4 and Epic 8 |
| M2 Automated Synchronization | Connector framework, Source Control and Deployment Platform Connectors | 5 |
| M3 Documentation Automation | Templates and additional export formats | 4 extended |
| M4 Historical Visibility | Snapshots, comparison, progression history | 6 |
| M5 Operational Dashboard | Cross-release dashboard and overviews | 7 |

---

# Issue Labels

```
type:infra      type:feature      type:docs      type:arch

epic:promotion-paths      epic:release-packs      epic:environments
epic:docgen               epic:connectors         epic:portability

milestone:M0 … milestone:M5
```

---

# Milestone 0 Issues

| Issue | Title | Labels | Depends on |
| --- | --- | --- | --- |
| 1 | Scaffold Maven multi-module skeleton and module boundaries | type:infra | — |
| 2 | Add architecture tests enforcing documented boundaries | type:arch | 1 |
| 3 | Embedded H2 database in file mode with Flyway migrations | type:infra | 1, 36 |
| 4 | Spring Boot API skeleton with health endpoint and error contract | type:infra | 1, 3 |
| 5 | React and TypeScript web skeleton with API client | type:infra | 4 |
| 6 | Continuous integration: build, tests, architecture tests, frontend build | type:infra | 1, 5 |
| 7 | Documentation reconciliation for C6 to C8, E1 to E6, G1 to G6 and Backlog Epic 8 | type:docs | 8 |
| 8 | Accept or reject ADR-005 to ADR-010 | type:arch | — |
| 36 | Application data directory and configuration loading | type:infra | 1 |

Issue 3 covers PostgreSQL compatibility mode and dialect-neutral migrations, as required by ADR-009.

Issue 36 establishes the .tower directory and configuration loading.

It precedes issue 3 because the database file lives in that directory.

Issue 8 gates issue 7 and the whole of Milestone 1.

ADR-005 to ADR-008 determine Environment cardinality, observation sourcing, Promotion Path mutability and state derivation.

ADR-009 and ADR-010 determine storage, deployment topology and data portability.

All six shape the Milestone 1 schema.

No implementation issue may start before they are resolved.

---

# Milestone 1 Issues

## Epic 1 — Promotion Paths

| Issue | Title | Traceability |
| --- | --- | --- |
| 9 | Environment registry with Stage classification | G1, ADR-008 |
| 10 | Promotion Path aggregate with ordered Environment references | ADR-005 |
| 11 | Promotion Path versioning and archival | ADR-007, G3 |
| 12 | Promotion Path REST API | FR-007, FR-008 |
| 13 | Lane visualization | FR-009 |

---

## Epic 2 — Release Packs

| Issue | Title | Traceability |
| --- | --- | --- |
| 14 | Application and Application Version registry | G2, BR-01 |
| 15 | Release Pack aggregate with create, edit and delete | FR-001, FR-002, G3 |
| 16 | Assign a Promotion Path version to a Release Pack | FR-005, ADR-007 |
| 17 | Associate and remove Application Versions | FR-003, FR-004 |
| 18 | Handover information authoring and persistence | FR-006 |
| 19 | Validation Iteration management | G4 |
| 20 | Release Pack REST API and screens | FR-014, FR-015, FR-017, FR-018 |

---

## Epic 3 — Environment Visibility

| Issue | Title | Traceability |
| --- | --- | --- |
| 21 | Observation model with immutability and provenance | FR-021, FR-022, FR-023, BR-02 |
| 22 | Manual Collector implementing the Connector interface | ADR-006 |
| 23 | Canonical Model projection of current Environment state | FR-010 to FR-013 |
| 24 | Derived Release Pack state from Environment Stage | ADR-008, FR-016, FR-034 |
| 25 | Environment views showing source and timestamp | FR-012, FR-013, NFR-013 |

---

## Epic 4 — Documentation Generation

| Issue | Title | Traceability |
| --- | --- | --- |
| 26 | Documentation engine reading from the Canonical Model | FR-024, FR-032, FR-038 |
| 27 | Release Pack, Handover and validation templates | FR-025 to FR-030 |
| 28 | Markdown export | FR-024, OQ-009 |
| 29 | Traceability metadata in generated output | FR-031, FR-032, NFR-011 |

---

## Epic 8 — Portability

| Issue | Title | Traceability |
| --- | --- | --- |
| 37 | Export Tower-owned information as versioned JSON | ADR-010, IA |
| 38 | Import with explicit conflict resolution | ADR-010 |
| 39 | Observation provenance preservation across import | ADR-002, ADR-006, ADR-010 |
| 40 | Export and import screens | ADR-010 |

This epic exists because ADR-009 leaves each developer with an isolated database.

It is the interim mechanism for sharing Release Packs, Promotion Paths and Handover information across a team.

Epic 8 is new.

Backlog.md defines Epics 1 to 7 and does not yet contain it.

Adding it to the Backlog is part of the documentation reconciliation in issue 7, and depends on ADR-010 being accepted.

---

# Later Milestones

| Issue | Title | Milestone |
| --- | --- | --- |
| 30 | Epic: Connector framework and interface hardening | M2 |
| 31 | Epic: Source Control Connector | M2 |
| 32 | Epic: Deployment Platform Connector | M2 |
| 41 | Encrypted credential storage and credentials outbound port | M2 |
| 42 | Settings screen with masked write-only credential fields | M2 |
| 43 | Read-only connection test for each configured Connector | M2 |
| 33 | Epic: Documentation templates and export formats | M3 |
| 34 | Epic: Snapshots and historical comparison, resolving C7 | M4 |
| 35 | Epic: Operational dashboard | M5 |

Later milestones remain at epic level deliberately.

Decomposing them now would anticipate decisions that Milestone 1 will inform.

---

# Issue Template

Every issue shall contain the following sections.

- Goal
- Traceability, listing requirement, business rule and decision record identifiers
- Acceptance criteria
- Out of scope
- Dependencies

---

# Verification

## Per issue

Unit tests in tower-domain cover every business rule cited by the issue.

The architecture test suite passes, so boundary violations fail the build rather than review.

---

## Milestone 0 exit

A clean clone builds, verifies and runs with no database installation and no container runtime.

The .tower directory is created on first run and contains the database file and the configuration file.

The repository working tree remains clean after a run, confirming that no application data is written into it.

The application is reachable on the loopback interface and refuses connections from other hosts.

Continuous integration passes.

ADR-005 to ADR-010 carry a status other than Proposed.

---

## Milestone 1 exit

Milestone 1 is verified by executing the documented Scenarios end to end.

1. Scenario 1. Create the Regular Promotion Path, create a Release Pack containing two Applications, assign the path, record Handover information, observe versions in Dev1 and then UAT, and confirm that the derived state progresses from Planned through Development to Validation.

2. Scenario 3. Create the Hotfix Promotion Path reusing UAT and Production, confirming ADR-005. Observe progression to Production and confirm the Release Pack reaches the Production state without a pre-production Environment, confirming ADR-008.

3. Scenario 2. Confirm that two Release Packs progress independently with separate Handover information and Iterations.

4. Scenario 7. Observe an Application Version that does not match the intended Release Pack and confirm that Tower reports the drift without judging it.

5. Scenario 8. Generate release documentation, confirm that every value traces to an Observation or to User-Owned Information, and confirm that regeneration produces identical output.

6. Read-only verification. Confirm that no Milestone 1 code path writes to any External System.

Portability is verified separately, using two instances.

7. Export a Release Pack from the first instance and import it into the second. Confirm that the Promotion Path version, Handover information and Iterations arrive intact.

8. Import the same file a second time. Confirm that conflict resolution is offered and that no duplicate Observations are created.

9. Confirm that imported Observations display their original source and original timestamp rather than the time of import.

10. Inspect an export file and confirm that it contains no credentials and no configuration.

---

## Milestone 2 exit

Save a credential, restart the application and confirm that the Connector still authenticates.

Confirm that the configuration file contains no plaintext secret and carries owner-only permissions.

Confirm that the API never returns a stored credential and that no credential appears in logs or in generated documentation.

Confirm that connection testing performs no write operation against any External System.

---

## Performance

Environment and Release Pack views shall return within one second at the ninety-fifth percentile with fifty Environments and two hundred Release Packs.

This provides the measurable target identified as gap G6.

---

# Governance

This plan implements the architecture.

It does not amend it.

Any implementation decision that would contradict an existing Architectural Decision Record requires a new Architectural Decision Record, as required by NFR-030.
