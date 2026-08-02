# ADR-020 — A Deployment Run Is an Observation, a Build Run Is Not

**Status**

Accepted

---

## Context

The CI/CD Connector is the fourth of the five categories Connector-Model.md names, and the only one
never scheduled. Milestone 2's scope named two Connectors — Source Control and Deployment Platform —
and the Issue Tracking Connector was built later on request. CI/CD has no Functional Requirements, no
milestone and no issue. It exists as a heading.

Connector-Model.md § Observation Mapping contains this:

```
Pipeline Execution
        │
        ▼
Observation
```

ADR-018 corrected the claim standing next to it — "every Connector ultimately produces Observations"
— when the Issue Tracking Connector turned out not to. This diagram was left alone, because at the
time there were no grounds to touch it. Building the category means having grounds.

The question it hides is not small. **A pipeline tells you what was done. A platform tells you what is
true.** If a deployment succeeded and somebody rolled it back by hand an hour later, the pipeline
record still says the version was deployed, and says so for ever. Tower's stated purpose is to show
what is actually running. A record of an action folded into Environment state as though it were a
sighting would make Tower assert a version is live because a pipeline once said it deployed it.

### The system this is for

The intended target is Jenkins, used in a way its owner describes as suboptimal, with a fair chance
of being decommissioned within a year.

Both facts belong in this decision rather than beside it. The second in particular invites the
conclusion "then do not build it", and that conclusion is wrong for a reason worth writing down: the
part of this work that takes the time is vendor-neutral and outlives Jenkins, and the part that dies
with Jenkins is a few hundred lines of HTTP and JSON. The GitHub and Jira Issue Tracking Connectors
demonstrated the ratio twice — they share an SPI, a Collector, a binding, an API and a screen, and
differ only in the module that speaks to the vendor.

---

## Decision

### A pipeline run is not one thing

The category conflates two kinds of event, and they land in different places.

**A build run produces a candidate Application Version.** A build says "version 2.5.0 was produced
from commit abc123". That is not a fact about any Environment, and BR-01 makes an Application Version
immutable, so one created without anybody asking would be immutable too. It is proposed and stored
nowhere — exactly the footing ADR-014 established for a git ref, and exactly what
`SourceControlCollector` already does. A build is a second source of the same kind of candidate.

**A deployment run produces an Observation.** ADR-006 settles this, and settles it without new
machinery. That decision widened the definition from *a fact collected from an external system* to
**a fact collected from an identified source**, and admitted the Manual Collector on that basis: a
person stating that a version is in an Environment produces a real Observation, carrying who said so
and when. A Jenkins record of a successful deployment is a claim of the same kind from a better
witness — machine-recorded, timestamped, made by the actor that performed the action. If a human
typing it qualifies, this qualifies.

Connector-Model.md § Observation Mapping is corrected accordingly: a **deployment** execution maps to
an Observation, a **build** execution maps to a candidate Application Version, and the undifferentiated
"Pipeline Execution → Observation" goes.

### Only a successful deployment run counts

A failed, aborted or unstable run is not evidence that anything reached an Environment. Such runs are
**reported and not recorded**, the same treatment FR-060 gives a workload Tower cannot attribute. A
run whose outcome Tower does not recognise is reported too, rather than assumed to have succeeded.

### What a deployment Observation does not claim

It does not claim the version is running now. It claims that at instant T an identified source
reported putting it there.

Nothing new is needed to keep that honest, because the model already does it in two ways. Observations
are timestamped and immutable, and Environment state is the latest Observation per Environment and
Application — so a later reading from the Deployment Platform Connector supersedes an earlier Jenkins
record without argument. And every Observation already carries its provenance, which the Viewer
already shows: a Jenkins-sourced sighting is visibly a Jenkins-sourced sighting, exactly as a
manually entered one is visibly manual.

The residue is real and is stated rather than solved: if a deployment is undone by a route Jenkins
never sees, and Tower cannot reach that Environment either, Tower will go on showing the deployed
version. That is a limit of the evidence, not a defect in the recording, and it is the same limit
ADR-006 accepted for manual entry.

