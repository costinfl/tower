# ADR-019 — Connector Fixtures Are Verified Against the Vendor's Own Description

**Status**

Accepted

---

## Context

The Jira Connector shipped green and proved very little.

Its test stands up a real HTTP server on a loopback port and serves bodies written by hand. Those bodies
were written from beliefs about what Jira returns — the same beliefs that wrote the Connector that reads
them. If a field name was guessed wrong, the fixture is wrong in the same direction, the Connector reads it
happily, and the suite passes. The test cannot fail for the one reason it exists.

Implementation-Plan.md had already named this failure mode, about a different claim:

> The audit log is the evidence for the read-only claim, because a test written against our own Connector
> could only confirm what we already intended.

That sentence was written about proving the Kubernetes Connector never writes. It applies word for word to
every canned response in every Connector test.

A second, quieter problem sat beside it. `IssueTrackerConnector` makes real promises in its Javadoc: omit an
identifier the tracker does not have rather than reporting it, never normalise a status, distinguish "not
there" from "could not ask", never let a credential into a message. Prose cannot fail a build. Each
Connector re-tested those promises in its own words, unevenly — GitHub asserted its 5xx behaviour, Jira
asserted its non-JSON behaviour, neither asserted both — and a third tracker would have started from
nothing.

---

## Decision

**Two mechanisms, addressing two different halves.**

**1. A conformance suite the SPI's promises are written into.** `IssueTrackerConnectorContract` in
`tower-testkit` asserts what every Issue Tracking Connector must do, and each Connector's own test extends
it. The subclass supplies only what is vendor-specific: how to point the Connector at the fake, what a
locator looks like for that vendor, and how that vendor would write an issue on the wire. A Connector that
starts returning an empty list where it should throw now fails a test it did not write.

**2. Fixtures checked against the vendor's own published API description.** The vendor's OpenAPI
description, sliced to the operations Tower reads, is committed under `specs/`. Every body a fake serves is
measured against it before it is served. A field invented from memory, nested in the wrong place, or given
the wrong type fails the build and names the field.

**Where a real response can be recorded, it is.** The GitHub fixtures are not written by hand at all: they
are responses recorded from the real api.github.com, with only the fields a test is about substituted. A
recording is stronger than a description, because a description can be wrong about its own server.

**Absence is stated, never silent.** Atlassian's description is not reachable from the environment Tower is
developed in, so `specs/jira/` is empty and the Jira schema check *skips with a message naming what is
unverified*. A test that quietly passes because its evidence is missing is worse than no test.

---

## What this does not establish

**A description is not a server.** Vendors' descriptions drift from their implementations, and Jira's are
known to. Nothing here proves that a real API answers the way its own document says it does.

This matters more than it sounds, because a large green suite is exactly the kind of thing that quietly
retires a manual check nobody has got round to. The live verification items in `docs/CHECKLIST.md` — a real
Jira site, the real-cluster checks — are **not** closed by this ADR, and the Jira Connector has still never
spoken to a Jira. That item is the evidence the scheduler decision waits on, and it stays open.

Neither does the description contain the failures that actually bite: an HTML login page returned with HTTP
200, rate limiting, redirects, undocumented error bodies. Those stay hand-written tests, because they are
things no description describes.

---

## Naming

This is **not** consumer-driven contract testing in the Pact sense. There is no provider running our
expectations against itself; a vendor has never heard of Tower. Calling it contract testing would claim a
guarantee nobody is offering.

It is two things, and they are worth naming separately: an **SPI conformance suite**, and **schema
conformance of fixtures against a vendor-published API description**.

---

## Consequences

**A new Connector is held to the promises before it is written.** Extending the contract is how a Connector
declares itself one, and the failures arrive in the vocabulary of the SPI rather than of HTTP.

**`tower-testkit` lives in `dev.tower.testkit`, not `dev.tower.connector.testkit`.** The architecture rule
`connectors_expose_no_write_operation` forbids classes under `dev.tower.connector..` from calling any method
named `write`, and serving a response body calls `OutputStream.write`. Naming the package for symmetry would
have meant either a failing rule or an exception carved into it. The rule is worth more than the symmetry.

**A vendor library is used in test scope, which ADR-014 and ADR-018 do not contradict.** Both of those
declined a vendor SDK, and this reaches for Atlassian's OpenAPI validator. Those decisions govern what
production code compiles against and what ships; `tower-testkit` is test support that no production class can
see, and no Connector's dependencies changed. The alternative — a bare JSON Schema validator — would mean
owning OpenAPI 3.0's departures from JSON Schema, `nullable` and boolean `exclusiveMinimum` among them, in
our own code. That is the wrong thing to own.

**Descriptions are vendored as slices, not wholesale.** GitHub's full description is 12.9 MB against a 96 KB
slice. `scripts/slice-openapi.py` keeps only GET operations, so a vendored description here cannot describe
a write even in principle, and writes deterministically, so a diff means the vendor changed something.

**Drift is checked on demand, not in CI.** `scripts/check-openapi-drift.sh` re-slices and diffs.
`scripts/acceptance.sh` was kept network-free for the same reason: a build that goes red because a vendor
edited a document is a build that went red for something the diff did not do.

**The validator is itself tested.** `VendorDescriptionTest` corrupts a real recorded response — removes a
required field, changes a type, moves a field — and requires the check to catch each one. A validator that
passes everything is a green tick that means nothing, and the first draft of that test proved the point by
corrupting a field the fixture did not have and passing an untouched body.

---

## Alternatives considered

**A helper module that mimics the vendor's server from its description.** Routing, validating and answering
like the real API. Rejected as mostly redundant: the conformance suite and the schema check already deliver
what it would, at a fraction of the cost, and a fake that implements only what a description documents still
misses every failure listed above under *What this does not establish*.

**Doing only the conformance suite.** Cheapest, and it fixes the weaker half. It leaves untouched the thing
that prompted this: whether the fixtures resemble what the vendor actually sends.

**Recording real responses for every vendor and skipping descriptions.** Strongest where it is possible, and
it is what GitHub does here. It is not possible for a vendor nobody can reach, which is precisely the
Connector with the least evidence behind it.
