# ADR-003 — Vendor-Neutral Connectors

**Status**

Accepted

---

## Context

Tower must integrate with multiple categories of engineering tools.

Examples include:

- Source Control
- Issue Tracking
- Documentation Platforms
- CI/CD Platforms
- Deployment Platforms

Individual organizations may use different products within each category.

The business architecture should remain independent of those choices.

---

## Decision

Tower shall integrate through vendor-neutral Connector categories.

Business concepts shall reference connector categories rather than specific products.

Vendor-specific implementations shall terminate at the Connector boundary.

Examples:

Source Control Connector

Possible implementations:

- GitHub
- Bitbucket
- GitLab
- Azure Repositories

Issue Tracking Connector

Possible implementations:

- Jira
- Azure Boards
- GitHub Issues

Deployment Platform Connector

Possible implementations:

- Kubernetes
- OpenShift
- Amazon ECS

The Domain Model shall remain unchanged regardless of implementation.

---

## Consequences

Positive

- improves portability;
- reduces vendor lock-in;
- simplifies architectural evolution;
- encourages clean boundaries.

Negative

- connector implementations require normalization.
- some vendor-specific capabilities may not be represented.

---

## Alternatives Considered

### Model Vendor Products Directly

Rejected.

Business concepts would become coupled to specific commercial products.

---

### Create Separate Domain Models Per Vendor

Rejected.

This would duplicate business logic and significantly increase maintenance cost.

---

## Related Documents

- Connector-Model.md
- Context.md
- Information-Architecture.md
- Non-Functional-Requirements.md
