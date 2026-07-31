# Tower Documentation Checklist

This checklist tracks the architectural documentation required before implementation planning begins.

## Phase 1 - Vision

- [x] Vision.md
- [x] Principles.md
- [x] Guardrails.md
- [x] Product-Boundaries.md
- [x] Roadmap.md

## Phase 2 - Domain

- [x] Glossary.md
- [x] Domain-Model.md
- [x] CRC-Cards.md
- [x] Event-Model.md
- [x] State-Model.md
- [x] Scenarios.md

## Phase 3 - Architecture

- [x] Context.md
- [x] Connector-Model.md
- [x] Information-Architecture.md
- [x] Functional-Requirements.md
- [x] Non-Functional-Requirements.md

## Phase 4 - Architecture Decisions

- [x] ADR-000-Template.md
- [x] ADR-001-Read-Only-Architecture.md
- [x] ADR-002-Observation-Is-The-Atomic-Unit.md
- [x] ADR-003-Vendor-Neutral-Connectors.md
- [x] ADR-004-Release-Pack-Is-The-Central-Concept.md

## Phase 5 - Planning Preparation

- [x] Open-Questions.md
- [x] Backlog.md
- [x] Milestones.md

## Post-Planning

- [x] Consistency-Report.md
- [x] Implementation-Plan.md
- [x] ADR-005 to ADR-010
- [x] Documentation reconciliation (issue #7)

## Milestone 2 - Automated Synchronization

- [x] ADR-011-Synchronization-Records-Change-Not-Repetition.md
- [x] ADR-012-External-Bindings-Map-Tower-Concepts-To-Vendor-Locators.md
- [x] FR-055 to FR-061
- [x] Connector service provider interface
- [x] Encrypted credential storage
- [x] External Bindings
- [x] Kubernetes Deployment Platform Connector
- [x] Deployment Collector with change detection
- [x] Sync Run recording and reporting
- [x] Synchronize-now use case and API
- [x] Connectors screen and read-only connection test
- [x] ADR-014-Source-Control-Is-Read-Through-Git-Not-A-Vendor-API.md
- [x] Source Control Connector — git reference discovery (issue #3)
- [x] Repository bindings (V8) and version discovery from refs (issue #3)
- [x] Source control discovery in the Viewer (issue #3)

## Milestone 3 - Documentation formats

- [x] HTML release document renderer (OQ-009)
- [x] ADR-013-Documentation-Templates-Select-Sections-Not-Markup.md
- [x] FR-062 to FR-065
- [x] Document Templates: section selection and ordering (OQ-010)
- [x] Documentation templates screen and template picker
- [x] ADR-015-DOCX-Is-Written-Directly-For-Reproducibility.md
- [x] DOCX release document renderer (OQ-009 answered, PDF declined)

## Milestone 4 - Snapshots and historical comparison

- [x] ADR-017-A-Snapshot-Is-Derived-Not-Stored.md
- [x] OQ-007 answered (nothing is stored, so nothing is retained)
- [x] FR-068, FR-069, FR-070
- [x] Point-in-time Environment state and comparison
- [x] Release Pack progression history
- [x] Historical comparison in the Viewer

## Milestone 5 - Operational dashboard

- [x] FR-071 to FR-075
- [x] Convergence: which releases are heading for which Environment (issue #7)
- [x] Dashboard API and screen
- [x] Summary indicators that count silence as silence

## Issue Tracking Connector

- [x] ADR-018-A-Work-Item-Reference-Is-Intent-Not-An-Observation.md
- [x] Connector-Model corrected: not every Connector produces Observations
- [x] IssueTrackerConnector SPI (vendor-neutral)
- [x] Work item references on a Release Pack (V10), export schema version 2
- [ ] Application layer, API and Viewer
- [ ] Work items in generated documentation
- [ ] GitHub Issues implementation
- [ ] Jira implementation

## Deferred work closed

- [x] ADR-016-Handover-Is-Versioned-Release-Pack-Metadata-Is-Not.md
- [x] Handover versioning (issue #8, IA-02, FR-054, FR-067)

---

Current Phase: **Milestone 5 — Operational dashboard**
