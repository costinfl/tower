# ADR-021 — An Artifact Is Confirmed, Not Recorded

**Status**

Accepted

---

## Context

A release in the setting this is written for produces three things from one build: the application
itself, a Docker image, and a Helm chart. All three go to JFrog Artifactory, and **the image and the
chart are both tagged with the version and the short commit hash**.

That last sentence decides most of this ADR, so it is worth saying what it rules out first.

The question raised before writing this was whether an Application Version, an image tag and a chart
version are one thing with three names or three things that need relating. Had the image been tagged
`build-4471` and the chart `0.3.9` while the application called itself `2.5.0`, they would have been
three identities, and Tower would have needed to store a mapping — discovered from somewhere,
maintained by someone, and wrong the moment a build changed its numbering.

They are not. `2.5.0-abc1234` is a **function** of the version and the commit, and Tower already
records both: `ApplicationVersion` has carried `version` and `commit` since Milestone 1. So there is
nothing to map. There is only something to compute, and then to confirm.

### What an artifact is not

**It is not an Observation.** An Observation is an Application Version seen in an Environment
(ADR-002), and an artifact has no Environment. An image sitting in a repository named
`docker-prod` is not running in production; it is a file in a folder whose name says "prod". That is
the same trap ADR-020 caught between a build and a deployment, and it is worth naming twice because
Artifactory invites it harder: promotion between repositories looks so much like promotion between
Environments that a reasonable person would fold them together, and a release would then show as
live in Production because a file was copied.

**It is not Intent either.** Nobody stated it. Tower read it.

So it falls outside both origins IA-05 names, exactly as a work item's current title did in ADR-018
— and it is resolved the same way for the same reason.

---

## Decision

### An artifact is confirmed on demand and stored nowhere

Tower composes the coordinate, asks the repository whether something is there, and shows the answer.
Nothing about the artifact enters the Canonical Model.

This is ADR-018's arrangement applied to a different subject: the Issue Tracking Connector resolves
references without storing what it finds, and this confirms artifacts without storing what it finds.
Both read somebody else's system to show a person something beside what Tower holds, and in neither
case does the reading create a fact.

That keeps IA-05 whole without widening it. Nothing new is displayed as a stored fact, because
nothing is stored.

### A coordinate is composed, not mapped

The binding carries a template rather than a mapping:

```
docker-local/acme/customer-api:{version}-{shortCommit}
helm-local/customer-api-{version}-{shortCommit}.tgz
```

This is ADR-012's family — a Tower concept on the left, a vendor locator on the right — but running
in the opposite direction. ADR-012's version pattern **extracts** a version from a string the vendor
produced; this **composes** a string the vendor will recognise from a version Tower holds. The
symmetry is worth recording, because the two look alike enough that a later reader may wonder why
one is a regular expression and the other is not.

`{shortCommit}` is the first seven characters of the recorded commit, because that is what
`git rev-parse --short` gives by default. It is configurable, because that default is not a
guarantee: git lengthens it when seven would be ambiguous, and a team may have pinned a different
length in their pipeline. A team whose build uses a different length and does not say so will see
artifacts reported missing, which is the failure mode discussed below.

### The failure mode is better here than anywhere else in Tower, and that is the point

ADR-012 carries a warning that a wrong version pattern produces wrong Application Versions, and that
because Observations are immutable those survive the correction. That warning does not apply here. A
wrong template produces a **false "not found"** — visible, harmless, and fixed by correcting the
template. Nothing is written, so nothing outlives the mistake.

This is the strongest argument for confirming rather than recording, and it is worth stating plainly:
the same design that keeps IA-05 whole also makes the one mistake this Connector can make a
reversible one.

### A document prints what somebody accepted

A tag is mutable. `2.5.0-abc1234` can be pushed over, and the digest beneath it changes. That is
precisely the drift a team wants to be shown — and precisely what NFR-025 forbids a release document
to contain, because a document that prints a live digest stops regenerating byte-identically the day
somebody re-pushes.

