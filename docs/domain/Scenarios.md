# Scenarios

**Status:** Draft

**Owner:** Architecture

**Related:**

- Domain-Model.md
- Event-Model.md
- State-Model.md

---

# Purpose

This document captures representative business scenarios that validate the Tower domain model.

The scenarios describe expected behavior from a business perspective.

They are not implementation specifications.

---

# Scenario 1 - Standard Release

## Description

A development team prepares a Release Pack containing multiple Applications that will be promoted through the regular delivery pipeline.

## Promotion Path

```
Dev1
    ↓
SIT1
    ↓
UAT
    ↓
PreProd
    ↓
Production
```

## Flow

1. A Release Pack is created.
2. Application Versions are associated with the Release Pack.
3. A Promotion Path is assigned.
4. Developers prepare Handover information.
5. Tower observes deployments in each Environment.
6. Tower updates the current observed position of the Release Pack.
7. Release documentation is generated from the Canonical Model.

## Expected Outcome

Tower always answers:

- Which Applications belong to the Release Pack?
- Which versions are included?
- Which Environment currently contains the latest observed deployment?
- What deployment information must be handed over?

---

# Scenario 2 - Parallel Releases

## Description

Two independent Release Packs progress simultaneously through different Promotion Paths.

Example:

Release A

Technology Upgrade

Release B

Business Features

Both releases may coexist without interfering with each other.

## Expected Outcome

Tower maintains independent Release Packs.

Each Release Pack has:

- its own Promotion Path;
- its own Handover;
- its own validation Iterations;
- its own observed Environment progression.

---

# Scenario 3 - Production Hotfix

## Description

A defect is discovered after a Production deployment.

A Hotfix Release Pack is created.

The Hotfix follows a shorter Promotion Path.

## Promotion Path

```
Dev-Hotfix
      ↓
SIT
      ↓
UAT
      ↓
Production
```

## Expected Outcome

Tower distinguishes the Hotfix Release Pack from regular Release Packs.

The Production Environment reflects the Hotfix deployment after it has been observed.

---

# Scenario 4 - Environment Visibility

## Description

A developer selects an Environment.

Tower displays the current observed deployment state.

## Expected Information

- deployed Applications;
- deployed Application Versions;
- Release Packs represented;
- last Observation timestamp.

Tower does not estimate missing information.

Only observed information is displayed.

---

# Scenario 5 - Release Pack Visibility

## Description

A developer selects a Release Pack.

Tower presents its complete business context.

## Expected Information

- Applications;
- Application Versions;
- Promotion Path;
- current observed position;
- Iterations;
- Handover information.

---

# Scenario 6 - Technology Migration

## Description

A long-running infrastructure migration introduces a new integration endpoint.

Applications migrate independently over multiple releases.

Some Applications continue using the legacy endpoint while others use the new endpoint.

## Expected Outcome

Tower records only the observed Application Versions deployed in each Environment.

Tower does not infer compatibility.

Developers determine whether an Environment represents a valid integration state.

---

# Scenario 7 - Environment Drift

## Description

An Environment contains Application Versions that no longer match the intended Release Pack.

## Expected Outcome

Tower reports the observed state.

Tower does not automatically determine whether the drift is acceptable.

Observed information always takes precedence over assumptions.

---

# Scenario 8 - Documentation Generation

## Description

A Release Pack has completed its validation activities.

Developers request release documentation.

## Expected Outcome

Tower generates documentation using information already present in the Canonical Model.

Typical output includes:

- Release Pack contents;
- Application Versions;
- Promotion Path;
- deployment instructions;
- database migration references;
- validation Iterations;
- Handover information.

Generated documentation does not become the source of truth.

The Canonical Model remains authoritative.

---

# Scenario 9 - A Release That Carries Database Changes

## Description

Two teams deliver database changes in the two ways this setting actually uses (ADR-022).

One keeps its migrations inside the application repository and runs them with Flyway, so the schema
change travels inside the Application Version and is deployed by the same job.

The other keeps its scripts, changelogs and changesets together in one repository, organised by a
convention of its own. What is delivered from it is a jar wrapping the Liquibase libraries, invoked
from a bash script and versioned with SemVer through Maven, exactly as a web application is.

A release that changes two Oracle schemas is two runs of that one jar with different parameters. The
developer writes, in that Release Pack's Handover, which script runs against which schema, for the
release management team who will execute it.

## Expected Outcome

Tower expresses both with the concepts it already has, and needs no new one.

For the first team, nothing is recorded about the schema at all. The Application Version is the
record, and a separately stored schema version would be a second account of the same fact, free to
disagree with it.

For the second, the jar is an Application and its version is an Application Version, sitting in the
Release Pack beside the code it must agree with. Nothing is registered for a schema: nothing called
"Orders schema" was ever built, and Tower does not invent deliverables for the things software is
pointed at.

Which schema each run targets is Handover information. Tower stores it, versions it so an
instruction is never silently rewritten, and prints it in the release document beside the versions
it belongs to. One document then carries both halves of what a release management team is handed:

- the migration jar's version, in the Release Pack contents;
- the per-schema instructions, in the Handover, printed as the developer wrote them.

Tower does not judge whether the versions in an Environment are compatible with each other. That is
the same refusal as Scenario 6.

Tower does not read the databases themselves. What it holds was either stated by a person or
collected from the job that applied the change — never from the history table inside the database
being migrated.

The limit is deliberate and known: Tower reports that the jar reached an Environment, not that the
run against one schema succeeded while the run against another failed. Both runs applied the same
Application Version. That difference lives in what the release management team reports back, and
ADR-022 records what would have to be true for Tower to hold it instead.

---

# Validation

The scenarios defined in this document validate that the Domain Model supports Tower's primary business objectives.

Every future feature should be evaluated against these scenarios before being added to the product.
