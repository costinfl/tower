# ADR-022 — A Migration Bundle Is An Application; A Schema Is A Parameter

**Status**

Accepted

**Revised once, before anything was built**

The first version of this ADR ruled that several schemas from one repository are several
Applications, one per schema. That was written from a two-sentence description of the arrangement
and is wrong. The arrangement was then described properly — one Maven-versioned jar, run once per
schema with parameters — and the ruling it produces is the opposite one: a schema is not a
deliverable at all. The mistake is recorded rather than quietly overwritten, because it is a good
example of a model that fits a description instead of a fact, which is the failure ADR-020 was
written about.

---

## Context

OQ-018 asked how a release accounts for the database changes it carries. Two shapes are in use, and
the second one is what needed deciding.

### Migrations inside the application repository

Flyway runs them as part of deploying the application. The schema change travels inside the
Application Version, through the same pipeline, in the same release. What is deployed to an
Environment is one thing.

### A migration bundle delivered on its own

The scripts, changelogs and changesets live together in one Bitbucket repository, organised by a
convention the team invented. What is delivered from it is a **jar**: a thin wrapper over the
Liquibase libraries, invoked from a bash script.

That jar is versioned with **SemVer 2.0, through Maven, exactly as a web application is** — Maven is
there because the jar has dependencies to manage, and the version numbering follows from that rather
than from anything about databases.

A release that changes two Oracle schemas is **two runs of that one jar with different parameters**.
Not two deliverables. One artifact, pointed at a different schema each time.

Which script runs against which schema is written by the developer, per Release Pack, **in the
Handover**, for the release management team who will execute it.

That last pair of facts decides this ADR, and the first version of it was written without them.

### What was tempting and is wrong

**A schema version as a property of an Application Version.** It fits the Flyway shape and is nearly
free. It is useless for the other shape, and Tower must serve both. Worse, in the shape it does fit,
it would be a second account of a fact the Application Version already carries, free to disagree
with it — and IA-05 asks that a displayed value have one origin, not two.

**A second kind of Application Version.** Cheap, and it answers a question nobody asked: the jar is
already an ordinary versioned artifact, so a second kind of anything is machinery around something
that needs none.

**One Application per schema.** This was the first version of this ADR, and it is worth saying
exactly why it is wrong, because it is superficially attractive: it would let Tower report that the
ORDERS schema is at 4.2 while CUSTOMERS is still at 4.1, with no new concept.

It buys that by inventing deliverables nobody builds. Nothing called "Orders schema" is ever
compiled, versioned or published; the version numbers it would carry are the **jar's**, borrowed. The
Glossary says an Application Version "uniquely identifies software intended for deployment", and a
schema is not software intended for deployment — it is what software is pointed at. A model that
registers targets as deliverables will read plausibly right up until somebody asks where "Orders
schema 4.2" was built, and the answer is nowhere.

---

## Decision

### No new domain concept

Both shapes are expressed with Application, Application Version, Release Pack, Observation and
Handover, exactly as they stand. Nothing below requires a line of code.

### Migrations inside the application repository need nothing

The migration travels inside the Application Version, and Tower shall **not** record a separate
schema version for this shape, for the reason above.

### The migration jar is an Application, and an unremarkable one

It is built by Maven, versioned with SemVer, tagged in a git repository, published, and delivered
into an Environment. That is an Application in every sense Tower uses the word. The word is about
**being deployable**, not about being a service — worth saying out loud, because a reader who
assumes "Application = service" will find this strange for about a minute.

The consequence worth stating is how little follows from it. Every existing part of Tower already
works on this artifact with no special case:

- **Version discovery** reads the repository's tags through an ordinary version pattern (ADR-012),
  because SemVer tags are what it already expects.
- **Artifact confirmation** composes its coordinate from version and commit like any other
  (ADR-021).
- **The Release Pack** carries its version beside the code it must agree with, which is what a
  Release Pack is for.
- **The release document** lists it in contents like any other member.
- **Bitbucket needs no Connector.** ADR-014 decided that source control is read as git rather than
  through a vendor API, and this is the first case that collects on that decision: the repository is
  Bitbucket and nothing in Tower has to know.

That it happens to migrate a database is not something Tower needs to know, and this ADR's real
content is that Tower should not be taught.

### A schema is a parameter of a run, not a deliverable

Two schemas are two runs of one jar with different parameters. Tower records the Application
Version — once — and says nothing about schemas.

