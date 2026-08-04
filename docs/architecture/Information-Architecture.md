# Information Architecture

**Status:** Draft

**Owner:** Architecture

**Related:**

- Context.md
- Connector-Model.md
- ../domain/Domain-Model.md

---

# Purpose

This document defines how information is organized inside Tower.

The Information Architecture identifies:

- where information originates;
- who owns it;
- how it flows through the system;
- how it becomes useful to users.

The objective is to establish a single Canonical Model from which every visualization and generated document is produced.

---

# Architectural Principle

Tower never presents raw information directly from External Systems.

Every piece of information passes through normalization before becoming part of the Canonical Model.

This guarantees consistency regardless of the originating system.

---

# Information Sources

Information enters Tower from two categories.

## Observed Information

Observed Information originates from External Systems.

Examples include:

- deployed Application Versions;
- Git branches;
- build identifiers;
- pipeline executions;
- deployment timestamps.

Observed Information is immutable.

Tower never edits it.

---

## User-Owned Information

User-Owned Information is created inside Tower.

Examples include:

- Release Packs;
- Promotion Paths;
- Handover information;
- deployment notes;
- release objectives;
- accepted work item titles;
- accepted artifact digests.

Tower is the authoritative owner of this information.

The last two are worth a sentence, because they arrive through a Connector and could be mistaken for
information Tower read. They are not. A tracker's current wording and a repository's current digest
are read, shown, and discarded; what is owned here is the wording and the digest a **person looked at
and accepted**, and from that moment they are Tower's own (ADR-018, ADR-021). The distinction is what
lets a generated document regenerate byte-identically (NFR-025) after somebody rewrites a ticket
summary or pushes a tag over.

---

# Canonical Model

The Canonical Model combines:

- Observed Information;
- User-Owned Information.

The Canonical Model is the only source used by:

- Viewer;
- Documentation Generation;
- Future reporting capabilities.

No feature should bypass the Canonical Model.

---

# Information Flow

```
External Systems
        │
        ▼
Connectors
        │
        ▼
Collectors
        │
        ▼
Observations
        │
        ▼
Canonical Model
        │
        ├────────► Viewer
        │
        ├────────► Documentation
        │
        └────────► Future Capabilities
```

---

# Information Ownership

## Owned by External Systems

Examples include:

- repositories;
- commits;
- branches;
- pipelines;
- deployments;
- issues;
- documentation pages.

Tower references this information.

Tower never owns it.

---

## Owned by Tower

Tower owns:

- Release Packs;
- Promotion Paths;
- Handover information;
- Intent;
- Canonical Model.

Only Tower may modify these concepts.

---

# Information Classification

Information is classified into four categories.

## Reference Information

Information owned elsewhere and referenced by Tower.

Examples:

- issue identifiers;
- commit hashes;
- build numbers.

---

## Observation Information

Immutable facts collected by Collectors.

Examples:

- deployed version;
- deployment timestamp;
- observed Environment.

---

## Business Information

Developer-owned concepts.

Examples:

- Release Packs;
- Promotion Paths;
- validation Iterations.

---

## Generated Information

Derived information produced by Tower.

Examples:

- release documentation;
- environment summaries;
- release views.

Generated Information is reproducible.

It should never become the authoritative source.

---

# Information Integrity

The following principles apply.

IA-01

Observed Information is immutable.

---

IA-02

User-Owned Information is versioned by Tower.

---

IA-03

Generated Information is disposable.

It can always be regenerated from the Canonical Model.

---

IA-04

Duplicated information shall be minimized.

---

IA-05

Every displayed value should be traceable to either:

- an Observation; or
- User-Owned Information.

---

# Traceability

Every generated artifact should be traceable back to its source.

Examples:

```
Release Documentation
        │
        ▼
Release Pack
        │
        ▼
Application Version
        │
        ▼
Observation
```

```
Environment View
        │
        ▼
Canonical Model
        │
        ▼
Observation
```

Users should always understand where information originated.

---

# Architectural Constraints

The Information Architecture shall guarantee:

- one Canonical Model;
- one ownership for every piece of information;
- no direct dependency between Viewer and External Systems;
- deterministic documentation generation.

---

# Success Criteria

The Information Architecture is successful when:

- every business concept has a clear owner;
- generated documentation can always be reproduced;
- no feature bypasses the Canonical Model;
- replacing an External System does not change business information.
