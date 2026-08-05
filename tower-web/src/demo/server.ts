import * as seed from "./seed";

// An in-memory stand-in for tower-api, used only by the public demo.
//
// It is installed as a `fetch` shim rather than as an alternative API client,
// so every page, component and error path runs exactly the code that runs
// against the real backend. Swapping the client instead would demo a different
// program from the one that ships.
//
// It mirrors the real API's behaviour where that behaviour is the point —
// derived Environment state, derived Release Pack state, the refusals — and
// does not attempt to be a second implementation of Tower. Where it is thinner
// than the real thing, it is thinner in ways a visitor cannot reach.

type Json = Record<string, unknown>;

type Stage = "DEVELOPMENT" | "VALIDATION" | "PRE_PRODUCTION" | "PRODUCTION";

// Declared explicitly rather than inferred from the seed: inference narrows
// `branch: null` to the literal type `null`, which then rejects a version
// created at runtime with a branch.
interface Env { id: string; name: string; stage: Stage }
interface App { id: string; name: string; description: string }
interface Ver {
  id: string; applicationId: string; version: string;
  branch: string | null; tag: string | null; commit: string | null; buildIdentifier: string | null;
}
interface PathVer { number: number; createdAt: string; environmentIds: string[] }
interface PathRec { id: string; name: string; archived: boolean; versions: PathVer[] }
interface Handover {
  deploymentInstructions: string; shellCommands: string; databaseMigrations: string;
  rollbackProcedure: string; validationNotes: string; operationalNotes: string;
}
interface Iter { id: string; name: string; startedAt: string; completedAt: string | null; notes: string }
// Issue #8, ADR-016. Append-only: the demo mirrors the rule, so a visitor who
// edits a Handover twice sees both versions rather than one.
interface HandoverRev { id: string; releasePackId: string; revisionNumber: number; recordedAt: string; handover: Handover }
interface PackRec {
  id: string; name: string; description: string; archived: boolean;
  promotionPathId: string | null; promotionPathVersion: number | null;
  contents: { applicationId: string; applicationVersionId: string }[];
  // ADR-018: Intent, not Observation. An identifier and a title a person
  // accepted, and nothing else about the work item.
  workItems: { identifier: string; title: string }[];
  handover: Handover; iterations: Iter[];
}
interface Src { collector: string; actor: string | null; originInstance: string | null; manual: boolean }
interface Obs {
  id: string; environmentId: string; applicationId: string; applicationVersionId: string;
  observedAt: string; source: Src;
}

const STAGE_RANK: Record<Stage, number> = {
  DEVELOPMENT: 1, VALIDATION: 2, PRE_PRODUCTION: 3, PRODUCTION: 4,
};

// Milestone 2 additions. Bindings, credentials and Sync Runs are fabricated
// like everything else here; the demo never reaches a cluster, so a
// "Synchronize now" simply reports that it could not.
interface EnvBinding { environmentId: string; connectorId: string; target: string; scope: string }
interface AppBinding { applicationId: string; connectorId: string; image: string; versionPattern: string }
// Issue #3, ADR-014. Its own Connector ("git"), not the Deployment Platform's.
interface RepoBinding {
  applicationId: string; connectorId: string; repositoryUrl: string;
  refSelection: "TAGS" | "BRANCHES" | "ALL"; versionPattern: string;
}
// ADR-020. The widest binding here and the only one naming two Tower concepts,
// because that is what a run of a deployment job asserts: this Application
// arrived in that Environment.
// ADR-020. The sibling of PipelineBinding below, and the difference is the whole
// of that decision: this names no Environment. A build says what was produced,
// not where it went, so a run of one proposes a candidate version.
interface BuildBinding {
  applicationId: string; connectorId: string; system: string; job: string;
  versionSource: "PARAMETER" | "RUN_NAME" | "JOB_PATH";
  versionKey: string; versionPattern: string;
}

interface PipelineBinding {
  environmentId: string; applicationId: string; connectorId: string;
  system: string; job: string;
  versionSource: "PARAMETER" | "RUN_NAME" | "JOB_PATH";
  versionKey: string; versionPattern: string;
}

// What a pipeline read produced. Carries confirmsNothingWasDeployed rather than
// a confirmsLiveness, and the difference is the whole of ADR-020: a CI system
// reads what happened, so a clean read licenses only "nothing was deployed by
// these jobs since Tower last looked".
interface PipelineReportRec {
  id: string; connectorId: string; startedAt: string; finishedAt: string;
  jobsRead: number; runsRead: number; observationsAppended: number;
  readEverything: boolean; confirmsNothingWasDeployed: boolean;
  notRecorded: { job: string; runId: string; outcome: string; reason: string }[];
  failures: string[];
}

// ADR-021. The mirror image of RepoBinding above: that carries a versionPattern
// which extracts a version from a string a vendor produced, this carries a
// template which composes a string a vendor will recognise out of a version
// Tower already holds. Several per Application, told apart by kind.
interface ArtifactBinding {
  applicationId: string; connectorId: string; kind: string; system: string;
  coordinateTemplate: string; shortCommitLength: number;
}

// FR-086. The one thing this whole Connector category writes, and it writes it
// because a person stated it rather than because Tower read it.
interface AcceptedArtifact {
  applicationVersionId: string; kind: string; coordinate: string;
  digest: string; acceptedAt: string;
}

interface RunRec {
  id: string; connectorId: string; startedAt: string; finishedAt: string;
  outcome: "SUCCEEDED" | "PARTIALLY_SUCCEEDED" | "FAILED";
  workloadsRead: number; observationsAppended: number;
  foundNoChange: boolean; confirmsLiveness: boolean;
  unrecognized: { scope: string; name: string; imageReference: string; reason: string }[];
  failures: string[];
}

// Milestone 3, OQ-010. A Document Template chooses which sections a release
// document contains and in what order; it holds no markup, which is why this is
// a list of section names rather than a body. Mirrors ADR-013.
interface TemplateRec { id: string; name: string; sections: string[] }

// Kept in the same order as DocumentSection, which is the order the complete
// document uses.
const DOCUMENT_SECTIONS: { name: string; heading: string; description: string }[] = [
  { name: "STATUS", heading: "Status",
    description: "Observed state and lifecycle, with the note that keeps the two apart." },
  { name: "PROMOTION_PATH", heading: "Promotion Path",
    description: "The path this release follows, at the version it was pinned to." },
  { name: "CONTENTS", heading: "Contents",
    description: "The Application Versions the release contains." },
  { name: "ARTIFACTS", heading: "Artifacts",
    description: "The artifact digests accepted for this release's versions." },
  { name: "WORK_ITEMS", heading: "Work Items",
    description: "The work items this release delivers, as the team stated them." },
  { name: "HANDOVER", heading: "Handover",
    description: "Deployment instructions, commands, migrations, rollback and notes." },
  { name: "ITERATIONS", heading: "Validation Iterations",
    description: "The validation cycles recorded against the release." },
  { name: "SIGHTINGS", heading: "Where this release has been observed",
    description: "Every Environment the release has been seen in, each citing its Observation." },
];

const COMPLETE_TEMPLATE_ID = "00000000-0000-0000-0000-000000000001";
const COMPLETE_TEMPLATE_NAME = "Complete document";

const state: {
  environments: Env[]; applications: App[]; versions: Ver[];
  paths: PathRec[]; packs: PackRec[]; observations: Obs[];
  environmentBindings: EnvBinding[]; applicationBindings: AppBinding[];
  repositoryBindings: RepoBinding[];
  issueTrackerBindings: { connectorId: string; locator: string }[];
  pipelineBindings: PipelineBinding[];
  buildBindings: BuildBinding[];
  pipelineReports: PipelineReportRec[];
  artifactBindings: ArtifactBinding[];
  acceptedArtifacts: AcceptedArtifact[];
  credentials: Record<string, string>; runs: RunRec[];
  documentTemplates: TemplateRec[];
  handoverRevisions: HandoverRev[];
} = {
  environments: seed.environments.map((e) => ({ ...e })),
  applications: seed.applications.map((a) => ({ ...a })),
  versions: seed.applicationVersions.map((v) => ({ ...v })),
  paths: seed.promotionPaths.map((p) => ({ ...p, versions: p.versions.map((v) => ({ ...v })) })),
  packs: seed.releasePacks.map((p) => ({
    ...p,
    contents: p.contents.map((c) => ({ ...c })),
    workItems: (p.workItems ?? []).map((w) => ({ ...w })),
    handover: { ...p.handover },
    iterations: p.iterations.map((i) => ({ ...i })),
  })),
  observations: seed.observations.map((o) => ({ ...o, source: { ...o.source } })),
  environmentBindings: seed.environmentBindings.map((b) => ({ ...b })),
  applicationBindings: seed.applicationBindings.map((b) => ({ ...b })),
  repositoryBindings: seed.repositoryBindings.map((b) => ({ ...b })),
  // Empty on purpose (ADR-018): the demo reaches no tracker, and a binding that
  // looked configured while every item stayed unresolved would read as Tower
  // having lost them.
  issueTrackerBindings: [],
  // Empty on purpose, for the reason the tracker bindings above are (ADR-020):
  // the demo reaches no CI system, and a job that looked bound while every read
  // failed would read as Tower having lost the runs.
  pipelineBindings: [],
  // Empty for the reason the pipeline bindings above are: the demo reaches no
  // CI system, so a build job that looked bound would propose nothing and read
  // as Tower having lost the versions.
  buildBindings: [],
  pipelineReports: [],
  // Empty on purpose, for the reason the tracker bindings above are (ADR-021):
  // the demo reaches no repository, and a template that looked configured while
  // every artifact stayed unread would read as Tower having lost them.
  artifactBindings: [],
  // Empty for a stronger reason. A digest is something a person accepted after
  // looking at a repository, and this demonstration has none to look at.
  // Seeding one would put a fabricated sha256 in front of a visitor as though
  // Tower had confirmed it.
  acceptedArtifacts: [],
  credentials: { ...seed.credentials },
  runs: seed.syncRuns.map((r) => ({
    ...r,
    unrecognized: r.unrecognized.map((u) => ({ ...u })),
    failures: [...r.failures],
  })),
  documentTemplates: seed.documentTemplates.map((t) => ({ ...t, sections: [...t.sections] })),
  handoverRevisions: seed.handoverRevisions.map((r) => ({ ...r, handover: { ...r.handover } })),
};

