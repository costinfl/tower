# Vision

## Purpose

Tower is a developer-centric command center for software delivery.

Its purpose is to answer a small number of high-value operational questions by consolidating release information scattered across multiple engineering tools into a single, trustworthy view.

Tower is built by developers for developers.

It assists development teams during the preparation and execution of software releases while respecting organizational segregation of duties.

Tower does not replace existing ALM, CI/CD, deployment, QA or Release Management tools.

Instead, it observes them, correlates their information and presents it in a form that helps developers understand the current state of software delivery.

---

# Problem Statement

Modern software delivery information is fragmented.

Developers frequently need to answer questions such as:

- Which application version is currently deployed in each environment?
- Which applications belong to a Release Pack?
- Which promotion lane is a Release Pack following?
- What information must be handed over before deployment?
- What changed between two environments?

Answering these questions often requires consulting multiple systems including source control, CI/CD pipelines, deployment platforms, issue trackers and documentation repositories.

The process is manual, repetitive and error-prone.

Tower aims to make these answers immediately available.

---

# Vision

Tower provides a single, consistent view of Release Packs, application versions, environments and promotion paths.

The platform collects observations from external systems and combines them with developer-owned metadata to produce a reliable representation of the current delivery landscape.

Tower becomes the team's operational reference during release preparation.

---

# Scope

The first milestone focuses on four capabilities.

1. Model Release Packs.
2. Track which application versions are deployed in each environment.
3. Represent user-defined promotion paths.
4. Generate release documentation from collected information.

Anything outside these goals should be considered out of scope for Milestone 1.

---

# Intended Users

Primary users:

- Developers
- Technical Leads
- Software Architects

Secondary users (read-only):

- QA Engineers
- DevOps Engineers
- Release Managers

Tower is not intended to replace the responsibilities of these teams.

---

# Design Philosophy

Tower follows a small-core philosophy.

It intentionally limits its scope to solving a few important problems exceptionally well instead of attempting to become a complete ALM or DevOps platform.

Whenever possible, Tower prefers observation over automation.

---

# Relationship with Existing Tools

Tower integrates with existing engineering tools through adapters.

Examples include:

- Jira
- Confluence
- Bitbucket
- Jenkins
- OpenShift

These systems remain the authoritative owners of their respective data.

Tower enriches and correlates that information but does not attempt to replace the systems themselves.

---

# Success Criteria

Tower is successful when developers can answer its core questions within seconds without navigating multiple tools.

Documentation preparation becomes largely automatic.

Release preparation becomes faster, more reliable and easier to understand.

---

# Long-Term Direction

Future milestones may introduce additional capabilities such as richer historical analysis, advanced documentation generation and broader integrations.

However, every future enhancement must preserve the project's core philosophy:

> Observe. Correlate. Explain.

Tower should remain a lightweight command center rather than evolve into another deployment or ALM platform.
