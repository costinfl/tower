# ADR-004 — Release Pack is the Central Business Concept

**Status**

Accepted

---

## Context

Tower exists to assist development teams in preparing, understanding and tracking software releases.

A release is rarely composed of a single application.

Instead, multiple applications, repositories and technologies are delivered together and must be understood as one logical unit.

Developers also prepare release documentation, deployment instructions and validation information around that logical unit.

A central concept is required to organize all developer-owned information.

---

## Decision

The Release Pack is the central business concept of Tower.

Every major capability shall relate to a Release Pack.

A Release Pack groups together:

- Application Versions;
- Promotion Path;
- Handover information;
- Validation Iterations;
- developer-owned metadata.

Release Packs do not represent deployments.

Deployments are represented by Observations.

Tower correlates Observations with Release Packs to determine the currently observed progression of a release.

---

## Consequences

Positive

- provides a clear business focus;
- aligns with the daily workflow of development teams;
- simplifies documentation generation;
- allows multiple applications to be managed as a single release;
- separates release planning from deployment observations.

Negative

- applications may belong to multiple Release Packs over time;
- Release Packs require correlation with Observations to determine deployment progress.

---

## Alternatives Considered

### Application as the Primary Concept

Rejected.

Applications are deployment artifacts.

Tower's purpose is understanding releases rather than managing individual applications.

---

### Environment as the Primary Concept

Rejected.

Environments describe deployment locations.

They do not represent the developer's planning and release process.

---

### Deployment as the Primary Concept

Rejected.

Deployments belong to operational tooling.

Tower intentionally focuses on release coordination and documentation.

---

## Related Documents

- Vision.md
- Domain-Model.md
- CRC-Cards.md
- Information-Architecture.md
- Functional-Requirements.md
