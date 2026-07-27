# ADR-006 — Manual Observation Entry via a Manual Collector

**Status**

Proposed

---

## Context

Milestone 1 delivers Environment visibility without the Connector framework.

The Connector framework is scheduled for Milestone 2 and Backlog Epic 5 is Priority 2.

Milestone 1 therefore includes manual Observation management.

However, the Glossary defines an Observation as an immutable fact collected from an external system.

ADR-002 states that Observations are externally sourced.

The Information Architecture states that Observed Information originates from External Systems.

EV-03 states that Observation Events originate from External Systems.

As currently written, Milestone 1 cannot produce a legal Observation.

At the same time, the Functional Requirements declare themselves to be the scope of Milestone 1 and FR-019 requires Observations to be collected through Connectors.

A decision is required that allows Milestone 1 to deliver Environment visibility without weakening the Observation concept.

---

## Decision

Manual entry shall be modelled as a Collector rather than as an exception to the Observation model.

A Manual Collector shall be introduced whose source is identified as manual entry.

Every Observation produced by the Manual Collector shall record:

- the identity of the person who entered it;
- the timestamp at which it was entered;
- the source identifier indicating manual entry.

Observations produced by the Manual Collector are immutable in exactly the same way as Observations produced by any other Collector.

The definition of Observation is widened from a fact collected from an external system to a fact collected from an identified source.

An identified source may be an External System or the Manual Collector.

Every other property of an Observation is unchanged.

Observations remain immutable, timestamped, traceable to their origin and the atomic unit of information.

The pipeline is unchanged.

All information continues to reach the Canonical Model through a Collector.

FR-019 to FR-022 are satisfied in Milestone 1 by the Manual Collector and in Milestone 2 by external Connectors.

---

## Consequences

Positive

- Milestone 1 delivers Environment visibility without the Connector framework;
- a single Observation pipeline serves both manual and automated sources;
- immutability, timestamps and provenance are preserved for every Observation;
- FR-013 and FR-022 remain satisfiable because manual entries carry a source;
- Milestone 2 introduces Connectors without reworking the Observation model;
- users can always distinguish a manually entered fact from an automatically collected one.

Negative

- the definition of Observation in the Glossary must be widened;
- manually entered information depends on human accuracy;
- an Environment may temporarily contain a mixture of manual and automated Observations;
- correcting a manual mistake requires a superseding Observation rather than an edit.

---

## Alternatives Considered

### Ship a Deployment Platform Connector in Milestone 1

Rejected.

This preserves the literal wording of ADR-002 but contradicts Milestones.md and Backlog Epic 5.

It also significantly increases the scope of the minimum viable product.

---

### Introduce a Separate Declaration Concept

Rejected.

Manual entries would become developer Intent rather than Observations.

Observation would remain purely external, but every Environment view would need to render two different kinds of information with different rules.

This adds a primary business concept to a deliberately small domain.

---

### Allow Observations to Be Edited

Rejected.

This directly violates BR-02, IA-01 and ADR-002.

Immutability is the foundation of historical comparison and deterministic documentation generation.

---

## Related Documents

- Glossary.md
- Domain-Model.md
- Event-Model.md
- Information-Architecture.md
- Connector-Model.md
- Functional-Requirements.md
- ADR-002-Observation-Is-The-Atomic-Unit.md
