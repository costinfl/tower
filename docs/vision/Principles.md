# Principles

## Purpose

This document defines the fundamental principles that guide every architectural and implementation decision made within the Tower project.

If a future feature conflicts with these principles, the principles take precedence.

---

# P1. Developer First

Tower is built by developers for developers.

Its primary objective is to simplify release preparation and provide operational visibility to development teams.

---

# P2. Observe, Don't Interfere

Tower observes engineering systems.

It does not execute deployments, modify infrastructure, trigger pipelines or change external systems.

---

# P3. Single Source of Understanding

Tower correlates information from multiple systems into one coherent view.

It is not necessarily the authoritative source of every piece of data, but it is the authoritative place to understand software delivery.

---

# P4. Vendor Neutrality

Tower models engineering concepts rather than vendor-specific concepts.

External systems are accessed through adapters.

Replacing Jira with Azure DevOps or Bitbucket with GitHub must not require changes to the domain model.

---

# P5. Documentation as a View

Documentation is generated from the domain model.

Manual duplication of release information should be minimized.

---

# P6. Small Core

Tower intentionally solves a small number of problems exceptionally well.

New features should only be introduced when they reinforce the project's primary purpose.

---

# P7. Read-Only by Default

Unless explicitly required in future milestones, Tower interacts with external systems using read-only operations.

---

# P8. Evolution Through Milestones

Tower is designed to grow incrementally.

Each milestone must deliver a complete, usable product while preserving architectural simplicity.
