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
- [x] Application layer, API and Viewer
- [x] Work items in generated documentation
- [x] GitHub Issues implementation, verified against this repository's own issues
- [x] Jira implementation (live verification against a real site outstanding — see below)

## Connector verification

- [x] ADR-019-Connector-Fixtures-Are-Verified-Against-The-Vendors-Own-Description.md
- [x] tower-testkit: recording HTTP server and the conformance suite every Issue Tracking Connector extends
- [x] GitHub fixtures recorded from the real API, checked against GitHub's published description
- [x] The validator proven to fail: a corrupted recording is caught and the field named
- [ ] Jira fixtures checked against Atlassian's description — `specs/jira/` needs producing on a machine
      that can reach developer.atlassian.com, and its redistribution terms checking first

## CI/CD Connector

- [x] ADR-020-A-Deployment-Run-Is-An-Observation-A-Build-Run-Is-Not.md
- [x] Connector-Model corrected: a build run is not an Observation
- [x] FR-076 to FR-081
- [x] CiCdConnector SPI (vendor-neutral, named for what CI systems generally offer)
- [x] Job bindings (V12): which job deploys which Application to which Environment, and where the version is
- [x] Collector: successful runs become Observations on a timeline, unattributable runs reported
- [x] Reports persisted (V13), use case and API — kept apart from Sync Runs, which is OQ-017's first half
- [x] OQ-017 answered: the two histories stay apart, and the Connectors screen shows both, each
      stating what its own clean read establishes. Merging them would need one of the two to claim
      something Tower does not know.
- [x] Connectors screen: job bindings, the credential and connection test per CI server, and the
      report — including the runs read and deliberately not recorded
- [x] Build runs as candidate Application Versions (V16). A build job binds without an Environment,
      because a build says what was produced rather than where it went, and what a run proposes is
      stored nowhere. The discovery screen gained a source rather than a mode, as ADR-020 asked: a
      ref and a build run sit in one list, each saying which system offered it.
- [x] Jenkins implementation — deliberately last, and the only part that dies with Jenkins
- [x] Pipeline job binding API, verified end to end against a stand-in Jenkins
- [ ] Live verification against a real Jenkins (no host is reachable from the build environment)

## Artifact Repository Connector

- [x] ADR-021-An-Artifact-Is-Confirmed-Not-Recorded.md
- [x] Connector-Model: a sixth category, producing no Observations and storing nothing it reads
- [x] FR-082 to FR-086
- [x] ArtifactRepositoryConnector SPI (vendor-neutral)
- [x] Coordinate templates on an Application (V14), one per artifact kind — composing where every
      other binding extracts, and an unrecognised token refused rather than left to become a literal
- [x] Confirmation beside what Tower holds: present, absent, not addressable, or unread — never a
      null standing in for two of them
- [x] An accepted digest for documents (V15, FR-086). A document prints what somebody accepted;
      where the repository later reports different bytes under the same name the difference is shown
      and nothing is corrected. Divergence is a fifth state rather than a nullable field, for the
      reason ADR-018 made it one for a work item's title.
- [x] An Artifacts section in the release document, in all three renderers and the demo's fourth
- [x] Viewer: artifacts on an Application Version, the coordinate bindings and the coordinate
      preview on the Connectors screen, and the button that accepts a digest. Driven end to end in a
      real browser: bind a template, preview it, confirm, accept, force a divergence, withdraw.
- [x] The demo backend carries the same endpoints, and the parity check now compares an Artifacts
      section with rows in it rather than only its empty state. Proven to bite: dropping the
      backticks on one side reports drift.
- [x] Artifactory implementation — GET only; its AQL search is a POST and ADR-001 forbids one
- [x] Verified end to end against a stand-in Artifactory (`scripts/acceptance-artifacts.sh`, 49
      checks), including that the repository saw nothing but GET, that a re-pushed tag is reported,
      and that a document regenerates byte-identically after one
- [ ] Live verification against a real Artifactory (no host is reachable from the build environment)

## Getting started

- [x] FR-087 and FR-088
- [x] A setup path on the Dashboard: the order the model forces, each step done or not done, derived
      on request from what exists and never stored. Binding a system is marked optional and the path
      is complete without it — ADR-006 makes a person recording a deployment a Collector like any
      other, so a Tower connected to nothing is a supported way to run.
- [x] The path shows only while a required step remains, and each step leads to the screen that
      completes it
- [x] Navigation in three groups — the daily screens, the setup screens in the order they must be
      done, then sharing — so neither order is paid for in the other
- [x] The Connectors screen split into the five Connector categories Connector-Model.md names,
      instead of nine panels in one scroll
- [x] Driven in a real browser against a fresh store: five steps, none done; each step navigates;
      steps flip one at a time; an empty Release Pack does not tick the step that asks for contents;
      the path disappears after the fourth. The demo bundle was driven too — nothing automated
      compares the two dashboards.

## Database changes in a release

- [x] ADR-022-A-Schema-Is-An-Application-Not-A-New-Kind-Of-Thing.md
- [x] OQ-018 answered, before anything was implemented, which is what the question asked for
- [x] Scenario 9 — both shapes: Flyway inside the application repository, and a Liquibase changelog
      repository addressing several schemas
- [x] Verified end to end with endpoints that all existed beforehand (14 checks in
      `scripts/acceptance.sh`, now 100). Two schemas at different levels in one Environment, the
      schema left behind named rather than counted, two Applications bound to one repository, and
      the API surface asserted to carry no schema concept at all.
- [x] No type, no endpoint, no screen, no migration. The point of the ADR is that the model already
      says this.

## Defects found and fixed

- [x] The credential store returned garbage instead of failing when the master key was wrong, about
      once in every 286 reads (measured). First seen as an intermittent test failure and written off
      as flakiness; it was an unauthenticated-cipher padding collision, and the consequence was a
      Connector presenting random bytes as somebody's token. Fixed with a key check value, and the
      regression test hammers the path 500 times rather than once.

## Outstanding verification

None of the items below is closed by the schema work above. A vendor's description is not a vendor's
server, and only the checks here have ever spoken to one.

- [ ] Jira Connector against a real site. Built and tested against a local server standing in for
      Jira, and exercised end to end through Tower's whole path — binding, Collector, Connector,
      HTTP — but no Atlassian host is reachable from the environment this was built in, so nothing
      here has yet spoken to a real Jira. This is the condition to clear before a scheduler is
      considered.
- [ ] Jenkins Connector against a real Jenkins. Same position as Jira: built and exercised end to
      end against a local server standing in for it, with no reachable host to try.
- [ ] Artifactory Connector against a real Artifactory. Weaker footing than either of the above,
      and worth stating rather than leaving to be inferred: JFrog publishes no OpenAPI description,
      so ADR-019's schema check has nothing to bite on and the stand-in's bodies are written from
      knowledge of the API rather than recorded off an instance. Nothing here is evidence about
      Artifactory.
- [ ] A generated DOCX opened in Word.
- [ ] The six real-cluster M2 exit checks.

## Deferred work closed

- [x] ADR-016-Handover-Is-Versioned-Release-Pack-Metadata-Is-Not.md
- [x] Handover versioning (issue #8, IA-02, FR-054, FR-067)

---

Current Phase: **Milestone 5 — Operational dashboard**
