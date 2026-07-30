// Seed data for the public demo.
//
// EVERY VALUE HERE IS INVENTED. No release described below ever existed and
// nothing here was observed by anyone. Tower's entire claim is that it reports
// facts with provenance, so a demo populated with fabricated observations has
// to say so loudly — see DemoBanner, which is not dismissible for that reason.
//
// The shape mirrors the Regular and Hotfix paths from Scenarios.md, because the
// thing most worth seeing is ADR-005 convergence: two paths meeting at UAT and
// Production, with a human deciding which release proceeds.

export const DEMO_INSTANCE = "demo.tower";

const id = (n: string) => `demo-${n}`;

export const environments = [
  { id: id("env-dev1"), name: "Dev1", stage: "DEVELOPMENT" as const },
  { id: id("env-devhotfix"), name: "Dev-Hotfix", stage: "DEVELOPMENT" as const },
  { id: id("env-sit1"), name: "SIT1", stage: "VALIDATION" as const },
  { id: id("env-sit2"), name: "SIT2", stage: "VALIDATION" as const },
  { id: id("env-uat"), name: "UAT", stage: "VALIDATION" as const },
  { id: id("env-preprod"), name: "PreProd", stage: "PRE_PRODUCTION" as const },
  { id: id("env-prod"), name: "Production", stage: "PRODUCTION" as const },
];

export const applications = [
  { id: id("app-customer"), name: "Customer API", description: "Customer profile and preferences" },
  { id: id("app-orders"), name: "Orders API", description: "Order capture and fulfilment" },
  { id: id("app-auth"), name: "Authentication Service", description: "Token issuing and validation" },
  { id: id("app-web"), name: "Web Frontend", description: "Customer-facing storefront" },
];

export const applicationVersions = [
  { id: id("v-cust-250"), applicationId: id("app-customer"), version: "2.5.0",
    branch: "release/2.5", tag: "v2.5.0", commit: "a1c3f90", buildIdentifier: "build-1187" },
  { id: id("v-cust-251"), applicationId: id("app-customer"), version: "2.5.1",
    branch: "hotfix/2.5.1", tag: "v2.5.1", commit: "b7e2d41", buildIdentifier: "build-1204" },
  { id: id("v-orders-140"), applicationId: id("app-orders"), version: "1.4.0",
    branch: "release/1.4", tag: "v1.4.0", commit: "c04ba18", buildIdentifier: "build-1188" },
  { id: id("v-auth-330"), applicationId: id("app-auth"), version: "3.3.0",
    branch: "main", tag: "v3.3.0", commit: "d51e7c2", buildIdentifier: null },
  { id: id("v-web-980"), applicationId: id("app-web"), version: "9.8.0",
    branch: "release/9.8", tag: null, commit: "e93a0bb", buildIdentifier: "build-1190" },
  // Present in no Release Pack — this is the drift in Scenario 7.
  { id: id("v-orders-141rc"), applicationId: id("app-orders"), version: "1.4.1-rc1",
    branch: "spike/latency", tag: null, commit: "f2d8c07", buildIdentifier: "build-1211" },
];

const T = (d: string) => `2026-07-${d}`;

export const promotionPaths = [
  {
    id: id("path-regular"),
    name: "Regular",
    archived: false,
    versions: [
      {
        number: 1,
        createdAt: `${T("06")}T08:00:00Z`,
        environmentIds: [id("env-dev1"), id("env-sit1"), id("env-uat"), id("env-preprod"), id("env-prod")],
      },
    ],
  },
  {
    // Converges with Regular at UAT and Production. One business team tests in
    // UAT and decides which release goes on; Tower shows them the situation and
    // decides nothing (ADR-001, ADR-005).
    id: id("path-hotfix"),
    name: "Hotfix",
    archived: false,
    versions: [
      {
        number: 1,
        createdAt: `${T("06")}T08:05:00Z`,
        environmentIds: [id("env-devhotfix"), id("env-sit1"), id("env-uat"), id("env-prod")],
      },
      {
        number: 2,
        createdAt: `${T("21")}T11:30:00Z`,
        environmentIds: [id("env-devhotfix"), id("env-sit2"), id("env-uat"), id("env-prod")],
      },
    ],
  },
  {
    id: id("path-legacy"),
    name: "Legacy Monthly",
    archived: true,
    versions: [
      {
        number: 1,
        createdAt: "2025-11-02T09:00:00Z",
        environmentIds: [id("env-dev1"), id("env-uat"), id("env-prod")],
      },
    ],
  },
];

