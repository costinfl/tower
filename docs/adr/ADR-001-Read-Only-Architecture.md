# ADR-001 — Read-Only Architecture

**Status**

Accepted

---

## Context

Tower integrates with numerous engineering systems including source control, issue tracking, CI/CD platforms, deployment platforms and documentation systems.

Each of these systems is already authoritative within its own domain.

Allowing Tower to modify those systems would increase coupling, blur ownership and violate segregation of duties.

---

## Decision

Tower shall operate as a read-only platform with respect to External Systems.

Tower may collect information.

Tower may correlate information.

Tower may generate documentation.

Tower shall not:

- execute deployments;
- modify pipelines;
- create or update issues;
- change documentation in external systems;
- modify repositories.

---

## Consequences

Positive

- preserves segregation of duties;
- simplifies integration;
- reduces operational risk;
- keeps Tower vendor neutral;
- makes the system easier to trust.

Negative

- Tower cannot automate operational activities.
- Some workflows require users to perform actions in external tools.

---

## Alternatives Considered

### Deployment Orchestrator

Rejected.

Products such as deployment orchestrators already solve that problem.

Tower intentionally occupies a different architectural space.

### Pipeline Controller

Rejected.

Pipeline execution belongs to CI/CD platforms.

Tower consumes pipeline information but does not control it.

---

## Related Documents

- Vision.md
- Context.md
- Connector-Model.md
- Non-Functional-Requirements.md