Which schema each run targets, in what order, with which parameters, is **Handover information**.
That is not a fallback. It is an instruction from a developer to the people who will execute it,
which is precisely what Handover is: User-Owned Information, versioned so an instruction is never
silently rewritten (ADR-016), and printed in the release document beside the versions it belongs to.

The team already writes it. Tower already stores it, versions it and prints it. The correct amount
of new machinery is none.

### Tower shall not read the migrated database

Flyway keeps `flyway_schema_history` and Liquibase keeps `DATABASECHANGELOG` **inside the database
being migrated**. Collecting a schema's level from there means Tower holding a credential to a
production Oracle database, which is a different class from every credential it holds today — all of
them for delivery systems: a cluster's API, a git remote, a CI server, an artifact repository.

The difference is not squeamishness. ADR-001's read-only guarantee is structural: a Connector cannot
name a write method, and the build fails if one does. A database credential is **not structurally
read-only**. The same credential that selects from a history table can drop it, and no architecture
rule of Tower's can make that untrue. Tower's central promise would rest on restraint for the first
time, in the place where being wrong is least recoverable.

Where a level is to be collected rather than stated, it shall come from **the job that applied it** —
a deployment run under ADR-020, a delivery-system read Tower already performs. That is weaker
evidence, and Tower already lives with the weakness and names it: a run says what was applied, not
what is there now. A person stating it directly remains a Collector like any other (ADR-006).

### The limit of this, stated rather than discovered

Tower will say that db-migrations 4.2 reached UAT. It will **not** say that the ORDERS run succeeded
and the CUSTOMERS run did not. Both runs applied the same Application Version, and an Observation is
per Environment, so the two runs are indistinguishable to Tower.

Today that difference lives where it is produced: in what the release management team reports back,
and in the release's validation notes. If it ever needs to be a fact **inside** Tower — queryable,
comparable, on a dashboard — then a schema has become a place, and Environment is the only place
Tower has ever had. That is the trigger for a Schema concept, and it is a good deal closer than the
triggers the first version of this ADR named. The other one remains: a schema per tenant, at
different levels, which an Application cannot express either.

---

## Consequences

**Nothing is built.** No type, no endpoint, no screen, no migration. Verified by expressing both
shapes end to end against a running Tower — Scenario 9 — rather than by asserting that they fit.

**A release that ships a database change looks like one that does not.** Intended. The jar is a
member of the pack; the instructions are in the Handover; both appear in one document, which is
what a release management team is handed today.

**Tower gives an Application no "kind".** A kind invites behaviour that differs by kind, and that is
how a small model stops being small. The name says what it is.

**Environment-level granularity is the accepted cost**, and it is written down above rather than
left for somebody to hit.

**OQ-018 is answered** before anything was implemented, which is what the question asked for: "not
to be answered by whichever shape gets implemented first."

---

## Alternatives considered

**One Application per schema.** The first version of this ADR. Rejected above: it registers targets
as deliverables and borrows one artifact's version numbers for things that were never built. It is
recorded here rather than deleted because it is the answer a reader will re-invent, and the reason it
fails is not obvious until the arrangement is described precisely.

**A Schema as a first-class concept with its own Observations.** The honest maximal answer, and it
would handle the limit named above on the day it was built. Rejected for now because it gives Tower
a second kind of place — a change to the Domain Model's spine — to record a distinction that
currently lives in a report nobody has asked Tower to replace. The trigger is written down so the
case arrives as evidence rather than as an argument.

**A Database Connector reading the history table.** The most accurate answer available: it says what
*is* rather than what was applied. Rejected on the credential class above. Worth revisiting only if
a read-only database role can be made structural rather than promised — and that is a claim about
somebody else's database server, which Tower cannot verify from inside itself.

**Leaving it entirely in Handover prose, with no Application at all.** Nearly this ADR, and it is
what happens today for the Flyway shape. Rejected for the separate bundle because the jar genuinely
is a deliverable with a version and an artifact, and a release that does not name it cannot say which
version of the migrations it shipped. The prose says what to run; the Application Version says what
was shipped. Both are needed, and neither is a substitute for the other.

**Treating a schema as an Environment.** Mentioned only to close it off, because "the ORDERS schema
in UAT" reads like a place inside a place. An Environment is where software runs and what a Promotion
Path is a sequence of. Making schemas Environments would put them into promotion sequences and onto
the dashboard as places releases converge on — the same category error ADR-021 refused when
`docker-prod` looked like Production.
