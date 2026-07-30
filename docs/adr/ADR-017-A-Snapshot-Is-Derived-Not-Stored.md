# ADR-017 — A Snapshot Is Derived, Not Stored

**Status**

Accepted

---

## Context

Milestone 4 exists to answer what changed, when it changed, and which Release Pack introduced it. Glossary.md defines a Snapshot as an immutable representation of the observed state of one or more Environments at a specific point in time, and adds that its implementation is intentionally unspecified.

Event-Model.md ends its synchronization flow in **Snapshot Created**, and EV-07 requires every Snapshot to be created from Observations. Read on its own, that event implies a Snapshot is a thing Tower writes down.

Two decisions already taken pull the other way, and they were taken for reasons that have not changed.

State-Model.md and `EnvironmentState` derive what is deployed in an Environment from its Observations rather than storing it, on the grounds that deriving leaves exactly one source of truth and no stored projection that can silently disagree with it. ADR-008 does the same for Release Pack state.

ADR-002 makes an Observation immutable and the store append-only, and ADR-011 keeps that store honest by appending only on change and recording the rest as Sync Runs. Nothing is ever edited or removed.

Put together, those mean the Observation stream **is** the history. Every Observation carries the instant it describes, nothing is deleted, and current state is already a fold over that stream. Reconstructing the state at any past instant is the same fold with an upper bound on `observedAt` — the data is already present and already immutable.

That makes stored Snapshots a copy of information Tower can already produce, which IA-03 classifies as Generated Information: disposable, reproducible, and never a source of truth.

There is a sharper argument than duplication, though.

A stored Snapshot can only answer questions about moments somebody captured. A Snapshot taken nightly cannot say what was running when an incident began at 14:32, because nobody took one at 14:32. A derived Snapshot answers for **any** instant, including the ones nobody thought to record — which is precisely when the question gets asked.

---

## Decision

**A Snapshot shall be derived from the Observation stream on request, and shall not be stored.**

Tower shall answer the state of an Environment at any instant by folding the Observations whose `observedAt` is at or before that instant, in exactly the way current state is already folded. Current state becomes the special case where the instant is now.

This satisfies Glossary.md as written. A derived Snapshot is an immutable representation of observed state at a point in time: immutable because the Observations behind it are, and because asking the same question of the same stream twice gives the same answer.

**Event-Model.md's "Snapshot Created" shall be read as describing the moment a Snapshot is produced, not a write.** EV-07 is satisfied in the strongest available sense — every Snapshot is created from Observations, because a Snapshot is *nothing but* Observations folded.

**OQ-007 — how long historical Snapshots should be retained — is answered: the question does not arise.** Nothing is retained because nothing is stored. Retention of the Observation stream is a separate question that ADR-011 already left open, and it is the only one that matters.

**Comparison shall be between two derived states**, whether that is one Environment at two instants or two Environments at one instant. A comparison is itself derived and stored nowhere.

Every derived value shall keep citing the Observation it came from, exactly as current state does (FR-031, FR-032, NFR-011). A historical answer that could not be traced back to the fact behind it would be worse than no answer.

**A Release Pack's progression shall be derived the same way** (FR-070): where the release has been observed, and when. It reports two instants per Environment rather than one, because a release arrives piecemeal — a single "arrived at" would have to choose between the moment the first version turned up and the moment the last one did, and those can be days apart. A release with versions still outstanding is reported as partly arrived, naming them.

Nothing had to be recorded at the time for any of this to be answerable now, which is the same property the point-in-time state has and the reason both belong to this decision rather than to a table.

---

## Consequences

Milestone 4 needs no new table, no new migration, and no retention policy. The capability it exists to deliver is unlocked by reading what is already there differently.

Any instant is answerable, not only the ones somebody captured. "What was running when the alert fired" is the question this milestone is for, and a stored-Snapshot design would have answered it with the nearest nightly capture and called that close enough.

There is exactly one source of truth for history, as there already is for current state. A stored Snapshot could disagree with the Observations it was built from — after a correcting Observation arrived late, for instance — and nothing would reconcile them.

The cost is computation. Deriving a past state reads a Release Pack's or Environment's Observations and folds them, where a stored Snapshot would be a single row. For the workload Tower is built for — one developer, one instance, local-first (ADR-009) — that is not a trade worth making yet. If it ever becomes one, the fix is a cache of a derivation, which is a different thing from a stored fact: a cache may be discarded and rebuilt, and IA-03 already says what such a thing is.

A late-arriving Observation changes the answer to a question about the past, and that is correct rather than a defect. If Tower learns on Tuesday that version 2.5.0 was running on Monday, then the honest answer to "what was running on Monday" changes on Tuesday. A stored Snapshot would have frozen the earlier, less informed answer and presented it with equal confidence.

This decision is reversible in the direction that matters. Adding a stored Snapshot later is additive; removing one that features had come to depend on would not be.