export const releasePacks = [
  {
    id: id("pack-august"),
    name: "Release 2026.08",
    description: "August business features: saved baskets and address validation.",
    archived: false,
    promotionPathId: id("path-regular"),
    promotionPathVersion: 1,
    contents: [
      { applicationId: id("app-customer"), applicationVersionId: id("v-cust-250") },
      { applicationId: id("app-orders"), applicationVersionId: id("v-orders-140") },
      { applicationId: id("app-web"), applicationVersionId: id("v-web-980") },
    ],
    handover: {
      deploymentInstructions:
        "Deploy in order: customer-api, then orders-api, then web.\n"
        + "Web will 502 briefly until orders-api reports ready.",
      shellCommands:
        "kubectl -n retail rollout status deploy/customer-api\n"
        + "kubectl -n retail rollout status deploy/orders-api\n"
        + "kubectl -n retail rollout status deploy/web",
      databaseMigrations:
        "V37__add_saved_basket.sql — additive, safe to run ahead of the deploy.\n"
        + "V38__address_validation_cache.sql — additive.",
      rollbackProcedure:
        "Roll back web first, then orders-api, then customer-api.\n"
        + "Both migrations are additive and need no down-script.",
      validationNotes:
        "Smoke test: add to basket, sign out, sign in, basket still present.\n"
        + "Check address validation against a known-bad postcode.",
      operationalNotes:
        "Expect elevated latency on orders-api for roughly ten minutes while the cache warms.",
    },
    iterations: [
      { id: id("it-sit1"), name: "SIT Iteration 1", startedAt: `${T("13")}T09:00:00Z`,
        completedAt: `${T("15")}T16:30:00Z`, notes: "Two defects raised, both fixed in 2.5.0." },
      { id: id("it-sit2"), name: "SIT Iteration 2", startedAt: `${T("16")}T09:00:00Z`,
        completedAt: `${T("17")}T15:00:00Z`, notes: "Regression pass clean." },
      { id: id("it-uat"), name: "UAT Iteration", startedAt: `${T("20")}T09:00:00Z`,
        completedAt: null, notes: "Business team reviewing address validation." },
    ],
  },
  {
    id: id("pack-hotfix"),
    name: "Hotfix 2026.08.1",
    description: "Checkout returns 500 for guest users with no saved address.",
    archived: false,
    promotionPathId: id("path-hotfix"),
    // Pinned to version 1 while the path itself has moved to version 2. This is
    // correct, not stale, and is exactly what ADR-007 exists to preserve.
    promotionPathVersion: 1,
    contents: [{ applicationId: id("app-customer"), applicationVersionId: id("v-cust-251") }],
    handover: {
      deploymentInstructions: "Single service. Deploy customer-api 2.5.1 and restart nothing else.",
      shellCommands: "kubectl -n retail rollout status deploy/customer-api",
      databaseMigrations: "",
      rollbackProcedure: "Redeploy 2.5.0. No schema change to undo.",
      validationNotes: "Guest checkout with no saved address must complete.",
      operationalNotes: "",
    },
    iterations: [
      { id: id("it-hotfix-sit"), name: "SIT Iteration 1", startedAt: `${T("22")}T10:00:00Z`,
        completedAt: `${T("22")}T14:00:00Z`, notes: "Defect confirmed fixed." },
    ],
  },
  {
    id: id("pack-july"),
    name: "Release 2026.07",
    description: "July release. Archived — the team has stopped working it.",
    // Archived AND observed in Production. Two independent facts (ADR-008);
    // the UI must never collapse them into one indicator.
    archived: true,
    promotionPathId: id("path-regular"),
    promotionPathVersion: 1,
    contents: [{ applicationId: id("app-auth"), applicationVersionId: id("v-auth-330") }],
    handover: {
      deploymentInstructions: "Deploy auth 3.3.0.",
      shellCommands: "", databaseMigrations: "", rollbackProcedure: "Redeploy 3.2.4.",
      validationNotes: "", operationalNotes: "",
    },
    iterations: [
      { id: id("it-july"), name: "UAT Iteration", startedAt: "2026-06-29T09:00:00Z",
        completedAt: "2026-07-01T12:00:00Z", notes: "Signed off." },
    ],
  },
];

