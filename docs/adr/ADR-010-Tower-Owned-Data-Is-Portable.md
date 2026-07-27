# ADR-010 — Tower-Owned Data Is Portable

**Status**

Proposed

---

## Context

ADR-009 establishes that Tower initially runs on each developer's machine with an isolated database.

Tower-owned information therefore exists only on the machine that created it.

Two developers preparing the same release would each define their own Release Pack, their own Promotion Path and their own Handover information.

The Vision describes Tower as the team's operational reference during release preparation.

An isolated instance cannot satisfy that description.

A mechanism is required to move Tower-owned information between instances until a shared deployment exists.

That mechanism must not weaken the Observation model.

ADR-002 establishes Observation as the atomic unit and requires every fact to be traceable to its origin.

ADR-006 establishes that every Observation reaches the Canonical Model through a Collector.

A naive import that recreated Observations locally would break both records, because the receiving instance would claim facts it never observed.

---

## Decision

Tower shall support export and import of Tower-owned information.

Export shall cover the information that the Information Architecture identifies as owned by Tower.

This includes:

- Release Packs;
- Promotion Paths and their versions;
- Handover information;
- validation Iterations;
- Intent.

Observations may be included in an export as an explicit option.

When Observations are included, they retain their original provenance.

Import transfers existing immutable facts.

Import never creates new Observations.

The original source, the original timestamp and the original identity are preserved exactly.

An imported Observation therefore continues to record the Collector and the instance that first observed it, not the import event.

This keeps ADR-002 and ADR-006 intact, because every Observation still originates from a Collector, even when that Collector belongs to another instance.

The export format shall be JSON with an explicit schema version.

JSON is portable, diffable and reviewable by a developer before import.

Import shall be additive with explicit conflict resolution.

When an incoming record collides with an existing record, the user shall choose to skip it, replace it or import it as a duplicate.

Tower shall never merge conflicting records automatically.

Observations cannot conflict.

They are append-only and are deduplicated by identity, so importing the same Observation twice has no effect.

Only user-owned information can conflict.

Export files shall never contain credentials or configuration.

---

## Consequences

Positive

- Release Packs, Promotion Paths and Handover information can be shared between developers;
- the Vision is partially served during the local-first phase;
- the Observation model is preserved intact, because provenance survives transfer;
- a JSON export can be reviewed, stored or attached to a release record;
- explicit conflict resolution keeps the outcome predictable and auditable;
- the same mechanism remains useful after a shared deployment exists, for archival and for moving information between environments.

Negative

- export and import add scope to Milestone 1;
- a developer must remember to export and share, so information can drift between instances;
- conflict resolution requires a user interface and user judgement;
- a duplicate import creates parallel records unless the user chooses otherwise;
- the export format becomes a compatibility surface that must be versioned and maintained.

---

## Alternatives Considered

### Automatic Merge on Import

Rejected.

Automatic merge would require Tower to decide which version of a Release Pack is correct.

That is a human decision, consistent with ADR-001 and ADR-005.

Automatic merge would also make the result of an import unpredictable.

---

### Recreate Observations on Import

Rejected.

The receiving instance would record facts it never observed, and the original source and timestamp would be lost.

This violates ADR-002, ADR-006, FR-022 and NFR-012.

---

### Export Everything Including Configuration

Rejected.

Configuration contains connector credentials.

Exporting them would distribute secrets in a file intended to be shared, which contradicts NFR-026.

---

### Rely on a Shared Database Instead

Rejected as the primary mechanism.

A shared database solves team visibility completely but reintroduces the infrastructure prerequisite that ADR-009 avoids.

It remains available to teams that want it and is complementary to this decision rather than a replacement.

---

## Related Documents

- Vision.md
- Information-Architecture.md
- Domain-Model.md
- Implementation-Plan.md
- ADR-002-Observation-Is-The-Atomic-Unit.md
- ADR-006-Manual-Observation-Entry.md
- ADR-009-Local-First-Deployment.md
