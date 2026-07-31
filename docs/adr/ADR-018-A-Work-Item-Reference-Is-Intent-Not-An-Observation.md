# ADR-018 — A Work Item Reference Is Intent, Not an Observation

**Status**

Accepted

---

## Context

Connector-Model.md names an Issue Tracking Connector among the five Connector categories. Building it
exposes a claim in that same document that does not survive the attempt.

Connector-Model.md § Observation Mapping states that **every Connector ultimately produces Observations**,
and gives three worked examples: a git tag becomes an Application Version observed, a deployment becomes a
Deployment Unit observed, a pipeline execution becomes an Observation. It gives no example for a work item,
and the omission turns out to be the honest part of the section.

Tower's Observation is a specific shape — an Application Version seen in an Environment at an instant, from
an identified source. Every derivation in the system folds that shape: Environment state, Release Pack state
(ADR-008), point-in-time state and progression (ADR-017). A work item has no Environment, no Application and
no Application Version. Forcing one into that record would either corrupt those folds or require a second
kind of Observation that shares only a name with the first.

Product-Boundaries.md is also explicit that Tower **does not own work items**. Guardrails.md lists "a
replacement for Jira" and "a project management platform" among the things Tower must never become.

So the question is not how to turn an issue into an Observation. It is whether Tower should hold anything
about issues at all, and if so, under what claim.

IA-05 answers it. Every displayed value shall be traceable to **either an Observation or User-Owned
Information**. Tower has always had two origins for facts, not one, and a work item reference belongs to the
second.

---

## Decision

**A work item reference is Intent.** A developer states that a Release Pack delivers PROJ-123. That is a
claim by the team about their own release, owned by Tower, in exactly the way Release Pack membership and
Handover information are owned by Tower. It is not something Tower observed.

**The Issue Tracking Connector produces no Observations.** It resolves references that already exist. Given
an identifier, it reads the work item's current title, status and URL from the tracker. Nothing it returns
is stored as a fact about the world.

**Connector-Model.md § Observation Mapping is corrected** rather than worked around. "Every Connector
ultimately produces Observations" holds for the categories that observe running systems. It does not hold
for a Connector whose subject is a record in somebody else's database. The document now says so.

**A linked title is accepted, not fetched.** When a developer links a work item, Tower reads the title once
and offers it; the developer accepts it, and from that moment the title is Intent that Tower owns. Generated
documentation prints the identifier and that accepted title, and never reads the tracker.

This is the same interaction source control discovery already uses (ADR-014): a discovered version fills the
registration form rather than registering itself, so what gets recorded is what a person saw and accepted.

**The tracker's current state is shown, never stored.** The Viewer resolves linked references on request and
displays what the tracker says now, beside what Tower holds. Where the two differ, the difference is shown.
Tower does not correct itself silently, and does not treat the tracker as authoritative over a document
already handed to another team.

---

## Consequences

**NFR-025 survives.** Regenerating an unchanged Release Pack still produces byte-identical output, because
no part of the document is read from an external system at generation time. Had documents carried live
titles, renaming a ticket in Jira would have changed a document Tower calls reproducible, and IA-03's
"generated information is disposable, it can always be regenerated" would have quietly stopped being true.

**A stale title is possible, and is shown rather than prevented.** If somebody rewrites a ticket's summary
after a release document went out, Tower's copy is the older wording. That is the correct behaviour: the
document records what the team said this release delivers, and a handover already given to another team does
not change because a ticket was edited. The Viewer marks the divergence so nobody has to discover it by
comparing tabs.

**Work item references are versioned with the Release Pack's other Intent** (IA-02). Adding or removing one
is an edit to User-Owned Information.

**Tower does not become a second issue tracker.** It holds an identifier and a title a person accepted. It
holds no description, no comments, no assignee, no workflow, no relationships. Anything beyond identifying
the work belongs to the tracker, and the Viewer links to it.

**A reference may be linked to a tracker Tower cannot reach.** Linking requires only an identifier, so a
Release Pack can name work items before any Connector is configured, and keeps them if the tracker is
unreachable. Resolution fails visibly; the reference itself is unaffected, because it never depended on the
tracker.

**The category needs a locator, and it is not per Application.** Source control binds per Application
because each Application has its own repository. Work items belong to a release rather than to one
Application, so the binding is per Connector: one tracker, named once. This is a weaker form of ADR-012's
"Tower concept on the left" and is recorded here as a deliberate difference rather than an oversight.

---

## Alternatives Considered

### A work item becomes an Observation

Rejected. It would need a second Observation shape with no Environment and no Application Version, sharing a
name and a table with the first and satisfying none of the same derivations. The word would stop meaning
what ADR-002 says it means, which is the one thing that keeps correlation across Connectors simple.

### Documents read the tracker at generation time

Rejected, and this was the closest call. It produces richer documents — a business reader sees current
statuses — but it breaks NFR-025 outright and makes a generated document depend on an external system being
reachable and unchanged. A document that cannot be regenerated identically is not disposable, and IA-03 says
generated information must be.

### Documents print identifiers only

Rejected as unhelpful rather than wrong. It satisfies every constraint, and it is the strictest reading of
"Tower does not own work items". But a handover document listing PROJ-123 and PROJ-140 without saying what
they are fails the purpose the document exists for, which is telling another team what they are deploying.

### No Issue Tracking Connector at all

Rejected. The category is named in Connector-Model.md and Context.md, and the need is real: "what does this
release deliver" is answered today only in Application Versions, which is the wrong vocabulary for the people
who receive a handover. Reading a title is not owning a work item.

---

## Related

- ADR-002 — Observation is the Atomic Unit
- ADR-012 — External Bindings Map Tower Concepts To Vendor Locators
- ADR-014 — Source Control Is Read Through Git, Not A Vendor API
- ADR-017 — A Snapshot Is Derived, Not Stored
- IA-02, IA-03, IA-05; NFR-025
- Product-Boundaries.md — Tower does not own work items