let sequence = 0;
const newId = (prefix: string) => `demo-${prefix}-${++sequence}-${Date.now().toString(36)}`;

// --- errors -----------------------------------------------------------------

class ApiFailure extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}
const conflict = (m: string) => new ApiFailure(409, m);
const notFound = (m: string) => new ApiFailure(404, m);
const badRequest = (m: string) => new ApiFailure(400, m);

// --- lookups ----------------------------------------------------------------

const env = (id: string) => state.environments.find((e) => e.id === id);
const app = (id: string) => state.applications.find((a) => a.id === id);
const version = (id: string) => state.versions.find((v) => v.id === id);
const path = (id: string) => state.paths.find((p) => p.id === id);
const pack = (id: string) => state.packs.find((p) => p.id === id);

const envRef = (id: string) => {
  const e = env(id);
  return e ? { id: e.id, name: e.name, stage: e.stage } : null;
};
const appRef = (id: string) => {
  const a = app(id);
  return a ? { id: a.id, name: a.name } : null;
};
const versionRef = (id: string) => {
  const v = version(id);
  return v ? { id: v.id, version: v.version } : null;
};

// --- views ------------------------------------------------------------------

function pathView(p: PathRec) {
  const versionView = (v: PathVer) => ({
    number: v.number,
    createdAt: v.createdAt,
    environments: v.environmentIds.map(envRef).filter(Boolean),
  });
  return {
    id: p.id,
    name: p.name,
    archived: p.archived,
    currentVersion: versionView(p.versions[p.versions.length - 1]),
    versions: p.versions.map(versionView),
  };
}

function packView(p: PackRec) {
  const assigned = p.promotionPathId ? path(p.promotionPathId) : undefined;
  const pinned = assigned?.versions.find((v) => v.number === p.promotionPathVersion);
  return {
    id: p.id,
    name: p.name,
    description: p.description,
    archived: p.archived,
    promotionPath: assigned && pinned
      ? {
          pathId: assigned.id,
          pathName: assigned.name,
          versionNumber: pinned.number,
          environments: pinned.environmentIds.map(envRef).filter(Boolean),
        }
      : null,
    contents: p.contents.map((c) => {
      const v = version(c.applicationVersionId);
      return {
        applicationId: c.applicationId,
        applicationName: app(c.applicationId)?.name ?? "(unknown)",
        versionId: c.applicationVersionId,
        version: v?.version ?? "(unknown)",
        branch: v?.branch ?? null,
        tag: v?.tag ?? null,
        commit: v?.commit ?? null,
        buildIdentifier: v?.buildIdentifier ?? null,
      };
    }),
    workItems: p.workItems.map((w) => ({
      identifier: w.identifier, title: w.title, titleAccepted: w.title.trim() !== "",
    })),
    handover: { ...p.handover },
    iterations: p.iterations.map((i) => ({ ...i })),
  };
}

function observationView(o: Obs) {
  return {
    id: o.id,
    environment: envRef(o.environmentId),
    application: appRef(o.applicationId),
    applicationVersion: versionRef(o.applicationVersionId),
    observedAt: o.observedAt,
    source: { ...o.source },
  };
}

// Environment state is derived, never stored: latest Observation per
// Application wins, ties leaving the incumbent in place.
//
// `at` bounds the fold rather than selecting a stored snapshot (ADR-017), so
// past state is the same derivation with an upper bound and current state is
// the case where the bound is absent. Inclusive of the instant itself:
// "as of 15 March 10:00" includes what was observed at 15 March 10:00.
function environmentStateView(environmentId: string, at?: string | null) {
  const e = env(environmentId);
  if (!e) throw notFound(`Environment ${environmentId} does not exist.`);

  // Compared as instants rather than as strings: the seed writes
  // "…:00:00Z" and a browser writes "…:00:00.000Z" for the same moment, and
  // those two sort the wrong way round lexically.
  const bound = at ? Date.parse(at) : null;
  if (at && Number.isNaN(bound)) throw badRequest(`'${at}' is not a valid instant.`);

  const latest = new Map<string, Obs>();
  for (const o of state.observations.filter((x) => x.environmentId === environmentId)) {
    if (bound !== null && Date.parse(o.observedAt) > bound) continue;
    const held = latest.get(o.applicationId);
    if (!held || o.observedAt > held.observedAt) latest.set(o.applicationId, o);
  }
  const deployed = [...latest.values()]
    .sort((a, b) => b.observedAt.localeCompare(a.observedAt))
    .map((o) => {
      const v = version(o.applicationVersionId);
      return {
        application: appRef(o.applicationId),
        applicationVersion: {
          id: v?.id ?? o.applicationVersionId,
          version: v?.version ?? "(unknown)",
          branch: v?.branch ?? null,
          tag: v?.tag ?? null,
          commit: v?.commit ?? null,
          buildIdentifier: v?.buildIdentifier ?? null,
        },
        observedAt: o.observedAt,
        source: { ...o.source },
        observationId: o.id,
      };
    });

  return {
    environment: envRef(environmentId),
    hasBeenObserved: deployed.length > 0,
    lastObservedAt: deployed.length ? deployed[0].observedAt : null,
    deployed,
  };
}

// Mirrors StateComparison.between. The sides are left and right rather than
// before and after because the same operation answers "how did UAT change
// since Monday" and "how does UAT differ from Production".
function stateComparisonView(
  left: string, leftAt: string | null, right: string, rightAt: string | null) {
  const l = environmentStateView(left, leftAt);
  const r = environmentStateView(right, rightAt);

  // A row whose Application has since been deleted is dropped rather than
  // reported as an unnamed difference, matching what the real service does
  // with an Observation it can no longer resolve.
  const byApp = (s: ReturnType<typeof environmentStateView>) =>
    new Map(s.deployed.flatMap((d) => (d.application ? [[d.application.id, d] as const] : [])));
  const li = byApp(l);
  const ri = byApp(r);

  const differences: unknown[] = [];
  const unchanged: unknown[] = [];
  // Union of both sides, left order first, so an Application present on only
  // one side is still reported rather than silently dropped.
  const applicationIds = [...new Set([...li.keys(), ...ri.keys()])];

  for (const id of applicationIds) {
    const a = li.get(id);
    const b = ri.get(id);
    const name = (a ?? b)!.application!.name;
    if (a && b && a.applicationVersion.id === b.applicationVersion.id) {
      unchanged.push({
        applicationId: id, applicationName: name, version: a.applicationVersion.version,
        leftObservationId: a.observationId, rightObservationId: b.observationId,
      });
      continue;
    }
    // GONE means Tower has an Observation on one side and none on the other.
    // It is not a claim that anything was undeployed — nobody may have looked.
    const subject = (b ?? a)!.applicationVersion.id;
    differences.push({
      applicationId: id, applicationName: name,
      kind: a && b ? "CHANGED" : b ? "ARRIVED" : "GONE",
      leftVersion: a ? a.applicationVersion.version : null,
      rightVersion: b ? b.applicationVersion.version : null,
      leftObservationId: a ? a.observationId : null,
      rightObservationId: b ? b.observationId : null,
      // Containment, not causation — see the API's DifferenceView.
      releasePacks: state.packs
        .filter((p) => p.contents.some((c) => c.applicationVersionId === subject))
        .map((p) => p.name),
    });
  }

  return { identical: differences.length === 0, differences, unchanged };
}

// Mirrors ReleasePackProgression. Two instants rather than one, because a
// release arrives in an Environment piecemeal and a single "arrived at" would
// have to choose between the first piece and the last.
function packProgressionView(packId: string) {
  const p = pack(packId);
  if (!p) throw notFound(`Release Pack ${packId} does not exist.`);

  const packed = p.contents.map((c) => c.applicationVersionId);
  const firstSeen = new Map<string, Map<string, string>>();
  for (const o of state.observations) {
    if (!packed.includes(o.applicationVersionId) || !env(o.environmentId)) continue;
    const seen = firstSeen.get(o.environmentId) ?? new Map<string, string>();
    const held = seen.get(o.applicationVersionId);
    // Earliest sighting: a version re-observed later did not arrive later.
    if (!held || Date.parse(o.observedAt) < Date.parse(held)) {
      seen.set(o.applicationVersionId, o.observedAt);
    }
    firstSeen.set(o.environmentId, seen);
  }

  const arrivals = [...firstSeen.entries()].map(([environmentId, seen]) => {
    const missing = packed.filter((v) => !seen.has(v));
    const instants = [...seen.values()].sort((x, y) => Date.parse(x) - Date.parse(y));
    return {
      environmentId,
      environmentName: env(environmentId)?.name ?? null,
      stage: env(environmentId)?.stage ?? null,
      firstObservedAt: instants[0],
      // When the last piece landed — the moment the release as a whole was
      // there — and null while any of it is still outstanding.
      completeAt: missing.length === 0 ? instants[instants.length - 1] : null,
      complete: missing.length === 0,
      observedCount: seen.size,
      packedCount: packed.length,
      missing: missing.map((id) => {
        const v = version(id);
        return {
          applicationVersionId: id,
          applicationName: v ? app(v.applicationId)?.name ?? null : null,
          version: v?.version ?? null,
        };
      }),
    };
  });

  // Oldest first: the order the release actually travelled in.
  arrivals.sort((x, y) => Date.parse(x.firstObservedAt) - Date.parse(y.firstObservedAt));
  return { observed: arrivals.length > 0, arrivals };
}

