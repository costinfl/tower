# ADR-022 — A Schema Is An Application, Not A New Kind Of Thing

**Status**

Accepted

---

## Context

OQ-018 asked how a release accounts for the database changes it carries, and recorded that the
setting this is written for does not have one answer. It now has a sharper statement of the problem
than the question was written with:

> Most projects use Flyway in the application repository, but the Liquibase one is equally
> important, because it addresses multiple schemas.

Two shapes, then, and the second is not a rare exception to be handled later.

**Migrations inside the application repository.** Flyway runs them as part of deploying the
application. The schema change travels with the Application Version, through the same pipeline, in
the same release. What is deployed to an Environment is one thing.

**Migrations in a repository of their own.** Liquibase runs them from a separate build, on their own
schedule, and one run addresses **several schemas**. What is deployed to an Environment is two
things that must agree — and on the database side, several things that can each succeed or fail on
their own.

That last point is what decides this ADR. A multi-schema changelog run does not fail as a unit. The
Orders schema reaching 4.2 while the Customers schema stops on a failed changeset is the normal
failure of this arrangement, not an exotic one, and any model that cannot express it will be wrong
in exactly the situation a team most needs it to be right.

### What was tempting and is wrong

**A schema version as a property of an Application Version.** It fits the Flyway shape perfectly and
is nearly free. It is useless for the other shape — there is no Application Version to hang it on
when the changelog ships from its own repository — and Tower must serve both. Worse, in the shape it
does fit, it would be a *second account of the same fact*: the migration is already inside the
Application Version, and a separately recorded schema version could disagree with it, with nothing
in Tower able to say which one is right. IA-05 asks that every displayed value have one origin, and
this would give one value two.

**A second kind of Application Version.** Cheap and reuses everything, and it collapses the moment
several schemas move from one build. One build produces one version; the version reaches five
schemas separately. A single second-kind version cannot say which of the five it reached.

---

## Decision

### No new domain concept

Both shapes are expressed with Application, Application Version, Release Pack and Observation,
exactly as they stand. Nothing below requires a line of code.

### Migrations inside the application repository need nothing

The migration travels inside the Application Version. Tower shall **not** record a separate schema
version for this shape, for the reason above: it would be a second account of a fact the Application
Version already carries.

What a person needs to *do* about a migration — a manual step, an order of operations, a rollback
plan — is already Handover's free-text "database migrations" field (FR-028), and stays prose. It
answers a different question from any of the below, and prose is the right shape for it.

### A migration bundle delivered on its own schedule is an Application

It is built, versioned, published as an artifact, and applied to an Environment. That is precisely
what Tower means by an Application. The word is about **being deployable**, not about being a
service, and this ADR is the moment to say so out loud, because a reader who assumes "Application =
service" will find the ruling strange.

Its version goes in the Release Pack beside the code it must agree with. "Two things that must
agree, travelling together" is not a workaround here — it is the definition of a Release Pack, and
this is the case it was shaped for.

### Several schemas from one repository are several Applications

One Application per schema — `Orders schema`, `Customers schema` — each carrying the changelog
version that actually reached it. Several repository bindings then point at one repository, which is
allowed and means what it says: these deliverables come from the same place.

This is the ruling that costs something, and the cost is stated rather than discovered: **the same
version string is registered once per schema**, and a five-schema changelog is five registrations
and five Observations rather than one of each.

What that buys is the failure the arrangement actually has. "Orders schema 4.2 is in UAT and
Customers schema is still at 4.1" is an ordinary Environment state, needing no new word. "These five
moved together" is an ordinary Release Pack. A single Application for the whole bundle would have
been one registration instead of five and could not have said either thing.

### Tower shall not read the migrated database

Flyway keeps `flyway_schema_history` and Liquibase keeps `DATABASECHANGELOG` **inside the database
being migrated**. Collecting a schema's level from there means Tower holding a credential to a
production database, which is a different class from every credential it holds today — all of them
for delivery systems: a cluster's API, a git remote, a CI server, an artifact repository.

The difference is not squeamishness. ADR-001's read-only guarantee is structural: a Connector cannot
name a write method, and the build fails if one does. A database credential is **not structurally
read-only**. The same credential that selects from a history table can drop it, and no architecture
rule of Tower's can make that untrue. Tower's central promise would rest on restraint for the first
time, in the one place where being wrong is least recoverable.

Where a schema's level is to be collected rather than stated, it shall be collected from **the job
that applied it** — a deployment run under ADR-020, which is a delivery-system read Tower already
performs. That is weaker evidence, and Tower already lives with the weakness and names it: a run
says what was applied, not what is there now. A person stating it directly remains a Collector like
any other (ADR-006).

### What would make this wrong

Recorded now, while it is cheap to say, because ADR-020's lesson was that a model can fit one team's
arrangement and silently misrepresent another's:

- **The same schema more than once in one Environment** — a schema per tenant, at different levels.
  An Application is observed in an Environment once; this cannot express it.
- **A level that is not a version string** — a schema described by a set of applied changesets rather
  than by a number it has reached.

Either one means the schema is a *place* rather than a deliverable, and Environment is the only
place Tower has ever had. That is when a Schema concept becomes necessary, and not before.

---

## Consequences

**The Applications list will contain things that are not services.** That is intended. Tower shall
not give an Application a "kind", because a kind invites behaviour that differs by kind, and that is
how a small model stops being small. The name says what it is; nothing else needs to.

**A release document already names them.** A migration Application Version in a Release Pack appears
in the document's contents like any other member, in all three renderers, with no change. Traceable
by the same route as everything else (IA-05).

**Nothing is built.** This ADR adds no type, no endpoint, no screen and no migration. It is verified
by expressing both shapes end to end against a running Tower — Scenario 9 — rather than by
asserting that they fit.

**OQ-018 is answered**, and answered before anything was implemented, which is what the question
asked for: "not to be answered by whichever shape gets implemented first."

---

## Alternatives considered

**A Schema as a first-class concept with its own Observations.** The honest maximal answer, and it
would handle both triggers listed above on the day it was built. Rejected for now because nothing yet
needs it that an Application cannot express, and it would give Tower a second kind of place — a
change to the Domain Model's spine, made speculatively. The triggers are written down so the case
for it arrives as evidence rather than as an argument.

**A Database Connector reading the history table.** The most accurate answer available: it says what
*is* rather than what was applied. Rejected on the credential class above. Worth revisiting only if
a read-only database role can be made structural rather than promised — and that is a claim about
somebody else's database server, which Tower cannot verify from inside itself.

**Leaving it in Handover prose.** Where it lives today. It answers "what must a person do", and
never answers "where is the Orders schema right now" — the question that made this worth deciding.
Kept, unchanged, alongside.

**Treating a schema as an Environment.** Mentioned only to close it off, because "the Orders schema
in UAT" reads like a place inside a place. An Environment is where software runs and what a
Promotion Path is a sequence of. Making schemas Environments would put them into promotion
sequences and onto the dashboard as places releases converge on, which is the same category error
ADR-021 refused when `docker-prod` looked like Production.
