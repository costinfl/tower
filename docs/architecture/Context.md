# Context

**Status:** Draft

**Owner:** Architecture

**Related:**

- ../vision/Vision.md
- ../domain/Domain-Model.md
- Connector-Model.md

---

# Purpose

This document defines Tower's position within the software delivery ecosystem.

It describes:

- the users of Tower;
- the external systems with which Tower interacts;
- ownership of information;
- information exchanged between systems.

This document intentionally avoids implementation details.

---

# Architectural Position

Tower is a developer-centric command center.

Tower does not replace existing engineering systems.

Instead, it observes them, correlates their information and presents a unified operational view.

Tower occupies the space between engineering tools and developers.

---

# Primary Users

Primary users include:

- Developers
- Technical Leads
- Software Architects

These users create Release Packs, prepare release documentation and monitor promotion progress.

---

# Secondary Users

Secondary users include:

- QA Engineers
- DevOps Engineers
- Release Managers

Tower provides visibility to these users but does not replace their existing responsibilities.

---

# External Systems

Tower integrates with categories of engineering systems rather than specific products.

Supported categories include:

- Source Control
- Issue Tracking
- Documentation Platform
- CI/CD Platform
- Deployment Platform

Individual vendor products are implementation details.

---

# Ownership of Information

Tower owns:

- Release Packs
- Promotion Paths
- Handover information
- User-defined metadata
- Canonical Model

Tower does not own:

- Source code
- Git repositories
- Pipelines
- Kubernetes resources
- Issues
- Wiki pages
- Deployment execution

Each External System remains authoritative for its own domain.

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
        └────────► Documentation
```

Tower never bypasses the Canonical Model.

Every visualization and generated document originates from it.

---

# Context Diagram

```
                 Developers
                      │
                      ▼
               +--------------+
               |    Tower     |
               +--------------+
                 ▲    ▲    ▲
                 │    │    │
                 │    │    │
      Source     │    │    │ Deployment
      Control    │    │    │ Platform
                 │    │    │
      Issue -----+    │    +----- Documentation
     Tracking         │             Platform
                      │
                  CI/CD Platform
```

---

# Responsibilities

Tower is responsible for:

- correlating engineering information;
- presenting Release Packs;
- presenting Environment state;
- generating release documentation;
- tracking Promotion Paths.

Tower is not responsible for:

- executing deployments;
- triggering pipelines;
- modifying infrastructure;
- creating issues;
- managing source code.

---

# Architectural Constraints

The following constraints apply throughout the project.

- External systems remain authoritative.
- Tower is read-only with respect to external systems.
- Vendor neutrality shall be preserved.
- Every architectural component shall support the Canonical Model.
- Information duplication shall be minimized.

---

# Success Criteria

The Context Architecture is successful when:

- every owned responsibility is clearly identified;
- every external responsibility remains external;
- no architectural ambiguity exists regarding ownership of information;
- Tower remains a coordination platform rather than an execution platform.
