# ADR-009 — Local-First Deployment

**Status**

Proposed

---

## Context

No document in the Tower documentation set addresses where Tower runs.

The Vision, the Architecture and the Milestones describe what Tower does and what it owns, but deployment and hosting are absent.

Until a dedicated environment is provisioned, Tower will run on each developer's machine.

A developer clones the repository, builds the project and runs the application locally.

This arrangement is temporary but it has consequences that must be recorded rather than discovered.

Three consequences matter.

The first concerns storage.

A developer running the application locally should not be required to install and operate a database server.

The second concerns authentication.

An application running on a single developer's machine has no meaningful user population to authenticate.

The third concerns information ownership.

The Vision describes Tower as the team's operational reference during release preparation.

If every developer runs an isolated instance, then Tower-owned information such as Release Packs and Promotion Paths exists only on the machine that created it.

Tower would be a developer's reference rather than a team's reference.

---

## Decision

Tower shall be delivered as a single-instance application that runs locally on a developer's machine.

The application shall bind to the local loopback interface only.

No authentication mechanism shall be introduced in Milestone 1.

This is justified solely by the loopback binding.

Exposing the application on any other interface requires authentication to be introduced first, and this record exists so that the dependency is understood rather than assumed.

Application data shall be stored in a directory named .tower within the user's home directory.

This directory contains the database and the configuration file.

It sits outside the repository working tree, so application data survives rebuilds and cannot be committed by accident.

Persistence shall use an embedded database in file mode.

PostgreSQL and centrally hosted deployment are deferred.

To limit the cost of that deferral, three constraints apply.

The embedded database shall run in PostgreSQL compatibility mode.

Database migrations shall avoid vendor-specific syntax wherever practical.

All persistence shall remain behind the outbound ports defined in the application layer, so that a future change of database is contained within the persistence module.

Tower-owned information is therefore held per developer.

This limitation is accepted for the current phase.

ADR-010 defines the interim mechanism for moving Tower-owned information between instances.

The identity recorded against a manual Observation, as required by ADR-006, shall resolve to the local operating system user or to a configured display name until authentication exists.

Centralized configuration through a configuration server is recognized as the forward path once a dedicated environment exists.

It is not built now.

---

## Consequences

Positive

- a developer can clone, build and run Tower without installing a database or a container runtime;
- application data survives rebuilds because it lives outside the repository;
- no credential or database file can be committed accidentally;
- deferring authentication removes scope from Milestone 1 without creating a security exposure, because the application is unreachable from other hosts;
- the eventual move to a hosted deployment is contained within the persistence module.

Negative

- Tower-owned information is isolated per developer, so Tower is not yet a team reference;
- a future migration to PostgreSQL is required and has been deferred rather than avoided;
- compatibility mode reduces but does not eliminate the risk of dialect drift;
- the absence of authentication becomes a blocking dependency the moment the application is exposed beyond loopback;
- manual Observation identity is weaker than it would be with authenticated users.

---

## Alternatives Considered

### PostgreSQL in a Container

Rejected for the current phase.

This keeps laptop storage and future hosted storage identical and eliminates dialect drift.

It was rejected because it introduces a container runtime as a prerequisite for the clone, build and run workflow.

---

### Embedded Database Without Compatibility Mode

Rejected.

This offers no advantage over compatibility mode and increases the risk that migrations and queries depend on vendor-specific behaviour.

---

### Shared Database Across Developer Machines

Rejected for the current phase.

Pointing several local instances at one shared database would restore team-wide visibility immediately.

It was rejected because it reintroduces the infrastructure prerequisite that local-first deployment is intended to avoid.

It remains available to any team that wants it, because the database connection is configuration.

---

### Defer Team Visibility Entirely

Rejected.

Accepting isolated instances without any mechanism for moving information between them leaves the Vision unaddressed for the whole of Milestone 1.

ADR-010 provides the interim mechanism.

---

## Related Documents

- Vision.md
- Non-Functional-Requirements.md
- Information-Architecture.md
- Implementation-Plan.md
- ADR-006-Manual-Observation-Entry.md
- ADR-010-Tower-Owned-Data-Is-Portable.md
