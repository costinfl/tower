# ADR-008 — Release Pack State Derives from Environment Stage

**Status**

Accepted

---

## Context

The State Model defines six Release Pack states.

These are Planned, Development, Validation, Pre-Production, Production and Archived.

SM-02 states that Release Pack state is derived from Observations and that state is never manually assigned.

Two problems follow from this definition.

The first problem concerns Archived.

An archived Release Pack is described as one that is no longer actively progressing.

No Observation can express that a team has stopped caring about a release.

Archived is a human decision and therefore cannot be derived.

OQ-003 acknowledges this tension by asking whether Release Packs should support explicit lifecycle states in addition to derived observed state.

The second problem concerns the state names themselves.

The states assume an Environment topology containing development, validation, pre-production and production stages.

Promotion Paths are user-defined and need not contain any of these.

The Hotfix path described in the Scenarios and the State Model progresses through Dev-Hotfix, SIT, UAT and Production.

It contains no pre-production Environment.

The documented state transitions show Pre-Production as the only route into Production.

A Hotfix Release Pack can therefore never legally reach the Production state.

Deriving state from Environment names would also be fragile, because Environment naming is an organizational choice rather than a business concept.

---

## Decision

Every Environment shall carry a Stage classification.

The Stage values are DEVELOPMENT, VALIDATION, PRE_PRODUCTION and PRODUCTION.

Stage is a property of the Environment and is independent of the Environment name.

An Environment named SIT1, UAT or QA may all classify as VALIDATION.

Release Pack state shall be derived as the highest Stage at which any Application Version belonging to the Release Pack has been observed.

A Release Pack with no Observations is in the Planned state.

A Release Pack whose highest observed Stage is VALIDATION is in the Validation state, regardless of which Environments its Promotion Path contains.

A Promotion Path that contains no PRE_PRODUCTION Environment therefore progresses directly from Validation to Production.

This makes the Hotfix path legal without special handling.

Archived shall be separated from the derived states.

Archived becomes an explicit lifecycle flag owned by developers and classified as Intent rather than Observation.

A Release Pack therefore has two independent attributes.

The derived observed state, which answers where the release has been seen.

The lifecycle flag, which answers whether the team still considers the release active.

SM-02 continues to apply to the derived state and no longer applies to Archived.

OQ-003 is answered by this decision.

---

## Consequences

Positive

- Release Pack state works for any user-defined Promotion Path;
- the Hotfix path reaches Production without special handling;
- state derivation no longer depends on Environment naming conventions;
- SM-02 becomes true for every derived state;
- Archived is modelled honestly as a human decision;
- OQ-003 is resolved.

Negative

- every Environment must be classified when it is created;
- an incorrectly classified Environment produces an incorrect derived state;
- Stage introduces a small vocabulary that must be documented in the Glossary;
- a Release Pack now carries both a derived state and a lifecycle flag, which must be presented clearly.

---

## Alternatives Considered

### Derive State From Environment Names

Rejected.

Environment naming is an organizational convention rather than a business concept.

Tower would need to recognize arbitrary names such as SIT1, QA2 or Staging, and would break whenever a team renamed an Environment.

---

### Derive State From Position Within the Promotion Path

Rejected.

Position expresses progress through a specific path but is not comparable across paths.

A Release Pack at position three of a four-stage Hotfix path and a Release Pack at position three of a five-stage Regular path are not in equivalent states.

---

### Keep Archived as a Derived State

Rejected.

No Observation can indicate that a team has stopped progressing a release.

Retaining Archived as derived would leave SM-02 permanently false.

---

### Allow Manual State Assignment

Rejected.

This contradicts SM-02 and SM-04 and would allow the reported state to diverge from observed reality.

---

## Related Documents

- State-Model.md
- Domain-Model.md
- Glossary.md
- Scenarios.md
- Open-Questions.md
- ADR-002-Observation-Is-The-Atomic-Unit.md
- ADR-005-Environments-Are-Shared-Across-Promotion-Paths.md