// Mirrors DashboardService. Composes the derivations above rather than adding
// new ones, for the same reason the real service does: a dashboard that
// disagreed with the pages it summarises would be worse than no dashboard.
//
// Releases converging on an Environment are ordered BY NAME. Any order derived
// from progress would read as a recommendation, and deciding which release
// proceeds is the business team's call (ADR-001, Guardrails.md).
function dashboardView() {
  const active = state.packs.filter((p) => !p.archived);
  const progressions = new Map(active.map((p) => [p.id, packProgressionView(p.id)]));

  // The pinned Promotion Path version, not the path's current one (ADR-007).
  const namesEnvironment = (p: PackRec, environmentId: string) => {
    if (!p.promotionPathId) return false;
    const assigned = path(p.promotionPathId);
    const pinned = assigned?.versions.find((v) => v.number === p.promotionPathVersion);
    return pinned ? pinned.environmentIds.includes(environmentId) : false;
  };

  let contested = 0;
  let neverObserved = 0;
  const environments = state.environments.map((e) => {
    const derived = environmentStateView(e.id);
    if (!derived.hasBeenObserved) neverObserved += 1;

    const converging = active
      .filter((p) => namesEnvironment(p, e.id))
      .map((p) => {
        const arrival = progressions.get(p.id)!.arrivals.find((a) => a.environmentId === e.id);
        // No arrival means no Observation of this release here. That is not
        // evidence it is absent — nobody may have looked (Scenario 4).
        return {
          releasePackId: p.id,
          name: p.name,
          standing: !arrival ? "NOT_OBSERVED_HERE" : arrival.complete ? "FULLY_OBSERVED" : "PARTLY_OBSERVED",
          firstObservedAt: arrival ? arrival.firstObservedAt : null,
          completeAt: arrival ? arrival.completeAt : null,
          observedCount: arrival ? arrival.observedCount : 0,
          packedCount: p.contents.length,
        };
      })
      .sort((x, y) => x.name.localeCompare(y.name, undefined, { sensitivity: "base" }));

    if (converging.length > 1) contested += 1;

    return {
      environmentId: e.id,
      name: e.name,
      stage: e.stage,
      hasBeenObserved: derived.hasBeenObserved,
      lastObservedAt: derived.lastObservedAt,
      deployedCount: derived.deployed.length,
      contested: converging.length > 1,
      converging,
    };
  });

  const releasePacks = active
    .map((p) => {
      const arrivals = progressions.get(p.id)!.arrivals;
      // Every Environment at the highest Stage, not one: Environments share
      // Stages (ADR-008), and picking between two equally-far ones would
      // answer a question the data does not answer.
      let furthest: string[] = [];
      let furthestRank = 0;
      let lastObservedAt: string | null = null;
      for (const a of arrivals) {
        const e = env(a.environmentId);
        if (e && STAGE_RANK[e.stage] > furthestRank) {
          furthestRank = STAGE_RANK[e.stage];
          furthest = [];
        }
        if (e && STAGE_RANK[e.stage] === furthestRank) furthest.push(e.name);
        const seen = a.completeAt ?? a.firstObservedAt;
        if (!lastObservedAt || Date.parse(seen) > Date.parse(lastObservedAt)) lastObservedAt = seen;
      }
      furthest.sort((x, y) => x.localeCompare(y, undefined, { sensitivity: "base" }));
      const assigned = p.promotionPathId ? path(p.promotionPathId) : undefined;
      return {
        releasePackId: p.id,
        name: p.name,
        state: packStateView(p.id).state,
        promotionPath: assigned ? assigned.name : null,
        contentCount: p.contents.length,
        environmentsReached: arrivals.length,
        furthestEnvironments: furthest,
        lastObservedAt,
      };
    })
    .sort((x, y) => x.name.localeCompare(y.name, undefined, { sensitivity: "base" }));

  return {
    environments,
    releasePacks,
    summary: {
      activeReleasePacks: active.length,
      archivedReleasePacks: state.packs.length - active.length,
      contestedEnvironments: contested,
      packsNotObservedAnywhere: active.filter((p) => !progressions.get(p.id)!.observed).length,
      environmentsNeverObserved: neverObserved,
    },
  };
}

// ADR-008: the highest Stage at which any of the pack's contents was observed.
// Driven by Stage, not by path topology, which is what lets the Hotfix pack
// reach PRODUCTION with no pre-production Environment in its path.
function packStateView(packId: string) {
  const p = pack(packId);
  if (!p) throw notFound(`Release Pack ${packId} does not exist.`);

  const contents = new Set(p.contents.map((c) => c.applicationVersionId));
  const matched = state.observations.filter((o) => contents.has(o.applicationVersionId));

  let highest: Stage | null = null;
  for (const o of matched) {
    const e = env(o.environmentId);
    if (!e) continue;
    if (highest === null || STAGE_RANK[e.stage] > STAGE_RANK[highest]) highest = e.stage;
  }

  return {
    state: highest === null ? "PLANNED" : highest,
    sightings: matched
      .filter((o) => env(o.environmentId))
      .sort((a, b) => b.observedAt.localeCompare(a.observedAt))
      .map((o) => ({
        environment: envRef(o.environmentId),
        application: appRef(o.applicationId),
        applicationVersion: versionRef(o.applicationVersionId),
        observedAt: o.observedAt,
      })),
  };
}

// --- release documentation ---------------------------------------------------
// Mirrors MarkdownReleaseDocumentRenderer, including its Document Template
// handling (OQ-010, ADR-013). Deterministic for the same reason: no generation
// timestamp, stable ordering, absences stated rather than omitted — and a
// template that leaves a section out says so in the closing note rather than
// letting the section simply not be there.
//
// This is a second implementation of a renderer whose output must match the
// real one. `scripts/verify-demo-docgen.sh` replays one identical sequence of
// API calls against both this and a running Tower and diffs the documents, so
// drift is caught rather than discovered by a visitor. Run it after changing
// either renderer.

const dash = (v: string | null | undefined) => (v == null || v === "" ? "—" : v);
const oneLine = (v: string) =>
  !v || !v.trim() ? "—" : v.replace(/\|/g, "\\|").replace(/\s*\r?\n\s*/g, " ").trim();

// "A", "A and B", "A, B and C" — matching DocumentProvenance.
function sectionList(names: string[]): string {
  const headings = names.map((n) => DOCUMENT_SECTIONS.find((s) => s.name === n)?.heading ?? n);
  return headings
    .map((h, i) => (i === 0 ? h : (i === headings.length - 1 ? " and " : ", ") + h))
    .join("");
}

function releaseMarkdown(packId: string, template: TemplateRec | null): string {
  const p = pack(packId);
  if (!p) throw notFound(`Release Pack ${packId} does not exist.`);
  const view = packView(p);
  const derived = packStateView(packId);
  const out: string[] = [];

  out.push(`# Release Pack: ${p.name}\n`);
  if (p.description.trim()) out.push(`${p.description}\n`);

  // Driven by the template's own order, not by the declaration order of
  // DOCUMENT_SECTIONS. A template that puts Handover first is a template that
  // renders Handover first, here as in tower-docgen.
  const chosen = template ? template.sections : DOCUMENT_SECTIONS.map((s) => s.name);
  for (const section of chosen) {
    if (section === "STATUS") status(out, p, derived);
    else if (section === "PROMOTION_PATH") promotionPath(out, view);
    else if (section === "CONTENTS") contents(out, view);
    else if (section === "ARTIFACTS") artifacts(out, view);
    else if (section === "WORK_ITEMS") workItems(out, pack(packId)!);
    else if (section === "HANDOVER") handover(out, p);
    else if (section === "ITERATIONS") iterations(out, p);
    else if (section === "SIGHTINGS") sightings(out, derived);
  }

  out.push("---\n");
  out.push(`_${provenance(template)}`);
  out.push("This document is a view of that model, not a source of truth (BR-07), "
    + "and can be regenerated at any time._");
  return out.join("\n") + "\n";
}

// Mirrors DocumentProvenance: the template is named, and what it leaves out is
// named with it. Without that a reader cannot tell "no Iterations have been
// recorded" from "Iterations were not included in this document".
function provenance(template: TemplateRec | null): string {
  if (!template) return "Generated by Tower from the Canonical Model.";
  const omitted = DOCUMENT_SECTIONS.map((s) => s.name).filter((n) => !template.sections.includes(n));
  const origin = `Generated by Tower from the Canonical Model using the "${template.name}" template`;
  return omitted.length ? `${origin}, which does not include ${sectionList(omitted)}.` : `${origin}.`;
}

function status(out: string[], p: PackRec, derived: { state: string }) {
  out.push("| | |\n|---|---|");
  out.push(`| Observed state | ${derived.state} |`);
  out.push(`| Lifecycle | ${p.archived ? "Archived" : "Active"} |\n`);
  out.push("> Observed state is derived from Observations and records where this release has been");
  out.push("> seen. Lifecycle is a decision by the team and says nothing about deployment.\n");
}

function promotionPath(out: string[], view: ReturnType<typeof packView>) {
  out.push("## Promotion Path\n");
  if (!view.promotionPath) {
    out.push("_No Promotion Path has been assigned to this Release Pack._\n");
    return;
  }
  out.push(`**${view.promotionPath.pathName}** \u2014 version ${view.promotionPath.versionNumber}\n`);
  out.push(`${view.promotionPath.environments.map((e) => (e as Env).name).join(" \u2192 ")}\n`);
  out.push(`_This release follows version ${view.promotionPath.versionNumber} of the path. Later versions may define a different sequence; this is the`);
  out.push("topology the release was planned against (ADR-007)._\n");
}