// Provenance varies on purpose. Milestone 1 ships manual entry only, so most of
// these name a person; one is shown as imported from another instance to make
// ADR-010's guarantee visible — a fact keeps naming whoever actually saw it.
export const observations = [
  { id: id("obs-1"), environmentId: id("env-dev1"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-250"), observedAt: `${T("10")}T09:15:00Z`,
    source: { collector: "manual", actor: "priya", originInstance: null, manual: true } },
  { id: id("obs-2"), environmentId: id("env-dev1"), applicationId: id("app-orders"),
    applicationVersionId: id("v-orders-140"), observedAt: `${T("10")}T09:20:00Z`,
    source: { collector: "manual", actor: "priya", originInstance: null, manual: true } },
  { id: id("obs-3"), environmentId: id("env-sit1"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-250"), observedAt: `${T("13")}T08:40:00Z`,
    source: { collector: "manual", actor: "tomas", originInstance: null, manual: true } },
  { id: id("obs-4"), environmentId: id("env-sit1"), applicationId: id("app-orders"),
    applicationVersionId: id("v-orders-140"), observedAt: `${T("13")}T08:45:00Z`,
    source: { collector: "manual", actor: "tomas", originInstance: null, manual: true } },
  { id: id("obs-5"), environmentId: id("env-uat"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-250"), observedAt: `${T("20")}T08:30:00Z`,
    source: { collector: "manual", actor: "priya", originInstance: null, manual: true } },
  { id: id("obs-6"), environmentId: id("env-uat"), applicationId: id("app-orders"),
    applicationVersionId: id("v-orders-140"), observedAt: `${T("20")}T08:35:00Z`,
    source: { collector: "manual", actor: "priya", originInstance: null, manual: true } },
  { id: id("obs-7"), environmentId: id("env-uat"), applicationId: id("app-web"),
    applicationVersionId: id("v-web-980"), observedAt: `${T("20")}T08:36:00Z`,
    source: { collector: "manual", actor: "priya", originInstance: null, manual: true } },
  // Hotfix, straight to Production. Its path has no PRE_PRODUCTION environment,
  // which under the original State Model made Production unreachable — the
  // defect ADR-008 was written to fix.
  { id: id("obs-8"), environmentId: id("env-devhotfix"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-251"), observedAt: `${T("22")}T09:05:00Z`,
    source: { collector: "manual", actor: "tomas", originInstance: null, manual: true } },
  { id: id("obs-9"), environmentId: id("env-sit1"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-251"), observedAt: `${T("22")}T11:00:00Z`,
    source: { collector: "manual", actor: "tomas", originInstance: null, manual: true } },
  { id: id("obs-10"), environmentId: id("env-uat"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-251"), observedAt: `${T("23")}T09:00:00Z`,
    source: { collector: "manual", actor: "priya", originInstance: null, manual: true } },
  { id: id("obs-11"), environmentId: id("env-prod"), applicationId: id("app-customer"),
    applicationVersionId: id("v-cust-251"), observedAt: `${T("23")}T18:20:00Z`,
    source: { collector: "manual", actor: "tomas", originInstance: null, manual: true } },
  // Imported from a colleague's instance. It still names them, not this one.
  { id: id("obs-12"), environmentId: id("env-prod"), applicationId: id("app-auth"),
    applicationVersionId: id("v-auth-330"), observedAt: `${T("02")}T14:10:00Z`,
    source: { collector: "manual", actor: "mira", originInstance: "mira-laptop", manual: true } },
  // Scenario 7: a version in no Release Pack. Tower reports it and says nothing
  // about whether it should be there.
  { id: id("obs-13"), environmentId: id("env-sit2"), applicationId: id("app-orders"),
    applicationVersionId: id("v-orders-141rc"), observedAt: `${T("24")}T16:45:00Z`,
    source: { collector: "manual", actor: "tomas", originInstance: null, manual: true } },
];

// --- Connectors (Milestone 2, issues #46 to #53) ----------------------------
//
// Fabricated like everything else here. The demonstration reaches no cluster,
// so the runs below are invented and "Synchronize now" reports that nothing was
// contacted rather than pretending to have read something.

const CLUSTER = "https://api.cluster.example:6443";

export const environmentBindings = [
  { environmentId: id("env-uat"), connectorId: "kubernetes", target: CLUSTER, scope: "customer-uat" },
  { environmentId: id("env-prod"), connectorId: "kubernetes", target: CLUSTER, scope: "customer-prod" },
];

export const applicationBindings = [
  // The whole tag is the version — the common case.
  { applicationId: id("app-customer"), connectorId: "kubernetes",
    image: "registry.example/acme/customer-api", versionPattern: "^(.+)$" },
  // A team that prefixes its tags, showing why the pattern is configurable.
  { applicationId: id("app-orders"), connectorId: "kubernetes",
    image: "registry.example/acme/orders-api", versionPattern: "^release-(.+)$" },
];

/** Only the time it was saved. There is nowhere here a token could be read from. */
export const credentials: Record<string, string> = {
  [`kubernetes@${CLUSTER}`]: "2026-08-03T08:12:00Z",
};

export const syncRuns = [
  // Newest first. Nothing changed, which is the ordinary steady state under
  // ADR-011 and the case that would fill the store with noise if every run
  // appended an Observation.
  {
    id: id("run-3"), connectorId: "kubernetes",
    startedAt: "2026-08-05T09:30:00Z", finishedAt: "2026-08-05T09:30:04Z",
    outcome: "SUCCEEDED" as const, workloadsRead: 6, observationsAppended: 0,
    foundNoChange: true, confirmsLiveness: true,
    unrecognized: [
      // Something is running that nobody has bound. Tower says so rather than
      // guessing which Application it is (ADR-012, FR-060).
      { scope: "customer-prod", name: "legacy-batch/app",
        imageReference: "registry.example/acme/legacy-batch:4.1.0",
        reason: "No Application is bound to this image." },
      // The OpenShift case: an ImageStream trigger resolved the tag away.
      { scope: "customer-uat", name: "web-frontend/app",
        imageReference: "image-registry.openshift-image-registry.svc:5000/acme/web@sha256:9f2c1a",
        reason: "The image is pinned to a digest, so it carries no version." },
    ],
    failures: [],
  },
  {
    id: id("run-2"), connectorId: "kubernetes",
    startedAt: "2026-08-04T16:02:00Z", finishedAt: "2026-08-04T16:02:05Z",
    outcome: "SUCCEEDED" as const, workloadsRead: 6, observationsAppended: 1,
    foundNoChange: false, confirmsLiveness: true,
    unrecognized: [], failures: [],
  },
  // A partial run: one namespace was readable and one was not. The Observations
  // from the readable one stand.
  {
    id: id("run-1"), connectorId: "kubernetes",
    startedAt: "2026-08-04T11:15:00Z", finishedAt: "2026-08-04T11:15:07Z",
    outcome: "PARTIALLY_SUCCEEDED" as const, workloadsRead: 3, observationsAppended: 2,
    foundNoChange: false, confirmsLiveness: false,
    unrecognized: [],
    failures: [`Could not read ${CLUSTER}/customer-prod: forbidden`],
  },
];

// --- Document Templates (Milestone 3, OQ-010) --------------------------------

// One stored template beside the built-in complete document, so a visitor can
// see what choosing one does without having to define it first. "Handover only"
// is the case teams ask for: the page an operator needs at the moment of a
// deployment, without the planning material around it.
export const documentTemplates = [
  {
    id: id("template-handover"),
    name: "Handover only",
    sections: ["STATUS", "HANDOVER"],
  },
];

// --- Repository bindings and source refs (issue #3, ADR-014) -----------------

// The demo reaches no repository, so the refs a "discovery" reports are
// fabricated here just as the Sync Runs are. They are chosen to show the three
// things the real Collector distinguishes: a version already registered, one
// that is new, and a ref the pattern does not recognise.
export const repositoryBindings = [
  {
    applicationId: id("app-customer"),
    connectorId: "git",
    repositoryUrl: "https://github.com/acme/customer-api.git",
    refSelection: "TAGS" as const,
    versionPattern: "^v(.+)$",
  },
];

export const sourceRefs: Record<string, { kind: "TAG" | "BRANCH"; name: string; commit: string }[]> = {
  [id("app-customer")]: [
    { kind: "TAG", name: "v2.4.0", commit: "9f81c0a7d2b34e5f6a1b8c9d0e1f2a3b4c5d6e7f" },
    { kind: "TAG", name: "v2.5.0", commit: "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d" },
    { kind: "TAG", name: "v2.6.0", commit: "c0ffee1234567890abcdef1234567890abcdef12" },
    // Deliberately not a version: shown as unrecognised so the demo makes the
    // point that a pattern which misses something says so.
    { kind: "TAG", name: "experiment", commit: "dead00beef1234567890abcdef1234567890abcd" },
    { kind: "BRANCH", name: "release/2.6", commit: "c0ffee1234567890abcdef1234567890abcdef12" },
    { kind: "BRANCH", name: "main", commit: "c0ffee1234567890abcdef1234567890abcdef12" },
  ],
};
