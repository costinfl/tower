# ADR-007 — Promotion Paths Are Versioned, Not Immutable

**Status**

Proposed

---

## Context

SM-01 states that Promotion Paths are immutable after creation.

Backlog Epic 1 is Priority 1 and requires the ability to edit and delete Promotion Paths.

OQ-001 asks whether a Promotion Path can be modified after Release Packs already reference it, and records the question as deferred.

These three statements cannot all be satisfied.

The underlying tension is real.

Delivery workflows evolve.

Teams add validation Environments, retire old ones and reorder stages.

Forbidding all modification would force teams to create a new Promotion Path for every workflow change and would leave the original path permanently inaccurate.

At the same time, a Release Pack that has already progressed through a path must continue to report the topology it actually followed.

Editing a path in place would silently rewrite the history of every Release Pack referencing it, violating the principle that historical information never changes.

The Domain Model uses the word static in a different sense.

BR-03 states that Promotion Paths are static, and the surrounding text explains that Release Packs move through Promotion Paths while Promotion Paths never move.

That statement concerns movement rather than editability and is not in conflict with this decision.

---

## Decision

Promotion Paths shall be editable.

Every edit shall produce a new immutable version of the Promotion Path.

A Promotion Path therefore consists of a stable identity and an ordered series of versions.

Each version contains the ordered sequence of Environment references in effect at that time.

A Release Pack references a specific Promotion Path version rather than the Promotion Path identity alone.

A Release Pack created before an edit continues to reference the version it was assigned.

A Release Pack created after an edit references the newer version.

Historical Release Packs therefore continue to report the topology they actually followed.

Deletion shall be replaced by archival whenever any Release Pack references any version of the Promotion Path.

An archived Promotion Path cannot be assigned to new Release Packs but remains readable for historical purposes.

A Promotion Path that no Release Pack references may be deleted outright.

SM-01 is replaced by this decision.

OQ-001 is answered by this decision.

OQ-002, which asks whether a Release Pack may change Promotion Paths, remains deferred.

---

## Consequences

Positive

- teams can evolve delivery workflows without abandoning existing Promotion Paths;
- historical Release Packs continue to report the topology they actually followed;
- immutability is preserved at the version level, consistent with SM-06;
- Backlog Epic 1 becomes implementable without violating the State Model;
- OQ-001 is resolved.

Negative

- Promotion Path becomes a versioned concept rather than a simple record;
- the user interface must make the distinction between identity and version understandable;
- comparing Release Packs that follow different versions of the same path requires care;
- archival introduces a lifecycle that did not previously exist.

---

## Alternatives Considered

### Keep Promotion Paths Immutable

Rejected.

This preserves SM-01 but makes Backlog Epic 1 unimplementable.

It also forces teams to create a new Promotion Path for every workflow change, accumulating near-duplicate paths.

---

### Allow In-Place Editing Without Versioning

Rejected.

Editing a path in place silently rewrites the reported history of every Release Pack that references it.

This violates SM-06 and the principle that historical information remains immutable.

---

### Copy the Promotion Path Into Each Release Pack

Rejected.

Embedding a full copy of the path inside every Release Pack duplicates information, contradicting IA-04.

It also removes the ability to reason about a Promotion Path as a shared concept.

---

## Related Documents

- State-Model.md
- Domain-Model.md
- Backlog.md
- Open-Questions.md
- Information-Architecture.md
- ADR-005-Environments-Are-Shared-Across-Promotion-Paths.md