function contents(out: string[], view: ReturnType<typeof packView>) {
  out.push("## Contents\n");
  if (!view.contents.length) {
    out.push("_No Application Versions have been added to this Release Pack._\n");
    return;
  }
  out.push("| Application | Version | Branch | Tag | Commit | Build |");
  out.push("|---|---|---|---|---|---|");
  [...view.contents]
    .sort((a, b) => a.applicationName.localeCompare(b.applicationName) || a.version.localeCompare(b.version))
    .forEach((c) => out.push(
      `| ${c.applicationName} | ${c.version} | ${dash(c.branch)} | ${dash(c.tag)} | ${dash(c.commit)} | ${dash(c.buildIdentifier)} |`));
  out.push("");
}

// Mirrors MarkdownReleaseDocumentRenderer.renderArtifacts (ADR-021, FR-086).
//
// Accepted digests only, never what a repository reports. That is the whole
// point of accepting one: a tag is mutable, and a document that printed the
// live digest would stop regenerating identically the day somebody re-pushed.
//
// The demo seeds none, and renders the empty state until a visitor accepts one
// on the Applications page. That is the honest default rather than an oversight
// - a digest is something a person accepted after looking at a repository, and
// this demonstration has none to look at.
function artifacts(out: string[], view: ReturnType<typeof packView>) {
  out.push("## Artifacts\n");

  const versionIds = new Set(view.contents.map((c) => c.versionId));
  const rows = state.acceptedArtifacts
    .filter((one) => versionIds.has(one.applicationVersionId))
    .map((one) => {
      const content = view.contents.find((c) => c.versionId === one.applicationVersionId);
      return {
        applicationName: content?.applicationName ?? "",
        version: content?.version ?? "",
        kind: one.kind,
        coordinate: one.coordinate,
        digest: one.digest,
      };
    })
    .sort((a, b) =>
      a.applicationName.localeCompare(b.applicationName)
      || a.version.localeCompare(b.version)
      || a.kind.localeCompare(b.kind));

  if (!rows.length) {
    out.push("_No artifact digests have been accepted for this release._\n");
    return;
  }

  out.push("| Application | Version | Kind | Coordinate | Digest |");
  out.push("|---|---|---|---|---|");
  rows.forEach((r) => out.push(
    `| ${r.applicationName} | ${r.version} | ${r.kind} | \`${r.coordinate}\` | \`${r.digest}\` |`));
  out.push("");
}

// Mirrors MarkdownReleaseDocumentRenderer.renderWorkItems (ADR-018).
// Identifier and accepted title only: no status, because the tracker owns that
// and a status printed into a document would be stale before it was read.
function workItems(out: string[], p: PackRec) {
  out.push("## Work Items\n");
  if (!p.workItems.length) {
    out.push("_No work items have been linked to this Release Pack._\n");
    return;
  }
  out.push("| Item | Title |");
  out.push("|---|---|");
  p.workItems.forEach((w) => out.push(
    `| ${w.identifier} | ${w.title.trim() ? w.title : "_no title accepted_"} |`));
  out.push("");
}

function handover(out: string[], p: PackRec) {
  out.push("## Handover\n");
  const h = p.handover;
  const prepared = Object.values(h).some((x) => (x ?? "").trim() !== "");
  if (!prepared) {
    out.push("_No Handover information has been prepared for this Release Pack._\n");
    return;
  }
  ([
    ["Deployment instructions", h.deploymentInstructions],
    ["Shell commands", h.shellCommands],
    ["Database migrations", h.databaseMigrations],
    ["Rollback procedure", h.rollbackProcedure],
    ["Validation notes", h.validationNotes],
    ["Operational notes", h.operationalNotes],
  ] as const).forEach(([heading, body]) => {
    out.push(`### ${heading}\n`);
    out.push(`${(body ?? "").trim() ? body : "_Not prepared._"}\n`);
  });
}

function iterations(out: string[], p: PackRec) {
  out.push("## Validation Iterations\n");
  if (!p.iterations.length) {
    out.push("_No validation Iterations have been recorded against this Release Pack._\n");
    return;
  }
  out.push("| Iteration | Started | Completed | Notes |\n|---|---|---|---|");
  [...p.iterations]
    .sort((a, b) => a.startedAt.localeCompare(b.startedAt) || a.name.localeCompare(b.name))
    .forEach((i) => out.push(
      `| ${i.name} | ${i.startedAt} | ${i.completedAt ?? "_in progress_"} | ${oneLine(i.notes)} |`));
  out.push("");
}

function sightings(out: string[], derived: ReturnType<typeof packStateView>) {
  out.push("## Where this release has been observed\n");
  if (!derived.sightings.length) {
    out.push("_This release has not been observed in any Environment._\n");
    return;
  }
  out.push("| Environment | Application | Version | Observed | Source | Observation |");
  out.push("|---|---|---|---|---|---|");
  derived.sightings.forEach((s) => {
    const o = state.observations.find(
      (x) => x.environmentId === (s.environment as Env).id
        && x.applicationVersionId === (s.applicationVersion as { id: string }).id
        && x.observedAt === s.observedAt);
    const src = o ? (o.source.actor ? `${o.source.collector} (${o.source.actor})` : o.source.collector) : "\u2014";
    out.push(`| ${(s.environment as Env).name} | ${(s.application as { name: string }).name} | `
      + `${(s.applicationVersion as { version: string }).version} | ${s.observedAt} | ${src} | ${o?.id ?? "\u2014"} |`);
  });
  out.push("");
}

// --- document templates -------------------------------------------------------

// The complete document is defined here rather than stored, exactly as the real
// service defines it in code: it is what Tower generates when nobody has
// expressed a preference, so it cannot be edited, renamed or deleted.
const completeTemplate = () => ({
  id: COMPLETE_TEMPLATE_ID,
  name: COMPLETE_TEMPLATE_NAME,
  sections: DOCUMENT_SECTIONS.map((s) => s.name),
  omitted: [] as string[],
  builtIn: true,
});

const templateView = (t: TemplateRec) => ({
  id: t.id,
  name: t.name,
  sections: [...t.sections],
  omitted: DOCUMENT_SECTIONS.map((s) => s.name).filter((n) => !t.sections.includes(n)),
  builtIn: false,
});

const builtInRefusal = (verb: string) =>
  `The complete document cannot be ${verb}. It is what Tower generates when no template is chosen;`
  + " define a template of your own instead.";

// An id that names nothing is an error rather than a reason to fall back: a
// fallback would hand someone the sections they had deliberately removed, with
// nothing on the page to say so.
function resolveTemplate(id: string | null): TemplateRec | null {
  if (!id || id === COMPLETE_TEMPLATE_ID) return null;
  const found = state.documentTemplates.find((t) => t.id === id);
  if (!found) throw notFound(`Document Template ${id} does not exist.`);
  return found;
}

function validTemplate(body: Json | null, beingUpdated: string | null): { name: string; sections: string[] } {
  const name = String(body?.name ?? "").trim();
  if (!name) throw badRequest("A Document Template must have a name.");
  if (name.toLowerCase() === COMPLETE_TEMPLATE_NAME.toLowerCase()) {
    throw badRequest(`"${COMPLETE_TEMPLATE_NAME}" names the built-in document that includes every`
      + " section. Choose another name.");
  }
  if (state.documentTemplates.some((t) => t.name.toLowerCase() === name.toLowerCase() && t.id !== beingUpdated)) {
    throw conflict(`A Document Template named "${name}" already exists.`);
  }

  const sections = Array.isArray(body?.sections) ? (body.sections as unknown[]).map(String) : [];
  if (!sections.length) {
    throw badRequest("A Document Template must include at least one section. A document with none"
      + " would be a title and a footer, which is a mistake rather than a preference.");
  }
  for (const s of sections) {
    if (!DOCUMENT_SECTIONS.some((known) => known.name === s)) {
      throw badRequest(`"${s}" is not a document section. Ask GET /api/document-templates/sections`
        + " for the ones this version of Tower offers.");
    }
  }
  if (new Set(sections).size !== sections.length) {
    throw badRequest("A Document Template may include each section at most once.");
  }
  return { name, sections };
}

// --- source control discovery (issue #3, ADR-014) ----------------------------

// Mirrors SourceControlCollector: apply the binding, match the pattern, mark
// what Tower already holds, and report refs the pattern missed rather than
// dropping them. Stores nothing, exactly as the real one does not — BR-01 makes
// an Application Version immutable, so discovery never creates one.
function discoverVersions(applicationId: string) {
  const binding = state.repositoryBindings.find((b) => b.applicationId === applicationId);
  if (!binding) {
    return {
      applicationId, repositoryUrl: null, candidates: [], unmatched: [],
      failure: "No repository is bound to this Application.",
    };
  }

  const refs = seed.sourceRefs[applicationId] ?? [];
  const candidates: {
    version: string; source: string; origin: string; branch: string | null; tag: string | null;
    commit: string | null; buildIdentifier: string | null; alreadyRegistered: boolean;
  }[] = [];
  const unmatched: string[] = [];
  const seen = new Set<string>();

  for (const ref of refs) {
    const wanted = ref.kind === "TAG"
      ? binding.refSelection === "TAGS" || binding.refSelection === "ALL"
      : binding.refSelection === "BRANCHES" || binding.refSelection === "ALL";
    // Filtered by the user's own choice, so not reported as a problem.
    if (!wanted) continue;

    const match = new RegExp(binding.versionPattern).exec(ref.name);
    if (!match || match[0] !== ref.name || !match[1]) {
      unmatched.push(ref.name);
      continue;
    }
    if (seen.has(match[1])) continue;
    seen.add(match[1]);

    candidates.push({
      version: match[1],
      // Which system proposed it (ADR-020). The demo has only source control to
      // offer - it reaches no CI system - but the field is filled honestly
      // rather than left out, because the real Tower puts build candidates in
      // this same list.
      source: "git",
      origin: ref.name,
      branch: ref.kind === "BRANCH" ? ref.name : null,
      tag: ref.kind === "TAG" ? ref.name : null,
      commit: ref.commit,
      // git has none, and ADR-014 refuses to derive one from a commit.
      buildIdentifier: null,
      alreadyRegistered: state.versions.some(
        (v) => v.applicationId === applicationId && v.version === match[1]),
    });
  }

  return { applicationId, repositoryUrl: binding.repositoryUrl, candidates, unmatched, failure: null };
}

