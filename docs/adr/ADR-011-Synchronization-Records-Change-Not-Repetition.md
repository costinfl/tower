# ADR-011 — Synchronization Records Change, Not Repetition

**Status**

Accepted

---

## Context

Milestone 1 produces Observations through the Manual Collector alone.

A person appends a fact deliberately, once, when something has happened.

Milestone 2 introduces Connectors that read External Systems without a person deciding when.

The same External System returns the same answer every time it is asked until something changes.

ADR-002 establishes the Observation as an immutable fact collected from an identified source at a point in time.

It does not say whether asking the same question twice and receiving the same answer constitutes one fact or two.

Connector-Model.md places the synchronization strategy explicitly outside its scope.

The question is therefore open rather than already decided.

The Observation store is append-only by design.

BR-02, IA-01, SM-06 and FR-023 all forbid editing or deleting an Observation, and the outbound port offers neither operation.

Nothing in the existing model would prevent a Connector from appending an identical Observation on every run.

A Connector reading a Kubernetes namespace every five minutes would append roughly two hundred and eighty-eight Observations per workload per day, almost all of them identical to their predecessor.

Milestone 4 depends on this store.

Snapshots, Environment comparison and release progression history all read the Observation stream, and a stream in which almost every entry is noise degrades that feature before it is built.

There is a competing concern.

Recording only change means the newest Observation for a workload may be weeks old, and Tower would have no way to distinguish a version that is still deployed from one it simply stopped looking at.

Connector-Model.md already requires that synchronization status shall be reported.

That requirement is currently unimplemented and is the natural home for the missing information.

---

## Decision

A synchronization run shall append an Observation only when the observed state differs from the newest state Tower already holds.

State is compared per Environment and Application.

When the Application Version observed for that pair differs from the Application Version of the most recent Observation for that pair, the run appends a new Observation.

When it does not differ, the run appends nothing.

Repetition is not a new fact.

Every synchronization run shall record a Sync Run, whether or not it appended anything.

A Sync Run records:

- the Connector that ran;
- when the run started and finished;
- how many workloads were read;
- how many Observations were appended;
- how many workloads could not be attributed to a bound Application;
- the failure, when the run failed.

The Sync Run is operational telemetry and is not part of the Canonical Model.

It records what Tower did, not what Tower observed about the world.

It shall therefore live in the application layer and shall never enter the Domain Model.

Tower shall distinguish "changed at a time" from "confirmed present at a time" by presenting the Observation together with the most recent successful Sync Run.

The Observation says when the deployed version last changed.

The Sync Run says when Tower last looked and found it unchanged.

Neither claim is invented, and neither requires an Observation that records nothing new.

A failed run shall append no Observation.

Previous Observations remain valid, as Connector-Model.md requires under Failure Handling.

A run that read some Environments successfully and failed on others shall be recorded as partial, with the failure preserved.

Incomplete synchronization shall never remove or invalidate what Tower already knows.

---

## Consequences

Positive

- the Observation stream records events rather than polling artefacts, so its size is proportional to what actually happened;
- Milestone 4 receives a history worth querying;
- ADR-002 is preserved exactly, because every Observation remains an immutable fact from an identified source;
- liveness information is available without fabricating facts, satisfying the Vision's claim that Tower reports what it observed;
- the existing requirement that synchronization status be reported is implemented rather than left dormant;
- a failed Connector degrades visibly instead of silently poisoning the Canonical Model.

Negative

- synchronization requires reading the newest Observation per Environment and Application before appending, which is additional work per run;
- two records must be consulted to answer "is this still deployed", rather than one;
- the Sync Run introduces a second kind of stored information alongside Observations, which must be persisted, displayed and reasoned about;
- a change that occurs and reverts between two runs is never observed, and Tower will report no change at all;
- Sync Run records accumulate and will eventually need a retention decision, which this record does not make.

---

## Alternatives Considered

### Append an Observation on Every Run

Rejected.

The store would grow in proportion to the polling frequency rather than to events in the world.

Almost every Observation would be identical to its predecessor.

Historical comparison, which Milestone 4 exists to provide, would have to filter out the overwhelming majority of its own input.

The polling interval, which is an operational detail, would silently determine the shape of the business record.

---

### Append on Change and Record Nothing Else

Rejected.

This keeps the store clean but leaves Tower unable to say whether a fact is still true.

A version observed three weeks ago and a version observed three minutes ago would be indistinguishable in the interface.

Tower would appear to assert current state while actually reporting an old measurement, which contradicts the honesty the Vision claims.

---

### Make the Observation Mutable, Updating a Last-Seen Timestamp

Rejected.

This is the most storage-efficient option and it breaks the central invariant of the system.

BR-02, IA-01, SM-06, FR-023 and ADR-002 all require that an Observation is never edited.

Mutating a timestamp would destroy the record of what was believed and when, which is what makes historical comparison possible at all.

---

### Deduplicate Identical Observations in the Repository

Rejected.

Suppressing an append inside the repository would hide the decision in the persistence adapter, where neither the requirement nor the reasoning is visible.

It would also make the repository lie about what it stored, which complicates import, where ADR-010 requires deduplication by identity for a different reason.

Change detection belongs to the Collector, which is the component responsible for normalization.

---

## Related Documents

- Connector-Model.md
- Information-Architecture.md
- Functional-Requirements.md
- Implementation-Plan.md
- ADR-002-Observation-Is-The-Atomic-Unit.md
- ADR-006-Manual-Observation-Entry.md
- ADR-010-Tower-Owned-Data-Is-Portable.md
- ADR-012-External-Bindings-Map-Tower-Concepts-To-Vendor-Locators.md