### What the category is actually for

Two things, and neither is "more Observations".

**When.** ADR-011 appends an Observation when a synchronization run finds something different from
what Tower holds, so a polled Observation is stamped with *when Tower looked*, not when the
deployment happened. A deployment run knows the real instant. For the progression history ADR-017
derives, that is the difference between "arrived sometime before Tuesday's sync" and "arrived at
14:32 on Monday".

**Where Tower cannot reach.** The Deployment Platform Connector needs credentials for the platform.
CI/CD needs credentials only for the CI system, which already had that access. For an Environment
Tower cannot read directly — vendor-managed, customer-hosted, behind a boundary nobody will open for
a reporting tool — a deployment run may be the only evidence available.

### Attribution is a binding, and it has a precondition

Which job deploys which Application to which Environment, and where in that job the version appears,
is configuration a Connector cannot infer. It is an External Binding in the sense of ADR-012, and it
carries the version pattern that decision already established, including the preview affordance —
because ADR-012's warning applies verbatim here: a wrong pattern produces wrong Application Versions,
and Observations are immutable, so those outlive the correction.

The precondition is worth stating plainly, because no amount of connector will substitute for it: **a
job that does not record the version anywhere a machine can read cannot be attributed.** Not in a
build parameter, not in the job name, not in a recorded artefact — then Tower can report that the job
ran and nothing more. Saying so early is cheaper than discovering it after a module exists.

### Vendor-neutral first, Jenkins last

The service provider interface is defined in terms of what CI systems generally offer — a job, a run,
an outcome, an instant, a set of named values — and names nothing of Jenkins. `tower-connector-jenkins`
is written afterwards, is the only place Jenkins' shape is known, and is a runtime-scope dependency of
tower-api like every other Connector, so ArchUnit governs it.

This is ADR-003 doing the job it was written for. When Jenkins goes, that module goes, and the SPI,
Collector, binding, persistence, API and screen stay for whatever replaces it.

---

## Consequences

**No new domain concept**, unlike ADR-018. Observation, Collector, External Binding and provenance are
reused as they stand. That is the strongest evidence the decision is right: the pieces fit without
being reshaped.

**A second source of candidate versions.** A team that tags releases and a team that versions by build
number are both served, and the version discovery screen gains a source rather than a mode.

**Jenkins Observations can go stale** in Environments Tower cannot otherwise reach. Provenance and
supersession contain it; nothing hides it.

**Re-reading must not duplicate.** A synchronization run reads runs it has not seen before, identified
by job and run number, and ADR-011's rule does the rest: an Observation is appended only where what was
read differs from what Tower holds. Re-running a job for the same version in the same Environment
records nothing new, which is correct — nothing changed.

**Not verifiable live from the development environment.** No Jenkins host is reachable from where
Tower is built, so this Connector will be in the position the Jira Connector is in: tested against a
local server, with the live check outstanding in CHECKLIST.md. ADR-019's machinery applies —
conformance suite, and fixtures checked against a description if one can be obtained — and Jenkins
publishes no OpenAPI description, so recorded responses from a real instance are the only upgrade
available, and they need somebody with a Jenkins.

---

## Alternatives considered

**Treat every pipeline run as an Observation**, as the diagram implied. It would record builds as
though they were deployments, putting versions into Environments on the strength of having been
compiled.

**Treat no pipeline run as an Observation**, showing runs beside a release the way work items are
shown. It loses the only two things the category is good for, and it contradicts ADR-006: if a person
typing a sighting produces an Observation, a machine record of the same event cannot fail to.

**Wait for the Jenkins decision.** Superficially prudent, and wrong on the numbers. The vendor-neutral
work is the larger part and survives the decision either way; the successor needs the same SPI; and
the alternative to building now is building the identical thing later with less time.

**Read the console log to find versions** where a job records them nowhere else. Rejected: it makes
Tower's correctness depend on log formatting, and a wrong parse produces immutable wrong Observations.
Where a job records nothing, Tower says so.