// --- routing ----------------------------------------------------------------

const seg = (p: string) => p.split("?")[0].split("/").filter(Boolean);

// --- Artifacts (ADR-021) ----------------------------------------------------

// Composes a coordinate the way ArtifactCoordinateBinding does, and refuses an
// unrecognised token for the same reason: written with a small c,
// {shortcommit} would become a literal, the repository would truthfully answer
// that no such artifact exists, and the report would be useless.
const ARTIFACT_TOKENS = ["version", "commit", "shortCommit"];

function composeCoordinate(
  template: string, shortCommitLength: number, version: string, commit: string | null,
): { composed: string | null; missing: string[] } {
  const length = shortCommitLength || 7;
  const missing: string[] = [];
  let failed = false;

  const composed = template.replace(/\{([^{}]*)\}/g, (_whole, token: string) => {
    if (token === "version") return version;
    if (token === "commit") {
      if (!commit) { missing.push("commit"); failed = true; return ""; }
      return commit;
    }
    if (token === "shortCommit") {
      if (!commit) { missing.push("shortCommit"); failed = true; return ""; }
      return commit.slice(0, length);
    }
    failed = true;
    return "";
  });

  return { composed: failed ? null : composed, missing };
}

function validArtifactTemplate(template: string): string {
  const trimmed = String(template ?? "").trim();
  if (!trimmed) {
    throw badRequest("An artifact coordinate binding must carry a template,"
      + " for example docker-local/acme/api:{version}-{shortCommit}.");
  }
  const found = [...trimmed.matchAll(/\{([^{}]*)\}/g)].map((m) => m[1]);
  const unknown = found.filter((token) => !ARTIFACT_TOKENS.includes(token));
  if (unknown.length) {
    throw badRequest(`The coordinate template uses ${unknown.map((t) => `{${t}}`).join(", ")}`
      + " Tower does not recognise. Use {version}, {commit} or {shortCommit}.");
  }
  if (!found.length) {
    throw badRequest("The coordinate template names no part of the version, so every version would"
      + " resolve to the same artifact. Use at least one of {version}, {commit} or {shortCommit}.");
  }
  return trimmed;
}

// What the repositories say about one version's artifacts, and what somebody
// accepted, side by side.
//
// Every template is reported including the ones that found nothing, and nothing
// here is PRESENT: the demo reaches no repository, so every addressable
// coordinate comes back UNREAD with the reason stated. A demonstration that
// answered "in the repository" would be inventing a confirmation.
function confirmArtifactsFor(applicationVersionId: string): unknown {
  const v = version(applicationVersionId);
  if (!v) throw notFound(`Application Version ${applicationVersionId} does not exist.`);

  const templates = state.artifactBindings
    .filter((binding) => binding.applicationId === v.applicationId)
    .sort((x, y) => x.kind.localeCompare(y.kind));

  const artifacts = templates.map((binding) => {
    const accepted = state.acceptedArtifacts.find(
      (one) => one.applicationVersionId === applicationVersionId && one.kind === binding.kind);
    const { composed, missing } = composeCoordinate(
      binding.coordinateTemplate, binding.shortCommitLength, v.version, v.commit);

    if (composed === null) {
      return {
        kind: binding.kind, connectorId: binding.connectorId, system: binding.system,
        coordinate: null, digest: null, acceptedDigest: accepted?.digest ?? null,
        storedAt: null, sizeBytes: 0, url: null, state: "NOT_ADDRESSABLE",
        detail: `This version carries no ${missing.join(" or ")}, and the template needs one: `
          + binding.coordinateTemplate,
        diverged: false,
      };
    }

    return {
      kind: binding.kind, connectorId: binding.connectorId, system: binding.system,
      coordinate: composed, digest: null, acceptedDigest: accepted?.digest ?? null,
      storedAt: null, sizeBytes: 0, url: null, state: "UNREAD",
      detail: "This demonstration has no network access, so no repository can be read."
        + " Against a real Tower this says whether those bytes are there and what their digest is.",
      diverged: false,
    };
  });

  return {
    applicationVersionId, version: v.version, commit: v.commit, artifacts,
  };
}

