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

The other keeps its changelogs in a repository of their own and runs them with Liquibase, from a
separate build, on a separate schedule. One run addresses several schemas, and each schema can reach
a different level: a changeset that fails against one schema leaves the others where the run put
them.

## Expected Outcome

Tower expresses both with the concepts it already has, and needs no new one.

For the first team, nothing is recorded about the schema at all. The Application Version is the
record, and a separately stored schema version would be a second account of the same fact, free to
disagree with it.

For the second, each schema is an Application, its changelog level is an Application Version, and
those versions sit in the Release Pack beside the code they must agree with. Two schemas at
different levels in one Environment is then an ordinary Environment state:

- Orders schema 4.2 observed in UAT;
- Customers schema 4.1 observed in UAT;
- Customer API 2.5.0 observed in UAT;
- all three named in the Release Pack, and all three in the generated document's contents.

Tower does not judge whether those three levels are compatible. That is the same refusal as
Scenario 6: it reports what was observed and leaves the integration question to the developers.

Tower does not read the databases themselves. What it holds about a schema was either stated by a
person or collected from the job that applied the change — never from the history table inside the
database being migrated (ADR-022).

---

# Validation

The scenarios defined in this document validate that the Domain Model supports Tower's primary business objectives.

Every future feature should be evaluated against these scenarios before being added to the product.
