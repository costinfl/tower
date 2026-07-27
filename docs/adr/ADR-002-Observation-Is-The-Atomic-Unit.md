# ADR-002 — Observation is the Atomic Unit

**Status**

Accepted

---

## Context

Tower aggregates information originating from multiple External Systems.

Those systems differ in terminology, technology and data models.

A common architectural foundation is required to normalize all incoming information.

Without a common atomic concept, every feature would need vendor-specific logic.

---

## Decision

Observation is defined as the atomic unit of information within Tower.

Every fact known by Tower shall originate from an Observation.

Observations are:

- immutable;
- timestamped;
- externally sourced;
- traceable to their origin.

Business concepts such as Environment state, Release Pack progression and Snapshots are derived from Observations.

---

## Consequences

Positive

- establishes a single source for observed facts;
- simplifies correlation across multiple systems;
- enables historical comparisons;
- supports deterministic documentation generation;
- improves traceability.

Negative

- requires normalization before information becomes usable.
- introduces an additional conceptual layer.

---

## Alternatives Considered

### Store Vendor Objects Directly

Rejected.

Vendor-specific models would leak into the Domain Model and reduce portability.

---

### Normalize Only During Presentation

Rejected.

Different views would interpret data differently, leading to inconsistent behaviour.

---

## Related Documents

- Glossary.md
- Domain-Model.md
- Event-Model.md
- Information-Architecture.md
