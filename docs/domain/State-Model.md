# State Model

**Status:** Draft

**Owner:** Architecture

**Related:**

- Domain-Model.md
- Event-Model.md
- Scenarios.md

---

# Purpose

This document defines the lifecycle of a Release Pack within Tower.

Tower models progression through user-defined Promotion Paths.

Tower does not execute promotions.

It records the current observed state of a Release Pack and correlates that information with Environment observations.

---

# Principles

A Promotion Path is static.

A Release Pack moves through a Promotion Path.

Environments never move.

Tower never changes deployment state.

Tower only reflects observed deployment state.

---

# Promotion Path

A Promotion Path is an ordered list of Environments.

Example:

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

Another example:

```
Dev-Hotfix
      ↓
SIT
      ↓
UAT
      ↓
Production
```

Promotion Paths are created by users.

Tower never generates Promotion Paths automatically.

---

# Release Pack State

Every Release Pack has one current state.

The current state corresponds to the most advanced Environment in which the Release Pack has been successfully observed.

Possible states include:

- Planned
- Development
- Validation
- Pre-Production
- Production
- Archived

These states provide a logical business view.

Actual deployment progression is represented by Environment observations.

---

# Environment State

Each Environment maintains the currently observed deployment content.

An Environment always answers:

- Which Applications are deployed?
- Which Application Versions are deployed?
- Which Release Packs are represented?
- When was the Environment last observed?

Environment state is reconstructed from Observations.

---

# State Transitions

## Planned

Release Pack exists.

No deployments have been observed.

```
Create Release Pack
        │
        ▼
Planned
```

---

## Development

At least one Application Version belonging to the Release Pack has been observed in the first Environment of its Promotion Path.

```
Planned
    │
Observed in Dev
    ▼
Development
```

---

## Validation

The Release Pack has been observed in one or more validation Environments.

Examples include:

- SIT
- UAT

```
Development
      │
Observed in Validation
      ▼
Validation
```

---

## Pre-Production

The Release Pack has been observed in a pre-production Environment.

```
Validation
      │
Observed in PreProd
      ▼
Pre-Production
```

---

## Production

The Release Pack has been observed in the Production Environment.

```
Pre-Production
        │
Observed in Production
        ▼
Production
```

---

## Archived

The Release Pack is no longer actively progressing.

Historical information remains available.

No further state transitions occur.

---

# State Derivation

Tower derives state from Observations.

State is never manually assigned.

For example:

Observed:

```
Dev1
Version 2.5.0
```

Result:

Release Pack State

```
Development
```

Observed:

```
Production
Version 2.5.0
```

Result:

```
Production
```

---

# Environment Questions

Every Environment shall answer:

- What Applications are deployed?
- Which versions are deployed?
- Which Release Packs are represented?
- Which Observation produced this information?
- When was the Environment last synchronized?

---

# Release Pack Questions

Every Release Pack shall answer:

- Which Applications belong to me?
- Which versions belong to me?
- Which Promotion Path do I follow?
- Which Environment currently contains my latest observed deployment?
- Which validation Iterations have been completed?
- What Handover information has been prepared?

---

# Business Rules

SM-01

Promotion Paths are immutable after creation.

---

SM-02

Release Pack state is derived from Observations.

---

SM-03

Tower never changes deployment state.

---

SM-04

Only Observations may advance the observed position of a Release Pack.

---

SM-05

A Release Pack may exist before any deployment occurs.

---

SM-06

Historical Observations never change.

---

# Future Evolution

Future milestones may introduce richer lifecycle information.

Examples include:

- partially promoted Release Packs;
- rollback detection;
- Environment drift;
- historical comparisons.

These enhancements shall extend this model without changing its fundamental concepts.
