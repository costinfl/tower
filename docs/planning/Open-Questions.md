# Open Questions

**Status:** Draft

**Owner:** Architecture

---

# Purpose

This document captures architectural and product questions intentionally deferred beyond Milestone 1.

Items listed here are not blockers for implementation.

They represent future decisions that may influence later milestones.

---

# Product

## OQ-001

Can a Promotion Path be modified after Release Packs already reference it?

Status

Deferred

---

## OQ-002

Can a Release Pack change Promotion Paths?

Status

Deferred

---

## OQ-003

Should Release Packs support explicit lifecycle states in addition to derived observed state?

Status

Deferred

---

## OQ-004

Should archived Release Packs remain searchable?

Status

Deferred

---

# Observation

## OQ-005

What synchronization frequency should Connectors use?

Status

Implementation Decision

---

## OQ-006

Should synchronization be manual, scheduled or event driven?

Status

Implementation Decision

---

## OQ-007

How long should historical Snapshots be retained?

Status

Deferred

---

## OQ-008

Should Tower detect Environment drift automatically?

Status

Future Milestone

---

# Documentation

## OQ-009

Which output formats should be supported?

Examples

- Markdown
- HTML
- PDF
- DOCX

Status

Future Milestone

---

## OQ-010

Should documentation templates be customizable?

Status

Deferred

---

# User Experience

## OQ-011

Should Release Packs support timeline visualization?

Status

Future Milestone

---

## OQ-012

Should dashboard widgets be customizable?

Status

Future Milestone

---

## Integration

## OQ-013

Should Connectors support incremental synchronization?

Status

Implementation Decision

---

## OQ-014

Should synchronization failures generate notifications?

Status

Future Milestone

---

## OQ-015

Should Connectors expose health information?

Status

Future Milestone

---

# Future Capabilities

The following ideas are intentionally outside Milestone 1.

- Environment drift detection
- Rollback visualization
- Dependency visualization
- Deployment history
- Notifications
- Analytics
- Release metrics
- Trend analysis
- Multi-project dashboards
- Cross-team reporting

---

# Governance

Questions recorded here shall be resolved through future ADRs.

No implementation shall introduce architectural decisions that contradict existing ADRs without creating a new ADR.
