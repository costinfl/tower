# ADR-005 — Environments Are Shared Across Promotion Paths

**Status**

Accepted

---

## Context

The Domain Model states that an Environment belongs to one Promotion Path.

The Canonical Relationships diagram reinforces this by showing Environment belonging to Promotion Path.

However, the Scenarios and the State Model both describe two Promotion Paths that share Environments.

The Regular path progresses through Dev1, SIT1, UAT, PreProd and Production.

The Hotfix path progresses through Dev-Hotfix, SIT, UAT and Production.

UAT and Production appear in both paths.

Organizations typically operate a small number of shared downstream Environments and a larger number of upstream Environments.

Several Promotion Paths converge on the same validation and production Environments.

The decision about which Release Pack proceeds through a shared Environment is made by people rather than by Tower.

A single-owner relationship cannot represent this reality.

---

## Decision

The relationship between Environment and Promotion Path shall be many-to-many.

A Promotion Path is an ordered sequence that references Environments.

A Promotion Path does not own the Environments it references.

An Environment may participate in any number of Promotion Paths at any position.

Convergence is explicit and expected.

Two Promotion Paths that share an Environment represent two delivery workflows that meet at that Environment.

Tower shall never infer which Release Pack proceeds beyond a convergence point.

That decision remains a human responsibility, consistent with ADR-001 and the observation-only philosophy.

An Environment observed to contain a given Application Version reports that fact to every Promotion Path that references it.

---

## Consequences

Positive

- accurately represents shared validation and production Environments;
- supports parallel Regular and Hotfix workflows as already described in the Scenarios;
- keeps Environments as independent business objects rather than path fragments;
- avoids duplicating Environment definitions across paths;
- preserves segregation of duties by leaving convergence decisions to people.

Negative

- Environment state may be relevant to several Promotion Paths simultaneously;
- determining the observed progression of a Release Pack requires the path it follows to be known;
- a shared Environment may contain Application Versions belonging to several Release Packs.

---

## Alternatives Considered

### Environment Belongs to One Promotion Path

Rejected.

This is the current wording of the Domain Model.

It contradicts the Scenarios and the State Model and cannot represent converging workflows.

---

### Duplicate Environments Per Promotion Path

Rejected.

A UAT Environment referenced by two paths would become two distinct records describing the same deployment target.

Observations would be duplicated and Environment state would diverge.

---

### Model Convergence as an Explicit Merge Object

Rejected for Milestone 1.

Introducing a merge concept adds a primary business object to a deliberately small domain.

Shared Environment references express the same reality without new vocabulary.

---

## Related Documents

- Domain-Model.md
- CRC-Cards.md
- Scenarios.md
- State-Model.md
- Glossary.md
