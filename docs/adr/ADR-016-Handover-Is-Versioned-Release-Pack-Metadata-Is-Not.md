# ADR-016 — Handover Is Versioned; Release Pack Metadata Is Not

**Status**

Accepted

---

## Context

IA-02 requires User-Owned Information to be versioned by Tower. FR-054 records that Milestone 1 does not meet this in full, and names two gaps: Release Pack metadata is overwritten by a rename, and Handover information is overwritten by an edit.

The two gaps are not equally serious, and treating them as one requirement obscures that.

Handover is the information handed to another team before a deployment. It holds the deployment instructions, the shell commands, the database migrations and the rollback procedure — the text someone follows at three in the morning when a release is going wrong.

When that goes wrong, the first question is what was actually handed over. Today Tower cannot answer it, because the answer was overwritten by whoever last edited the page. The record most needed at the worst moment is the one being destroyed.

A Release Pack rename loses something too: that the release used to be called something else. That is a fact about Tower's own bookkeeping rather than about what a team was told to do, and nobody reaches for it under pressure.

There is a precedent for each treatment. ADR-007 versions Promotion Paths, publishing an immutable version on every edit, because a pack pinned to version 1 must keep following the topology it was planned against. ADR-002 makes Observations append-only for a different reason: they are facts about the world and a fact does not stop having been true.

Handover is closer to the second. What a team was told on 3 August remains what they were told, whatever is written afterwards.

Three questions follow, and issue #8 raises all three.

Where the history lives matters more than it appears. A Release Pack is loaded on every listing, and an aggregate carrying an unbounded edit history would load all of it every time, to answer a question almost no listing asks.

Whether an export carries the history is a question about what an export is for. ADR-010 exports a release so another instance can hold it, and already treats Observations as an explicit opt-in rather than a default.

Retention is the third, and it is the easiest. OQ-007 asks about retention for Snapshots because a Snapshot of every Environment could grow without bound.

---

## Decision

**Handover shall be versioned. Every edit shall append an immutable revision, and no revision shall ever be modified or deleted.**

**Release Pack metadata shall not be versioned.** A rename overwrites, and that is accepted rather than overlooked. FR-054 shall be narrowed to record this as a deliberate remaining gap rather than an unmet requirement.

Revisions shall be held in **an append-only store of their own**, keyed by Release Pack, and not inside the Release Pack aggregate. The aggregate keeps its current Handover exactly as it does today, so nothing about reading or rendering a Release Pack changes and no listing pays for a history it did not ask for.

The current Handover therefore appears twice: on the aggregate, and as the newest revision. That duplication is accepted deliberately. The alternative — storing only superseded revisions — makes "what did we hand over on 3 August" a question that has to be answered from two places and joined, and makes the revision numbering describe overwrites rather than versions.

**A revision records when it was written and nothing about who wrote it.** ADR-009 has every developer running their own instance, so the author is always the person reading it, and recording an operating-system username would add a column that is the same on every row.

**Handover history shall not be exported** (ADR-010). An export transfers a release so another instance can hold it; the history records how one team's instructions evolved, which is about the instance that wrote them. This is additive to reverse if that turns out to be wrong.

**Every revision shall be retained for as long as its Release Pack exists.** A Handover is six text fields, and a release is edited a handful of times. Unlike a Snapshot there is no volume that would force a retention policy, so imposing one would be answering a question nobody has asked.

**Deleting a Release Pack shall delete its revisions.** The protection this decision gives is that an *edit* never loses history. Deleting the whole release is a different act, it is explicit, and it is already guarded — a pack carrying validation history cannot be deleted at all. Blocking the delete here would not protect a deliberate act; it would block one, and it would make every Release Pack whose Handover had ever been touched permanently undeletable. Revisions of a release that no longer exists are unreachable in any case: nothing can name them once the pack is gone.

---

## Consequences

The question that prompted this — what did we hand over last time — becomes answerable, and stays answerable, because nothing removes a revision.

A Release Pack loads exactly as fast as before. History is fetched only by the screen that shows it.

Generated documentation is unaffected. It renders the current Handover, because a release document describes the release as it stands; a document that rendered history would be a different document.

The duplication between the aggregate's Handover and the newest revision is a consistency risk, and it is confined to one place: the use case that updates Handover writes both. Nothing else may write either.

A rename still loses the previous name. Anyone who needs that history will find this decision record rather than an oversight, and reversing it is a smaller change than this one was.

An export carries the current Handover and not its history, so a release transferred to another instance arrives with what to do and without how the instructions were arrived at. That is the right default for sharing a release and the wrong one for auditing a team, and Tower is not an audit tool.

Every revision being kept means the store grows with editing rather than with time. That is a shape that stays small for the workload Tower is built for, and if it ever does not, the fix is a retention policy — which OQ-007 will have had to answer for Snapshots first anyway.

This decision record originally said the opposite about deletion, and blocking it was implemented before being tried. Deleting a Release Pack then answered 500 rather than refusing cleanly, which is what exposed the reasoning as wrong: the rule was protecting rows from a deliberate act rather than protecting a record from an accidental one.