export function handle(pathname: string, method: string, body: Json | null): unknown {
  const s = seg(pathname);
  if (s[0] !== "api") throw notFound(`No route for ${pathname}`);
  const [, area, a, b, c, d] = s;

  if (area === "health") return { status: "UP", version: "demo" };

  if (area === "dashboard") return dashboardView();

  if (area === "bindings") {
    if (a === "environments") {
      if (method === "GET") return state.environmentBindings;
      if (method === "PUT") {
        const incoming = body as unknown as EnvBinding;
        state.environmentBindings = state.environmentBindings.filter(
          (x) => !(x.environmentId === incoming.environmentId && x.connectorId === incoming.connectorId));
        state.environmentBindings.push(incoming);
        return incoming;
      }
      if (method === "DELETE") {
        state.environmentBindings = state.environmentBindings.filter((x) => x.environmentId !== b);
        return null;
      }
    }
    if (a === "applications") {
      if (method === "GET") return state.applicationBindings;
      if (method === "PUT") {
        const incoming = body as unknown as AppBinding;
        const pattern = incoming.versionPattern?.trim() || "^(.+)$";
        try {
          const compiled = new RegExp(pattern);
          if (new RegExp(compiled.source + "|").exec("")!.length - 1 < 1) {
            throw badRequest("The version pattern must contain a capturing group marking the version,"
              + " for example ^release-(.+)$. Use ^(.+)$ to treat the whole tag as the version.");
          }
        } catch (e) {
          if (e instanceof ApiFailure) throw e;
          throw badRequest("The version pattern is not a valid regular expression.");
        }
        const saved = { ...incoming, versionPattern: pattern };
        state.applicationBindings = state.applicationBindings.filter(
          (x) => !(x.applicationId === saved.applicationId && x.connectorId === saved.connectorId));
        state.applicationBindings.push(saved);
        return saved;
      }
      if (method === "DELETE") {
        state.applicationBindings = state.applicationBindings.filter((x) => x.applicationId !== b);
        return null;
      }
    }
    if (a === "repositories") {
      if (method === "GET") return state.repositoryBindings;
      if (method === "PUT") {
        const incoming = body as unknown as RepoBinding;
        const pattern = incoming.versionPattern?.trim() || "^(.+)$";
        try {
          const compiled = new RegExp(pattern);
          if (new RegExp(compiled.source + "|").exec("")!.length - 1 < 1) {
            throw badRequest("The version pattern must contain a capturing group marking the version,"
              + " for example ^v(.+)$. Use ^(.+)$ to treat the whole ref name as the version.");
          }
        } catch (e) {
          if (e instanceof ApiFailure) throw e;
          throw badRequest("The version pattern is not a valid regular expression.");
        }
        const saved = { ...incoming, versionPattern: pattern, refSelection: incoming.refSelection || "TAGS" };
        state.repositoryBindings = state.repositoryBindings.filter(
          (x) => !(x.applicationId === saved.applicationId && x.connectorId === saved.connectorId));
        state.repositoryBindings.push(saved);
        return saved;
      }
      if (method === "DELETE") {
        state.repositoryBindings = state.repositoryBindings.filter((x) => x.applicationId !== b);
        return null;
      }
    }
    // Where the team's work items live (ADR-018). Keyed by Connector alone —
    // no Tower concept on the left, unlike every binding above.
    if (a === "issue-trackers") {
      if (method === "GET") return state.issueTrackerBindings;
      if (method === "PUT") {
        const incoming = body as unknown as { connectorId: string; locator: string };
        const connectorId = String(incoming?.connectorId ?? "").trim();
        const locator = String(incoming?.locator ?? "").trim();
        if (!connectorId) throw badRequest("A binding must name the Connector it configures.");
        if (!locator) throw badRequest("A binding must name where the tracker lives,"
          + " in the form that Connector expects.");
        state.issueTrackerBindings = state.issueTrackerBindings.filter(
          (x) => x.connectorId !== connectorId);
        const saved = { connectorId, locator };
        state.issueTrackerBindings.push(saved);
        return saved;
      }
      if (method === "DELETE") {
        state.issueTrackerBindings = state.issueTrackerBindings.filter((x) => x.connectorId !== b);
        return null;
      }
    }
    // How to address the artifacts an Application Version produced (ADR-021).
    // The only binding here an Application may have several of for one
    // Connector, so the key carries the kind and so does the delete path.
    // Which job's runs mean an Application reached an Environment (ADR-020).
    // Two Tower ids in the key, so the delete path carries both.
    // Which job's runs build an Application (ADR-020). No Environment, and the
    // job is in the key: two build jobs for one Application are ordinary.
    if (a === "build-jobs") {
      if (method === "GET") return state.buildBindings;
      if (method === "PUT") {
        const incoming = body as unknown as BuildBinding;
        const applicationId = String(incoming?.applicationId ?? "").trim();
        const connectorId = String(incoming?.connectorId ?? "").trim();
        const system = String(incoming?.system ?? "").trim();
        const jobPath = String(incoming?.job ?? "").trim();
        if (!applicationId) throw badRequest("A build job binding must name the Application its"
          + " runs build.");
        if (!connectorId) throw badRequest("A build job binding must name its connectorId.");
        if (!system) throw badRequest("A build job binding must name the CI server to read.");
        if (!jobPath) throw badRequest("A build job binding must name the job to read.");
        const versionSource = (incoming?.versionSource ?? "PARAMETER");
        if (!["PARAMETER", "RUN_NAME", "JOB_PATH"].includes(versionSource)) {
          throw badRequest(`'${versionSource}' is not a version source. Use PARAMETER, RUN_NAME`
            + " or JOB_PATH.");
        }
        const pattern = incoming?.versionPattern?.trim() || "^(.+)$";
        try {
          const compiled = new RegExp(pattern);
          if (new RegExp(compiled.source + "|").exec("")!.length - 1 < 1) {
            throw badRequest("The version pattern must contain a capturing group marking the"
              + " version, for example ^release-(.+)$. Use ^(.+)$ to take the whole value.");
          }
        } catch (e) {
          if (e instanceof ApiFailure) throw e;
          throw badRequest("The version pattern is not a valid regular expression.");
        }
        const versionKey = versionSource === "PARAMETER"
          ? String(incoming?.versionKey ?? "").trim() : "";
        const saved: BuildBinding = {
          applicationId, connectorId, system, job: jobPath,
          versionSource, versionKey, versionPattern: pattern,
        };
        state.buildBindings = state.buildBindings.filter(
          (x) => !(x.applicationId === applicationId && x.connectorId === connectorId
            && x.job === jobPath));
        state.buildBindings.push(saved);
        return saved;
      }
      if (method === "DELETE") {
        // The job arrives in the query rather than the path: a job path
        // contains slashes, and a path segment would need escaping the server
        // would then have to undo.
        const params = new URLSearchParams(pathname.split("?")[1] ?? "");
        const jobPath = params.get("job") ?? "";
        state.buildBindings = state.buildBindings.filter(
          (x) => !(x.applicationId === b && x.job === jobPath));
        return null;
      }
    }

    if (a === "pipeline-jobs") {
      if (method === "GET") return state.pipelineBindings;
      if (method === "PUT") {
        const incoming = body as unknown as PipelineBinding;
        const environmentId = String(incoming?.environmentId ?? "").trim();
        const applicationId = String(incoming?.applicationId ?? "").trim();
        const connectorId = String(incoming?.connectorId ?? "").trim();
        const system = String(incoming?.system ?? "").trim();
        const jobPath = String(incoming?.job ?? "").trim();
        if (!environmentId) throw badRequest("A pipeline job binding must name the Environment"
          + " its runs concern.");
        if (!applicationId) throw badRequest("A pipeline job binding must name the Application"
          + " its runs concern.");
        if (!connectorId) throw badRequest("A pipeline job binding must name its connectorId.");
        if (!system) throw badRequest("A pipeline job binding must name the CI server to read.");
        if (!jobPath) throw badRequest("A pipeline job binding must name the job to read.");
        const versionSource = (incoming?.versionSource ?? "PARAMETER");
        if (!["PARAMETER", "RUN_NAME", "JOB_PATH"].includes(versionSource)) {
          throw badRequest(`'${versionSource}' is not a version source. Use PARAMETER, RUN_NAME`
            + " or JOB_PATH.");
        }
        const pattern = incoming?.versionPattern?.trim() || "^(.+)$";
        try {
          const compiled = new RegExp(pattern);
          if (new RegExp(compiled.source + "|").exec("")!.length - 1 < 1) {
            throw badRequest("The version pattern must contain a capturing group marking the"
              + " version, for example ^release-(.+)$. Use ^(.+)$ to take the whole value.");
          }
        } catch (e) {
          if (e instanceof ApiFailure) throw e;
          throw badRequest("The version pattern is not a valid regular expression.");
        }
        // Empty except when the version comes from a parameter: an absent name
        // and an empty one mean the same thing here.
        const versionKey = versionSource === "PARAMETER"
          ? String(incoming?.versionKey ?? "").trim() : "";
        const saved: PipelineBinding = {
          environmentId, applicationId, connectorId, system, job: jobPath,
          versionSource, versionKey, versionPattern: pattern,
        };
        state.pipelineBindings = state.pipelineBindings.filter(
          (x) => !(x.environmentId === environmentId && x.applicationId === applicationId
            && x.connectorId === connectorId));
        state.pipelineBindings.push(saved);
        return saved;
      }
      if (method === "DELETE") {
        state.pipelineBindings = state.pipelineBindings.filter(
          (x) => !(x.environmentId === b && x.applicationId === c));
        return null;
      }
    }

    if (a === "artifact-coordinates") {
      if (method === "GET") return state.artifactBindings;
      if (method === "PUT") {
        const incoming = body as unknown as ArtifactBinding;
        const applicationId = String(incoming?.applicationId ?? "").trim();
        const connectorId = String(incoming?.connectorId ?? "").trim();
        const kind = String(incoming?.kind ?? "").trim().toLowerCase();
        const system = String(incoming?.system ?? "").trim();
        if (!applicationId) throw badRequest("An artifact coordinate binding must name the Application"
          + " whose artifacts these are.");
        if (!connectorId) throw badRequest("An artifact coordinate binding must name its connectorId.");
        if (!kind) throw badRequest("An artifact coordinate binding must name its artifact kind.");
        if (!system) throw badRequest("An artifact coordinate binding must name its repository system.");
        const shortCommitLength = Number(incoming?.shortCommitLength) || 7;
        if (shortCommitLength < 4 || shortCommitLength > 40) {
          throw badRequest("The short commit length must be between 4 and 40 characters.");
        }
        const saved: ArtifactBinding = {
          applicationId, connectorId, kind, system,
          coordinateTemplate: validArtifactTemplate(incoming?.coordinateTemplate),
          shortCommitLength,
        };
        state.artifactBindings = state.artifactBindings.filter(
          (x) => !(x.applicationId === applicationId && x.connectorId === connectorId && x.kind === kind));
        state.artifactBindings.push(saved);
        return saved;
      }
      if (method === "DELETE") {
        const kind = String(c ?? "").toLowerCase();
        state.artifactBindings = state.artifactBindings.filter(
          (x) => !(x.applicationId === b && x.kind === kind));
        return null;
      }
    }

    if (a === "coordinate-preview") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      const template = validArtifactTemplate(params.get("coordinateTemplate") ?? "");
      const version_ = params.get("version") ?? "";
      if (!version_) throw badRequest("Supply a version to compose the coordinate from.");
      const { composed, missing } = composeCoordinate(
        template, Number(params.get("shortCommitLength")) || 0, version_, params.get("commit"));
      return { coordinateTemplate: template, composed, missing };
    }

    if (a === "version-preview") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      const imageTag = params.get("imageTag") ?? "";
      const versionPattern = params.get("versionPattern")?.trim() || "^(.+)$";
      let matched = false;
      let version: string | null = null;
      try {
        const m = new RegExp(versionPattern).exec(imageTag);
        // Whole-string match only, as the server requires.
        if (m && m[0] === imageTag && m[1]) { matched = true; version = m[1]; }
      } catch {
        throw badRequest("The version pattern is not a valid regular expression.");
      }
      return { imageTag, versionPattern, matched, version };
    }
  }

  // OQ-010, ADR-013. Templates choose sections; they carry no markup, so there
  // is no document body to accept here either.
  if (area === "document-templates") {
    if (a === "sections") return DOCUMENT_SECTIONS;

    if (!a && method === "GET") {
      return [completeTemplate(), ...state.documentTemplates.map(templateView)];
    }
    if (!a && method === "POST") {
      const created = { id: newId("template"), ...validTemplate(body, null) };
      state.documentTemplates.push(created);
      return templateView(created);
    }
    if (a && method === "GET") {
      if (a === COMPLETE_TEMPLATE_ID) return completeTemplate();
      const found = state.documentTemplates.find((t) => t.id === a);
      if (!found) throw notFound(`Document Template ${a} does not exist.`);
      return templateView(found);
    }
    if (a && method === "PUT") {
      if (a === COMPLETE_TEMPLATE_ID) throw badRequest(builtInRefusal("changed"));
      const found = state.documentTemplates.find((t) => t.id === a);
      if (!found) throw notFound(`Document Template ${a} does not exist.`);
      const { name, sections } = validTemplate(body, a);
      found.name = name;
      found.sections = sections;
      return templateView(found);
    }
    if (a && method === "DELETE") {
      if (a === COMPLETE_TEMPLATE_ID) throw badRequest(builtInRefusal("deleted"));
      state.documentTemplates = state.documentTemplates.filter((t) => t.id !== a);
      return null;
    }
  }

  if (area === "source-versions") {
    if (a === "connection-test") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      const url = params.get("repositoryUrl") ?? "";
      // The demo reaches no repository, and says so rather than pretending.
      return {
        connectorId: "git", target: url, scope: "whole repository", reachable: false,
        message: "This demonstration has no network access, so no repository can be read."
          + " Against a real Tower this reports whether the remote answered.",
      };
    }
    if (!a) return state.repositoryBindings.map((b) => discoverVersions(b.applicationId));
  }

  if (area === "work-items" && a === "connection-test") {
    const params = new URLSearchParams(pathname.split("?")[1] ?? "");
    const connectorId = params.get("connectorId") ?? "";
    const bound = state.issueTrackerBindings.find((x) => x.connectorId === connectorId);
    if (!bound) {
      return { connectorId, locator: null, reachable: false,
        message: "No tracker is bound to this Connector yet." };
    }
    // No network here either, and said plainly rather than answered with a
    // reassuring tick a visitor could not tell from a real one.
    return { connectorId, locator: bound.locator, reachable: false,
      message: "This demonstration has no network access, so no tracker can be read."
        + " Against a real Tower this reports whether the tracker answered." };
  }

  if (area === "artifacts" && a === "connection-test") {
    const params = new URLSearchParams(pathname.split("?")[1] ?? "");
    // No network here either, and said plainly rather than answered with a
    // reassuring tick a visitor could not tell from a real one.
    return {
      connectorId: params.get("connectorId"), target: params.get("system"),
      scope: "whole repository", reachable: false,
      message: "This demonstration has no network access, so no repository can be read."
        + " Against a real Tower this reports whether the repository answered.",
    };
  }

  if (area === "credentials") {
    const params = new URLSearchParams(pathname.split("?")[1] ?? "");
    const key = `${params.get("connectorId")}@${params.get("target")}`;
    if (method === "GET") {
      const at = state.credentials[key];
      return {
        connectorId: params.get("connectorId"), target: params.get("target"),
        configured: at !== undefined, updatedAt: at ?? null,
      };
    }
    if (method === "PUT") {
      const incoming = body as unknown as { connectorId: string; target: string; secret: string };
      if (!incoming?.secret) throw badRequest("secret is required");
      // The demo stores only the time it was saved. There is nowhere here for a
      // token to be read back from, which is the property the real API has too.
      state.credentials[`${incoming.connectorId}@${incoming.target}`] = new Date().toISOString();
      return null;
    }
    if (method === "DELETE") {
      delete state.credentials[key];
      return null;
    }
  }

  // Reading a CI system's runs (ADR-020). Its own path rather than more verbs
  // under /api/sync, matching the separation the reports themselves keep: what
  // the two read, and what a clean read of each establishes, are different
  // things.
  if (area === "pipeline-sync") {
    if (a === "reports") return state.pipelineReports.slice(0, 10);
    if (a === "connection-test") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      return {
        connectorId: params.get("connectorId"), target: params.get("system"),
        scope: params.get("job"), reachable: false,
        message: "This is the demonstration. No CI system is contacted, so no connection can be"
          + " made. Against a real Tower this reports whether the job could be read.",
      };
    }
    if (method === "POST") {
      const report: PipelineReportRec = {
        id: newId("pipeline-report"), connectorId: "jenkins",
        startedAt: new Date().toISOString(), finishedAt: new Date().toISOString(),
        jobsRead: 0, runsRead: 0, observationsAppended: 0,
        // Neither claim is made: nothing was read, so nothing is established.
        // Saying "nothing was deployed" here would be the demo asserting a fact
        // it has no basis for.
        readEverything: false, confirmsNothingWasDeployed: false,
        notRecorded: [],
        failures: ["This is the demonstration. No CI system was contacted and nothing was"
          + " recorded."],
      };
      state.pipelineReports.unshift(report);
      return [report];
    }
  }

  if (area === "sync") {
    if (a === "runs") return state.runs.slice(0, 10);
    if (a === "connection-test") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      return {
        connectorId: params.get("connectorId"), target: params.get("target"), scope: params.get("scope"),
        reachable: false,
        message: "This is the demonstration. No cluster is contacted, so no connection can be made.",
      };
    }
    if (a === "last-confirmation") {
      const latest = state.runs.find((r) => r.confirmsLiveness);
      if (!latest) throw notFound("No successful run.");
      return latest;
    }
    if (method === "POST") {
      const run: RunRec = {
        id: newId("run"), connectorId: "kubernetes",
        startedAt: new Date().toISOString(), finishedAt: new Date().toISOString(),
        outcome: "FAILED", workloadsRead: 0, observationsAppended: 0,
        foundNoChange: false, confirmsLiveness: false, unrecognized: [],
        failures: ["This is the demonstration. Nothing was contacted and nothing was recorded."],
      };
      state.runs.unshift(run);
      return [run];
    }
  }

  if (area === "portability") {
    if (a === "instance") return { name: seed.DEMO_INSTANCE };
    if (a === "export") {
      return {
        schemaVersion: 1, exportedFrom: seed.DEMO_INSTANCE, exportedAt: new Date().toISOString(),
        environments: state.environments, applications: state.applications,
        applicationVersions: state.versions, promotionPaths: state.paths,
        releasePacks: state.packs, observations: state.observations,
      };
    }
    if (a === "import") {
      const applied = b !== "preview";
      const file = (body ?? {}) as { exportedFrom?: string; schemaVersion?: number; releasePacks?: unknown[] };
      return {
        applied,
        sourceInstance: file.exportedFrom ?? "unknown",
        schemaVersion: file.schemaVersion ?? 1,
        entries: [
          { type: "Environment", id: "demo", name: "UAT", outcome: "SKIPPED",
            detail: "Already present; kept the local record." },
          { type: "Release Pack", id: "demo", name: "Release 2026.09", outcome: "CREATED", detail: null },
          { type: "Observation", id: "demo", name: "2026-07-20", outcome: "CREATED",
            detail: `Transferred from ${file.exportedFrom ?? "another instance"}.` },
        ],
      };
    }
  }

  if (area === "environments") {
    if (!a && method === "GET") return state.environments;
    if (!a && method === "POST") {
      const e = { id: newId("env"), name: String(body?.name ?? ""), stage: body?.stage as Stage };
      if (state.environments.some((x) => x.name.toLowerCase() === e.name.toLowerCase()))
        throw conflict(`An Environment named '${e.name}' already exists.`);
      state.environments.push(e);
      return e;
    }
    if (a && b === "state" && c === "comparison") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      const against = params.get("against");
      // Comparing an Environment with itself at two instants is the common
      // case, so the other side defaults to the same Environment.
      return stateComparisonView(
        a, params.get("at"), against && against !== "" ? against : a, params.get("againstAt"));
    }
    if (a && b === "state") {
      const params = new URLSearchParams(pathname.split("?")[1] ?? "");
      return environmentStateView(a, params.get("at"));
    }
    if (a && b === "observations") {
      return state.observations
        .filter((o) => o.environmentId === a)
        .sort((x, y) => y.observedAt.localeCompare(x.observedAt))
        .map(observationView);
    }
    if (a && method === "PUT") {
      const e = env(a); if (!e) throw notFound(`Environment ${a} does not exist.`);
      e.name = String(body?.name ?? e.name); e.stage = (body?.stage as Stage) ?? e.stage;
      return e;
    }
    if (a && method === "DELETE") {
      const using = state.paths.filter((p) => p.versions.some((v) => v.environmentIds.includes(a)));
      if (using.length)
        throw conflict(`Environment '${env(a)?.name}' is referenced by Promotion Path(s): `
          + `${using.map((p) => p.name).sort().join(", ")}. Remove it from those paths, or archive them, before deleting it.`);
      state.environments = state.environments.filter((x) => x.id !== a);
      return null;
    }
  }

  if (area === "applications") {
    if (!a && method === "GET") return state.applications;
    if (!a && method === "POST") {
      const x = { id: newId("app"), name: String(body?.name ?? ""), description: String(body?.description ?? "") };
      state.applications.push(x); return x;
    }
    if (a && b === "versions") return state.versions.filter((v) => v.applicationId === a);
    if (a && b === "source-versions") {
      if (!app(a)) throw notFound(`Application ${a} does not exist.`);
      return discoverVersions(a);
    }
    if (a && method === "PUT") {
      const x = app(a); if (!x) throw notFound(`Application ${a} does not exist.`);
      x.name = String(body?.name ?? x.name); x.description = String(body?.description ?? x.description);
      return x;
    }
    if (a && method === "DELETE") {
      if (state.versions.some((v) => v.applicationId === a))
        throw conflict(`Application '${app(a)?.name}' still has registered version(s). Remove them first.`);
      state.applications = state.applications.filter((x) => x.id !== a);
      return null;
    }
  }

  if (area === "application-versions") {
    if (!a && method === "GET") return state.versions;
    if (!a && method === "POST") {
      const v = {
        id: newId("ver"), applicationId: String(body?.applicationId ?? ""),
        version: String(body?.version ?? ""),
        branch: (body?.branch as string) || null, tag: (body?.tag as string) || null,
        commit: (body?.commit as string) || null, buildIdentifier: (body?.buildIdentifier as string) || null,
      };
      state.versions.push(v); return v;
    }
    if (a && method === "DELETE") {
      const holding = state.packs.filter((p) => p.contents.some((c) => c.applicationVersionId === a));
      if (holding.length)
        throw conflict(`This Application Version is contained in Release Pack(s): `
          + `${holding.map((p) => p.name).sort().join(", ")}. Remove it from them before deleting it.`);
      state.versions = state.versions.filter((v) => v.id !== a);
      return null;
    }
    // Confirming artifacts, and accepting a digest (ADR-021, FR-086).
    if (a && b === "artifacts" && !c) return confirmArtifactsFor(a);
    if (a && b === "artifacts" && c && d === "accepted-digest") {
      const kind = c.toLowerCase();
      if (!version(a)) throw notFound(`Application Version ${a} does not exist.`);
      if (method === "PUT") {
        const coordinate = String((body as { coordinate?: string })?.coordinate ?? "").trim();
        const digest = String((body as { digest?: string })?.digest ?? "").trim();
        if (!coordinate) throw badRequest("coordinate is required");
        // A coordinate on its own names a tag, and a tag can be pushed over.
        if (!digest) throw badRequest("digest is required");
        const accepted = {
          applicationVersionId: a, kind, coordinate, digest,
          acceptedAt: new Date().toISOString(),
        };
        // Replaced rather than appended: what a release shipped is the digest
        // somebody stands behind, not the sequence of times they looked.
        state.acceptedArtifacts = state.acceptedArtifacts.filter(
          (one) => !(one.applicationVersionId === a && one.kind === kind));
        state.acceptedArtifacts.push(accepted);
        return accepted;
      }
      if (method === "DELETE") {
        state.acceptedArtifacts = state.acceptedArtifacts.filter(
          (one) => !(one.applicationVersionId === a && one.kind === kind));
        return null;
      }
    }
    if (a) return version(a) ?? (() => { throw notFound(`Application Version ${a} does not exist.`); })();
  }

  if (area === "promotion-paths") {
    if (!a && method === "GET") return state.paths.map(pathView);
    if (!a && method === "POST") {
      const p = {
        id: newId("path"), name: String(body?.name ?? ""), archived: false,
        versions: [{ number: 1, createdAt: new Date().toISOString(),
          environmentIds: (body?.environmentIds as string[]) ?? [] }],
      };
      state.paths.push(p); return pathView(p);
    }
    const p = a ? path(a) : undefined;
    if (a && !p) throw notFound(`Promotion Path ${a} does not exist.`);
    if (p && b === "versions" && method === "POST") {
      p.versions.push({ number: p.versions.length + 1, createdAt: new Date().toISOString(),
        environmentIds: (body?.environmentIds as string[]) ?? [] });
      return pathView(p);
    }
    if (p && b === "name") { p.name = String(body?.name ?? p.name); return pathView(p); }
    if (p && b === "archive") { p.archived = true; return pathView(p); }
    if (p && b === "restore") { p.archived = false; return pathView(p); }
    if (p && method === "DELETE") {
      const followers = state.packs.filter((x) => x.promotionPathId === p.id);
      if (followers.length)
        throw conflict(`Promotion Path '${p.name}' is followed by Release Pack(s): `
          + `${followers.map((x) => x.name).sort().join(", ")}. Archive it instead of deleting it.`);
      if (p.versions.length > 1)
        throw conflict(`Promotion Path '${p.name}' has ${p.versions.length} published versions.`
          + " Archive it instead of deleting it, so the topology earlier versions describe is preserved.");
      state.paths = state.paths.filter((x) => x.id !== p.id);
      return null;
    }
    if (p) return pathView(p);
  }

  if (area === "release-packs") {
    if (!a && method === "GET") return state.packs.map(packView);
    if (!a && method === "POST") {
      const p = {
        id: newId("pack"), name: String(body?.name ?? ""), description: String(body?.description ?? ""),
        archived: false, promotionPathId: null as string | null, promotionPathVersion: null as number | null,
        contents: [] as { applicationId: string; applicationVersionId: string }[],
        workItems: [] as { identifier: string; title: string }[],
        handover: { deploymentInstructions: "", shellCommands: "", databaseMigrations: "",
          rollbackProcedure: "", validationNotes: "", operationalNotes: "" },
        iterations: [],
      } satisfies PackRec;
      state.packs.push(p); return packView(p);
    }
    const p = a ? pack(a) : undefined;
    if (a && !p) throw notFound(`Release Pack ${a} does not exist.`);
    if (!p) throw notFound(`No route for ${pathname}`);

    if (b === "state") return packStateView(p.id);
    if (b === "progression") return packProgressionView(p.id);

    // Work items (ADR-018). Intent: linking checks nothing against a tracker,
    // so it succeeds here exactly as it does against a real Tower with no
    // Connector configured.
    if (b === "work-items" && c === "resolved") {
      // The demo reaches no tracker and says so, rather than fabricating
      // titles and statuses a visitor would have no way to know are invented.
      // This is also the honest UNRESOLVED state the real Tower shows when
      // nothing is configured.
      return {
        connectorId: null,
        reachedTracker: false,
        failure: "This demonstration has no issue tracker, so Tower cannot say what these items are"
          + " now. The titles shown are the ones already accepted, which is what documents print.",
        items: p.workItems.map((w) => ({
          identifier: w.identifier, acceptedTitle: w.title,
          trackerTitle: null, status: null, closed: false, url: null,
          state: "UNRESOLVED", diverged: false,
        })),
      };
    }
    if (b === "work-items") {
      const same = (x: string, y: string) => x.toLowerCase() === y.toLowerCase();

      if (method === "POST") {
        const identifier = String(body?.identifier ?? "").trim();
        if (!identifier) throw badRequest("A work item reference must name the item,"
          + " as the tracker writes it.");
        if (p.workItems.some((w) => same(w.identifier, identifier)))
          throw conflict(`This Release Pack already delivers ${identifier}.`);
        p.workItems.push({ identifier, title: String(body?.title ?? "").trim() });
        return packView(p);
      }
      if (method === "PUT" && c === "title") {
        const identifier = String(body?.identifier ?? "").trim();
        const found = p.workItems.find((w) => same(w.identifier, identifier));
        if (!found) throw conflict(`This Release Pack does not deliver ${identifier}.`);
        found.title = String(body?.title ?? "").trim();
        return packView(p);
      }
      if (method === "DELETE") {
        const identifier = (new URLSearchParams(pathname.split("?")[1] ?? "")
          .get("identifier") ?? "").trim();
        const before = p.workItems.length;
        p.workItems = p.workItems.filter((w) => !same(w.identifier, identifier));
        if (p.workItems.length === before)
          throw conflict(`This Release Pack does not deliver ${identifier}.`);
        return packView(p);
      }
    }
    if (b === "documentation") {
      if (c === "markdown") {
        const requested = new URLSearchParams(pathname.split("?")[1] ?? "").get("template");
        return releaseMarkdown(p.id, resolveTemplate(requested));
      }
      return { packName: p.name };
    }
    if (b === "versions" && c) {
      if (method === "POST") {
        const v = version(c); if (!v) throw notFound(`Application Version ${c} does not exist.`);
        if (p.contents.some((x) => x.applicationVersionId === c))
          throw conflict("This Application Version is already in the Release Pack.");
        if (p.contents.some((x) => x.applicationId === v.applicationId))
          throw conflict("The Release Pack already contains a different version of this Application."
            + " Remove it first, since only one version of an Application can be delivered.");
        p.contents.push({ applicationId: v.applicationId, applicationVersionId: v.id });
      } else {
        p.contents = p.contents.filter((x) => x.applicationVersionId !== c);
      }
      return packView(p);
    }
    if (b === "promotion-path") {
      if (method === "DELETE") { p.promotionPathId = null; p.promotionPathVersion = null; }
      else { p.promotionPathId = String(body?.pathId ?? ""); p.promotionPathVersion = Number(body?.versionNumber ?? 1); }
      return packView(p);
    }
    if (b === "handover" && c === "history") {
      return state.handoverRevisions
        .filter((r) => r.releasePackId === p.id)
        .sort((x, y) => y.revisionNumber - x.revisionNumber)
        .map((r, index) => ({
          id: r.id, revisionNumber: r.revisionNumber, recordedAt: r.recordedAt,
          current: index === 0,
          empty: Object.values(r.handover).every((v) => !(v ?? "").trim()),
          ...r.handover,
        }));
    }
    if (b === "handover") {
      p.handover = { ...p.handover, ...(body as object) };
      // Appended, never replaced - the whole point of ADR-016.
      const highest = state.handoverRevisions
        .filter((r) => r.releasePackId === p.id)
        .reduce((max, r) => Math.max(max, r.revisionNumber), 0);
      state.handoverRevisions.push({
        id: newId("hrev"), releasePackId: p.id, revisionNumber: highest + 1,
        recordedAt: new Date().toISOString(), handover: { ...p.handover },
      });
      return packView(p);
    }
    if (b === "iterations") {
      if (!c && method === "POST") {
        p.iterations.push({ id: newId("it"), name: String(body?.name ?? ""),
          startedAt: (body?.startedAt as string) || new Date().toISOString(),
          completedAt: null, notes: String(body?.notes ?? "") });
        return packView(p);
      }
      const it = p.iterations.find((x) => x.id === c);
      if (!it) throw notFound(`Iteration ${c} does not belong to Release Pack '${p.name}'.`);
      if (d === "complete") it.completedAt = (body?.completedAt as string) || new Date().toISOString();
      else if (d === "reopen") it.completedAt = null;
      else if (d === "notes") it.notes = String(body?.notes ?? "");
      else if (method === "DELETE") p.iterations = p.iterations.filter((x) => x.id !== c);
      return packView(p);
    }
    if (b === "archive") { p.archived = true; return packView(p); }
    if (b === "restore") { p.archived = false; return packView(p); }
    if (method === "PUT") {
      p.name = String(body?.name ?? p.name); p.description = String(body?.description ?? p.description);
      return packView(p);
    }
    if (method === "DELETE") {
      if (p.iterations.length)
        throw conflict(`Release Pack '${p.name}' has ${p.iterations.length} validation Iteration(s) recorded`
          + " against it. Archive it instead of deleting it, so that history is preserved.");
      state.packs = state.packs.filter((x) => x.id !== p.id);
      return null;
    }
    return packView(p);
  }

  if (area === "observations" && method === "POST") {
    const environmentId = String(body?.environmentId ?? "");
    const applicationVersionId = String(body?.applicationVersionId ?? "");
    const v = version(applicationVersionId);
    if (!env(environmentId)) throw notFound(`Environment ${environmentId} does not exist.`);
    if (!v) throw notFound(`Application Version ${applicationVersionId} does not exist.`);
    const observedAt = (body?.observedAt as string) || new Date().toISOString();
    if (new Date(observedAt).getTime() > Date.now())
      throw conflict("An Observation cannot be dated in the future."
        + " Tower records what has been seen, not what is planned.");
    const o = {
      id: newId("obs"), environmentId, applicationId: v.applicationId, applicationVersionId,
      observedAt,
      // Provenance comes from the Collector, never the caller (ADR-006).
      source: { collector: "manual", actor: "you", originInstance: null, manual: true },
    };
    state.observations.push(o);
    return observationView(o);
  }

  throw notFound(`No route for ${method} ${pathname}`);
}

export { ApiFailure };