So the rule is ADR-018's, unchanged: **a screen shows what the repository says now, beside what Tower
holds; a document prints only what a person accepted.** If a release document is to record the exact
image a release shipped, the digest is accepted once, by someone, and from then on it is Tower's own.

### Only GET, and the tagging convention is what makes that possible

Artifactory's most capable read is AQL, and AQL is `POST /api/search/aql`. ADR-001 makes Tower's
read-only guarantee structural rather than a matter of restraint, and the architecture rule that
enforces it forbids a Connector from calling anything named `post`. A search would fail that rule
while being, semantically, a read.

It is not needed. Because the coordinate is derivable, the Connector can ask a direct question with
a direct GET — file information for a path, or the tag list for an image — rather than searching for
something whose name it does not know.

**Which is to say: the team's tagging convention is what makes a read-only Artifact Repository
Connector straightforward.** Without a derivable coordinate, Tower would have had to search, the
search wants POST, and the whole thing would have needed an exception carved into ADR-001. Worth
recording, because it is the kind of dependency nobody notices until somebody proposes changing the
tag format.

### A sixth Connector category

Connector-Model.md names five. Artifact Repository is a sixth, and unlike the Documentation
Connector — still unbuilt, still plausible — this one has a concrete subject and a concrete
consumer.

---

## What this is for

Three things, and none of them is more Observations.

**Confirming an artifact exists before it is needed.** "The chart for 2.5.0 is not in the
repository" is a fact worth knowing before a release window rather than during one.

**The digest, so "which exact image" has an answer.** A tag names a moving target; a digest does
not. For a release record, the digest is the only identity that means anything a year later.

**Noticing that a tag was re-pushed.** A digest that has changed under a version Tower already knows
about is a fact a team almost never learns any other way, and it is the reason an artifact is shown
beside what Tower holds rather than merely looked up.

---

## Consequences

**No new domain concept, again.** Application Version, External Binding and provenance are reused
unchanged; the only new thing is a template, and templates are already how this codebase maps Tower
to vendors.

**A third Connector that produces no Observations**, after Issue Tracking and the build half of
CI/CD. That is no longer the exception Connector-Model.md once implied it was, and the corrected
sentence there — "a Connector whose subject is a running system produces Observations" — holds for
this one too.

**Three artifact kinds, not one.** The application, the image and the chart are each a template on
the same Application, because a team may have all three, any two, or one. Tower reports what each
template found and says nothing about the ones not configured.

**Not verifiable from where Tower is built.** No JFrog host is reachable from this environment, and
Artifactory publishes no OpenAPI description for ADR-019's schema check. This Connector will be in
the position Jenkins and Jira are in: tested against a local server, with the live check outstanding
in CHECKLIST.md. The API shapes named above are written from knowledge rather than from a response
recorded off a real instance, which is a weaker footing than the GitHub Connector stands on and is
recorded as such.

---

## Alternatives considered

**Store the artifact coordinates as facts about an Application Version.** Tempting, because it would
let a document print them without anybody accepting anything. Rejected: they would be facts Tower
read rather than facts Tower owns, straining IA-05 in the direction ADR-018 declined to go, and a
re-pushed tag would silently make stored data wrong with no way to notice.

**Treat promotion between Artifactory repositories as an Observation.** This is the one a reader
will keep coming back to, because `docker-prod` reads so much like Production. An artifact in a
repository is a file that has been copied. Nothing about it says anything is running, and folding
the two would put versions into Environments on the strength of a copy.

**Use AQL to search rather than composing a coordinate.** More flexible, and it would work for teams
whose tags are not derivable. It needs POST, which contradicts the structural form of ADR-001 that
this project has been careful to keep. If a team without a derivable convention ever needs support,
that is the moment to weigh an exception — and to weigh it explicitly, rather than having taken it
now for convenience.

**Have the CI/CD Connector report the artifacts a run produced.** Jenkins knows what it published,
and this would avoid a sixth Connector entirely. Rejected because it answers a different question: a
run says what was published at the time, the repository says what is there now, and the gap between
those two is exactly what a team wants to see. It is the same distinction ADR-020 drew between what
was done and what is true.
