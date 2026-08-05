// Typed client for tower-api. The Viewer communicates exclusively with
// tower-api (see docs/planning/Implementation-Plan.md, "tower-web").
//
// In development, Vite proxies "/api" to the backend at 127.0.0.1:8080
// (see vite.config.ts). In production the built assets are served by
// tower-api itself, so "/api" resolves against the same origin.

export interface HealthStatus {
  status: string;
}

// --- Environments -----------------------------------------------------

// Stage classifies an Environment independently of its name (ADR-008).
// Different Environment names may share a Stage, e.g. "SIT1" and "UAT"
// can both be VALIDATION.
export type Stage = "DEVELOPMENT" | "VALIDATION" | "PRE_PRODUCTION" | "PRODUCTION";

export const STAGES: Stage[] = ["DEVELOPMENT", "VALIDATION", "PRE_PRODUCTION", "PRODUCTION"];

export interface Environment {
  id: string;
  name: string;
  stage: Stage;
}

export interface EnvironmentInput {
  name: string;
  stage: Stage;
}

// --- Promotion Paths ----------------------------------------------------

// A Promotion Path is versioned (ADR-007): every edit produces a new
// immutable version rather than rewriting history in place.
export interface PathVersion {
  number: number;
  createdAt: string;
  environments: Environment[];
}

export interface PathView {
  id: string;
  name: string;
  archived: boolean;
  currentVersion: PathVersion;
  versions: PathVersion[];
}

export interface CreatePathInput {
  name: string;
  environmentIds: string[];
}

export interface NewVersionInput {
  environmentIds: string[];
}

// --- Errors ---------------------------------------------------------------

// Shape returned by tower-api for non-2xx responses. `message` is written
// to be read by a developer and should be surfaced to the user as-is.
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  message: string;
  path: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly path?: string;
  readonly timestamp?: string;

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.path = body.path;
    this.timestamp = body.timestamp;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: init?.body ? { "Content-Type": "application/json", ...init.headers } : init?.headers,
  });

  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {};
    try {
      body = (await response.json()) as Partial<ApiErrorBody>;
    } catch {
      // Response body was not JSON (or was empty); fall back to a generic message.
    }
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

// The documentation endpoints return Markdown as text rather than JSON, so
// they need their own fetch. Errors still arrive as the standard JSON error
// body, and are surfaced through ApiError exactly as elsewhere.
async function getText(path: string): Promise<string> {
  const response = await fetch(path);
  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {};
    try {
      body = (await response.json()) as Partial<ApiErrorBody>;
    } catch {
      // Not a JSON error body; ApiError falls back to a generic message.
    }
    throw new ApiError(response.status, body);
  }
  return response.text();
}

function getJson<T>(path: string): Promise<T> {
  return request<T>(path);
}

function postJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

function putJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: "PUT", body: JSON.stringify(body) });
}

function deleteRequest(path: string): Promise<void> {
  return request<void>(path, { method: "DELETE" });
}

// A handful of DELETE endpoints below remove one thing from inside a
// Release Pack (a content version, an Iteration, the pinned Promotion
// Path) rather than deleting a standalone resource, so — like the POST/PUT
// mutations on the same aggregate — they hand back the updated
// ReleasePackView instead of 204 No Content.
function deleteJson<T>(path: string): Promise<T> {
  return request<T>(path, { method: "DELETE" });
}

// --- Health ---------------------------------------------------------------

export function getHealth(): Promise<HealthStatus> {
  return getJson<HealthStatus>("/api/health");
}

// The whole operational dashboard in one read, derived on request and stored
// nowhere. One GET and nothing else — there is no control on this resource
// that promotes, deploys or triggers anything (ADR-001).
export function getDashboard(): Promise<DashboardView> {
  return getJson<DashboardView>("/api/dashboard");
}

// --- Environments -----------------------------------------------------

export function getEnvironments(): Promise<Environment[]> {
  return getJson<Environment[]>("/api/environments");
}

export function createEnvironment(input: EnvironmentInput): Promise<Environment> {
  return postJson<Environment>("/api/environments", input);
}

export function updateEnvironment(id: string, input: EnvironmentInput): Promise<Environment> {
  return putJson<Environment>(`/api/environments/${encodeURIComponent(id)}`, input);
}

export function deleteEnvironment(id: string): Promise<void> {
  return deleteRequest(`/api/environments/${encodeURIComponent(id)}`);
}

// --- Promotion Paths ----------------------------------------------------

export function getPromotionPaths(): Promise<PathView[]> {
  return getJson<PathView[]>("/api/promotion-paths");
}

export function getPromotionPath(id: string): Promise<PathView> {
  return getJson<PathView>(`/api/promotion-paths/${encodeURIComponent(id)}`);
}

export function createPromotionPath(input: CreatePathInput): Promise<PathView> {
  return postJson<PathView>("/api/promotion-paths", input);
}

export function addPromotionPathVersion(id: string, input: NewVersionInput): Promise<PathView> {
  return postJson<PathView>(`/api/promotion-paths/${encodeURIComponent(id)}/versions`, input);
}

export function renamePromotionPath(id: string, name: string): Promise<PathView> {
  return putJson<PathView>(`/api/promotion-paths/${encodeURIComponent(id)}/name`, { name });
}

export function archivePromotionPath(id: string): Promise<PathView> {
  return postJson<PathView>(`/api/promotion-paths/${encodeURIComponent(id)}/archive`);
}

export function restorePromotionPath(id: string): Promise<PathView> {
  return postJson<PathView>(`/api/promotion-paths/${encodeURIComponent(id)}/restore`);
}

export function deletePromotionPath(id: string): Promise<void> {
  return deleteRequest(`/api/promotion-paths/${encodeURIComponent(id)}`);
}

// --- Applications -----------------------------------------------------

export interface Application {
  id: string;
  name: string;
  description: string;
}

export interface ApplicationInput {
  name: string;
  description: string;
}

// --- Application Versions -----------------------------------------------

// Application Versions are immutable once registered (BR-01): the API
// surface intentionally offers create and delete only, never update.
export interface ApplicationVersion {
  id: string;
  applicationId: string;
  version: string;
  branch: string | null;
  tag: string | null;
  commit: string | null;
  buildIdentifier: string | null;
}

export interface ApplicationVersionInput {
  applicationId: string;
  version: string;
  branch?: string;
  tag?: string;
  commit?: string;
  buildIdentifier?: string;
}

// --- Observations -----------------------------------------------------

// Where an Observation came from (ADR-006). Milestone 1 has only the
// Manual Collector, so `manual` is always true today and `actor` carries
// the name of the person who entered it. `collector`/`originInstance` are
// shaped so an automated Connector can slot in later (Milestone 2)
// without changing this contract.
export interface ObservationSource {
  collector: string;
  actor: string | null;
  originInstance: string | null;
  manual: boolean;
}

export interface ObservationEnvironmentRef {
  id: string;
  name: string;
  stage: Stage;
}

export interface ObservationApplicationRef {
  id: string;
  name: string;
}

export interface ObservationApplicationVersionRef {
  id: string;
  version: string;
}

// An Observation is the atomic unit of information in Tower (ADR-002): an
// immutable, timestamped fact that a version was seen in an Environment,
// from an identified source. Observations are never edited or deleted —
// correcting a mistake means recording a later Observation, and the
// earlier one remains in history.
export interface ObservationView {
  id: string;
  environment: ObservationEnvironmentRef;
  application: ObservationApplicationRef;
  applicationVersion: ObservationApplicationVersionRef;
  observedAt: string;
  source: ObservationSource;
}

// observedAt is optional; the server defaults it to now. A future-dated
// observedAt is rejected by the server with 409.
export interface CreateObservationInput {
  environmentId: string;
  applicationVersionId: string;
  observedAt?: string;
}

export interface DeployedApplicationVersion {
  id: string;
  version: string;
  branch: string | null;
  tag: string | null;
  commit: string | null;
  buildIdentifier: string | null;
}

// One row of an Environment's current derived state: the most recent
// Observation for a given Application in that Environment.
export interface DeployedApplicationView {
  application: ObservationApplicationRef;
  applicationVersion: DeployedApplicationVersion;
  observedAt: string;
  source: ObservationSource;
  observationId: string;
}

// Environment state is derived, never stored — for each Application, the
// most recent Observation wins. `hasBeenObserved` is false when no
// Observation has ever been recorded for this Environment; that is a
// distinct fact from "observed and found empty" and must be rendered as
// such, never as an empty-and-therefore-fine table (Scenarios.md
// Scenario 4).
export interface EnvironmentStateView {
  environment: ObservationEnvironmentRef;
  hasBeenObserved: boolean;
  lastObservedAt: string | null;
  deployed: DeployedApplicationView[];
}

// --- Historical comparison (Milestone 4, ADR-017) -----------------------

// `kind` is named by the server rather than inferred from which version is
// null, because that inference is exactly where "no longer deployed" gets
// confused with "no longer observed". GONE means Tower has an Observation on
// one side and none on the other — not that anything was undeployed.
export type DifferenceKind = "CHANGED" | "ARRIVED" | "GONE";

export interface DifferenceView {
  applicationId: string;
  applicationName: string | null;
  kind: DifferenceKind;
  leftVersion: string | null;
  rightVersion: string | null;
  leftObservationId: string | null;
  rightObservationId: string | null;
  // Release Packs that CONTAIN the version this difference is about — the one
  // on the right, or the one on the left when there is no right. This is
  // containment, not causation: two packs may hold the same version and
  // neither deployed it. Label it accordingly in the UI.
  releasePacks: string[];
}

export interface UnchangedView {
  applicationId: string;
  applicationName: string | null;
  version: string | null;
  leftObservationId: string;
  rightObservationId: string;
}

// `identical` is stated rather than inferred from an empty differences list,
// so "the two agree" cannot be confused with "the comparison returned
// nothing".
export interface StateComparisonView {
  identical: boolean;
  differences: DifferenceView[];
  unchanged: UnchangedView[];
}

// --- Operational dashboard (Milestone 5) --------------------------------

// How much of a Release Pack has been observed in one Environment. Named for
// what Tower was told, never for what is deployed: NOT_OBSERVED_HERE means
// nobody has reported this release in this Environment, which is a different
// fact from the release being absent (Scenarios.md Scenario 4).
export type ConvergingStanding = "NOT_OBSERVED_HERE" | "PARTLY_OBSERVED" | "FULLY_OBSERVED";

export interface ConvergingPackView {
  releasePackId: string;
  name: string;
  standing: ConvergingStanding;
  firstObservedAt: string | null;
  completeAt: string | null;
  observedCount: number;
  packedCount: number;
}

// `converging` arrives ordered BY NAME and must be rendered in that order.
// Any reordering by progress — nearest to arriving, furthest along — would
// turn a list into a recommendation, and deciding which release proceeds is
// the business team's call, not Tower's (ADR-001, Guardrails.md).
export interface DashboardEnvironmentView {
  environmentId: string;
  name: string;
  stage: Stage;
  hasBeenObserved: boolean;
  lastObservedAt: string | null;
  deployedCount: number;
  contested: boolean;
  converging: ConvergingPackView[];
}

export interface DashboardReleasePackView {
  releasePackId: string;
  name: string;
  state: ReleasePackState;
  promotionPath: string | null;
  contentCount: number;
  environmentsReached: number;
  // Every Environment at the highest Stage this release was observed at.
  // A list, not one name: Environments share Stages (ADR-008), and naming
  // one of them would answer a question the data does not answer.
  furthestEnvironments: string[];
  lastObservedAt: string | null;
}

export interface DashboardSummaryView {
  activeReleasePacks: number;
  archivedReleasePacks: number;
  contestedEnvironments: number;
  packsNotObservedAnywhere: number;
  environmentsNeverObserved: number;
}

export interface DashboardView {
  environments: DashboardEnvironmentView[];
  releasePacks: DashboardReleasePackView[];
  summary: DashboardSummaryView;
}

// --- Release Pack state -------------------------------------------------

// Derived as the highest Stage at which any content version of the pack
// has been observed (ADR-008). PLANNED means no Observation has matched
// this pack yet. This is entirely independent of ReleasePackView.archived
// — never merge the two into a single indicator.
export type ReleasePackState = "PLANNED" | "DEVELOPMENT" | "VALIDATION" | "PRE_PRODUCTION" | "PRODUCTION";

// The server returns nested references here, consistent with ObservationView.
// This type previously declared flat environmentId/environmentName fields that
// the response never carried, so the Environment column rendered blank —
// TypeScript could not catch it because the response is cast, not validated.
export interface ReleasePackSighting {
  environment: ObservationEnvironmentRef;
  application: ObservationApplicationRef;
  applicationVersion: ObservationApplicationVersionRef;
  observedAt: string;
}

export interface ReleasePackStateView {
  state: ReleasePackState;
  sightings: ReleasePackSighting[];
}

// --- Release Pack progression (Milestone 4, ADR-017) --------------------

// A release arrives in an Environment piecemeal, so two instants are
// reported rather than one: when the first of its versions was seen there
// and when the last one was. `completeAt` is null while any version is
// still missing, and `missing` names those versions — a reader waiting on a
// release needs to know what they are waiting for, not just how many.
export interface MissingVersionView {
  applicationVersionId: string;
  applicationName: string | null;
  version: string | null;
}

export interface EnvironmentArrivalView {
  environmentId: string;
  environmentName: string | null;
  stage: Stage | null;
  firstObservedAt: string;
  completeAt: string | null;
  complete: boolean;
  observedCount: number;
  packedCount: number;
  missing: MissingVersionView[];
}

// Environments the release has never been observed in do not appear at all.
// Their absence is not evidence the release is absent from them — it may
// mean nobody looked (Scenarios.md Scenario 4) — so the Viewer must say so
// rather than render an empty row.
export interface ReleasePackProgressionView {
  observed: boolean;
  arrivals: EnvironmentArrivalView[];
}

// --- Release Packs --------------------------------------------------------

// A Release Pack pins a specific Promotion Path VERSION (ADR-007), not the
// path's current state — pathName/versionNumber/environments describe the
// topology as it stood when pinned, which may differ from the path today.
export interface ReleasePackPromotionPath {
  pathId: string;
  pathName: string;
  versionNumber: number;
  environments: Environment[];
}

export interface ReleasePackContent {
  applicationId: string;
  applicationName: string;
  versionId: string;
  version: string;
  branch: string | null;
  tag: string | null;
  commit: string | null;
  buildIdentifier: string | null;
}

// Handed to another team to carry out a deployment. Every field is free
// text; none are required up front (see ReleasePacksPage's "not prepared
// yet" state).
export interface ReleasePackHandover {
  deploymentInstructions: string;
  shellCommands: string;
  databaseMigrations: string;
  rollbackProcedure: string;
  validationNotes: string;
  operationalNotes: string;
}

// A work item the release claims to deliver (ADR-018). The title is the one a
// person accepted, not what the tracker says now — that comparison is a
// separate read, so a Release Pack loads with no tracker configured.
export interface ReleasePackWorkItem {
  identifier: string;
  title: string;
  titleAccepted: boolean;
}

// What the tracker says right now, beside what Tower holds.
//
// `state` is named by the server and must be rendered as four distinct things.
// NOT_FOUND means the tracker answered and does not have this identifier;
// UNRESOLVED means Tower never asked, because none is configured or it could
// not be reached. Collapsing those two would turn "nobody looked" into "it does
// not exist" — the same mistake as reporting an unobserved Environment as empty.
export type WorkItemState = "RESOLVED" | "DIVERGED" | "NOT_FOUND" | "UNRESOLVED";

export interface ResolvedWorkItem {
  identifier: string;
  acceptedTitle: string;
  trackerTitle: string | null;
  status: string | null;
  closed: boolean;
  url: string | null;
  state: WorkItemState;
  diverged: boolean;
}

export interface WorkItemResolution {
  connectorId: string | null;
  reachedTracker: boolean;
  failure: string | null;
  items: ResolvedWorkItem[];
}

export interface ReleasePackIteration {
  id: string;
  name: string;
  startedAt: string;
  completedAt: string | null;
  notes: string;
}

// Archived is an explicit lifecycle flag (ADR-008), not a deployment
// state — it records that the team stopped working the release. Where a
// pack was actually observed belongs to Epic 3 and is not modelled here.
export interface ReleasePackView {
  id: string;
  name: string;
  description: string;
  archived: boolean;
  promotionPath: ReleasePackPromotionPath | null;
  contents: ReleasePackContent[];
  workItems: ReleasePackWorkItem[];
  handover: ReleasePackHandover;
  iterations: ReleasePackIteration[];
}

export interface ReleasePackInput {
  name: string;
  description: string;
}

export interface SetReleasePackPromotionPathInput {
  pathId: string;
  versionNumber: number;
}

export interface ReleasePackHandoverInput {
  deploymentInstructions: string;
  shellCommands: string;
  databaseMigrations: string;
  rollbackProcedure: string;
  validationNotes: string;
  operationalNotes: string;
}

export interface ReleasePackIterationInput {
  name: string;
  startedAt: string;
  notes: string;
}

// --- Applications -----------------------------------------------------

export function getApplications(): Promise<Application[]> {
  return getJson<Application[]>("/api/applications");
}

export function getApplication(id: string): Promise<Application> {
  return getJson<Application>(`/api/applications/${encodeURIComponent(id)}`);
}

export function createApplication(input: ApplicationInput): Promise<Application> {
  return postJson<Application>("/api/applications", input);
}

export function updateApplication(id: string, input: ApplicationInput): Promise<Application> {
  return putJson<Application>(`/api/applications/${encodeURIComponent(id)}`, input);
}

export function deleteApplication(id: string): Promise<void> {
  return deleteRequest(`/api/applications/${encodeURIComponent(id)}`);
}

export function getApplicationVersions(applicationId: string): Promise<ApplicationVersion[]> {
  return getJson<ApplicationVersion[]>(`/api/applications/${encodeURIComponent(applicationId)}/versions`);
}

// --- Application Versions -----------------------------------------------

export function getAllApplicationVersions(): Promise<ApplicationVersion[]> {
  return getJson<ApplicationVersion[]>("/api/application-versions");
}

export function getApplicationVersion(id: string): Promise<ApplicationVersion> {
  return getJson<ApplicationVersion>(`/api/application-versions/${encodeURIComponent(id)}`);
}

export function createApplicationVersion(input: ApplicationVersionInput): Promise<ApplicationVersion> {
  return postJson<ApplicationVersion>("/api/application-versions", input);
}

export function deleteApplicationVersion(id: string): Promise<void> {
  return deleteRequest(`/api/application-versions/${encodeURIComponent(id)}`);
}

// --- Observations -----------------------------------------------------

// Records that a version was already seen running in an Environment
// (ADR-001 — Tower observes, it never acts: this states a fact, it does
// not deploy or promote anything).
export function createObservation(input: CreateObservationInput): Promise<ObservationView> {
  return postJson<ObservationView>("/api/observations", input);
}

// Current state, or the state as it stood at `at` (Milestone 4, ADR-017).
// A past state is derived from the same Observations on request, never read
// from a stored snapshot, so any instant is answerable — not only the ones
// somebody thought to capture.
export function getEnvironmentState(environmentId: string, at?: string): Promise<EnvironmentStateView> {
  const query = at ? `?at=${encodeURIComponent(at)}` : "";
  return getJson<EnvironmentStateView>(`/api/environments/${encodeURIComponent(environmentId)}/state${query}`);
}

// Compares two derived states. Both sides are an Environment and an instant,
// either of which may be omitted, so this answers "how did UAT change since
// Monday" and "how does UAT differ from Production" with one call.
export function getEnvironmentStateComparison(
  environmentId: string,
  options: { at?: string; against?: string; againstAt?: string } = {},
): Promise<StateComparisonView> {
  const query = new URLSearchParams();
  if (options.at) query.set("at", options.at);
  if (options.against) query.set("against", options.against);
  if (options.againstAt) query.set("againstAt", options.againstAt);
  const suffix = query.toString() === "" ? "" : `?${query.toString()}`;
  return getJson<StateComparisonView>(
    `/api/environments/${encodeURIComponent(environmentId)}/state/comparison${suffix}`,
  );
}

// Full Observation history for an Environment, newest first. Superseded
// Observations remain in this list — history is append-only (ADR-002).
export function getEnvironmentObservations(environmentId: string): Promise<ObservationView[]> {
  return getJson<ObservationView[]>(`/api/environments/${encodeURIComponent(environmentId)}/observations`);
}

// --- Release Packs --------------------------------------------------------

export function getReleasePacks(): Promise<ReleasePackView[]> {
  return getJson<ReleasePackView[]>("/api/release-packs");
}

export function getReleasePack(id: string): Promise<ReleasePackView> {
  return getJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}`);
}

export function createReleasePack(input: ReleasePackInput): Promise<ReleasePackView> {
  return postJson<ReleasePackView>("/api/release-packs", input);
}

export function updateReleasePack(id: string, input: ReleasePackInput): Promise<ReleasePackView> {
  return putJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}`, input);
}

export function deleteReleasePack(id: string): Promise<void> {
  return deleteRequest(`/api/release-packs/${encodeURIComponent(id)}`);
}

export function setReleasePackPromotionPath(
  id: string,
  input: SetReleasePackPromotionPathInput,
): Promise<ReleasePackView> {
  return putJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}/promotion-path`, input);
}

export function clearReleasePackPromotionPath(id: string): Promise<ReleasePackView> {
  return deleteJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}/promotion-path`);
}

export function addReleasePackVersion(id: string, versionId: string): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}`,
  );
}

export function removeReleasePackVersion(id: string, versionId: string): Promise<ReleasePackView> {
  return deleteJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}`,
  );
}

// Every version the Handover has had, newest first (ADR-016). Read-only, and
// there is deliberately no "restore" call: putting an old Handover back is an
// edit like any other, made through the ordinary update, and it appends a new
// revision rather than rewriting history.
export interface HandoverRevision extends ReleasePackHandoverInput {
  id: string;
  revisionNumber: number;
  recordedAt: string;
  current: boolean;
  empty: boolean;
}

export function getHandoverHistory(id: string): Promise<HandoverRevision[]> {
  return getJson<HandoverRevision[]>(`/api/release-packs/${encodeURIComponent(id)}/handover/history`);
}

export function updateReleasePackHandover(id: string, input: ReleasePackHandoverInput): Promise<ReleasePackView> {
  return putJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}/handover`, input);
}

export function addReleasePackIteration(id: string, input: ReleasePackIterationInput): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}/iterations`, input);
}

export function completeReleasePackIteration(
  id: string,
  iterationId: string,
  completedAt: string,
): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/iterations/${encodeURIComponent(iterationId)}/complete`,
    { completedAt },
  );
}

export function reopenReleasePackIteration(id: string, iterationId: string): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/iterations/${encodeURIComponent(iterationId)}/reopen`,
  );
}

export function updateReleasePackIterationNotes(
  id: string,
  iterationId: string,
  notes: string,
): Promise<ReleasePackView> {
  return putJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/iterations/${encodeURIComponent(iterationId)}/notes`,
    { notes },
  );
}

export function deleteReleasePackIteration(id: string, iterationId: string): Promise<ReleasePackView> {
  return deleteJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/iterations/${encodeURIComponent(iterationId)}`,
  );
}

export function archiveReleasePack(id: string): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}/archive`);
}

export function restoreReleasePack(id: string): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(`/api/release-packs/${encodeURIComponent(id)}/restore`);
}

// Links a work item. Tower checks nothing against a tracker, so a release can
// name what it delivers before any Connector is configured.
export function linkWorkItem(id: string, identifier: string, title: string): Promise<ReleasePackView> {
  return postJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/work-items`, { identifier, title });
}

// Takes the tracker's current title as Tower's own. Always an explicit act:
// nothing refreshes a title on its own, which is what keeps a generated
// document reproducible (NFR-025).
export function acceptWorkItemTitle(id: string, identifier: string, title: string): Promise<ReleasePackView> {
  return putJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/work-items/title`, { identifier, title });
}

export function unlinkWorkItem(id: string, identifier: string): Promise<ReleasePackView> {
  return deleteJson<ReleasePackView>(
    `/api/release-packs/${encodeURIComponent(id)}/work-items?identifier=${encodeURIComponent(identifier)}`);
}

// Reads the tracker. Answers 200 even when the tracker could not be reached —
// the failure is stated in the body, and the release's own work items are still
// listed, marked unresolved.
export function resolveWorkItems(id: string): Promise<WorkItemResolution> {
  return getJson<WorkItemResolution>(`/api/release-packs/${encodeURIComponent(id)}/work-items/resolved`);
}

export function getReleasePackState(id: string): Promise<ReleasePackStateView> {
  return getJson<ReleasePackStateView>(`/api/release-packs/${encodeURIComponent(id)}/state`);
}

// When this release reached each Environment. Separate from state because it
// answers a different question: state says how far it got, progression says
// when it got there and what is still outstanding.
export function getReleasePackProgression(id: string): Promise<ReleasePackProgressionView> {
  return getJson<ReleasePackProgressionView>(`/api/release-packs/${encodeURIComponent(id)}/progression`);
}

// --- Release documentation (Epic 4) -----------------------------------------

// Generated from the Canonical Model on every request. Nothing is stored, and
// the same model always yields the same bytes, so a document is disposable and
// reproducible (IA-03) rather than an artifact to keep in step with reality.
export function getReleaseDocumentMarkdown(releasePackId: string, templateId?: string): Promise<string> {
  return getText(
    `/api/release-packs/${encodeURIComponent(releasePackId)}/documentation/markdown${templateQuery(templateId)}`,
  );
}

// A plain link, not a fetch: letting the browser follow it preserves the
// Content-Disposition filename the server derives from the pack name.
// HTML is served inline so the browser renders it, and separately as a
// download (Milestone 3, OQ-009). Both are plain links rather than fetches:
// the point of the HTML format is that a browser opens it.
export function releaseDocumentHtmlUrl(releasePackId: string, templateId?: string): string {
  return `/api/release-packs/${encodeURIComponent(releasePackId)}/documentation/html${templateQuery(templateId)}`;
}

export function releaseDocumentHtmlDownloadUrl(releasePackId: string, templateId?: string): string {
  return `/api/release-packs/${encodeURIComponent(releasePackId)}/documentation/html/download${templateQuery(templateId)}`;
}

// DOCX has no inline counterpart: a browser cannot render one, so there is a
// download and nothing else. It is the format that imports into Confluence as an
// editable page, and converts to PDF from there (ADR-015).
export function releaseDocumentDocxDownloadUrl(releasePackId: string, templateId?: string): string {
  return `/api/release-packs/${encodeURIComponent(releasePackId)}/documentation/docx/download${templateQuery(templateId)}`;
}

export function releaseDocumentDownloadUrl(releasePackId: string, templateId?: string): string {
  return `/api/release-packs/${encodeURIComponent(releasePackId)}/documentation/markdown/download${templateQuery(templateId)}`;
}

// --- Document Templates (OQ-010, ADR-013) -----------------------------------

// A template chooses which sections a release document contains and in what
// order. It holds no markup: rendering stays in Tower's own code, which is what
// keeps a regenerated document byte-identical (NFR-025).
export interface DocumentTemplate {
  id: string;
  name: string;
  sections: string[];
  omitted: string[];
  builtIn: boolean;
}

export interface DocumentSectionInfo {
  name: string;
  heading: string;
  description: string;
}

export interface DocumentTemplateInput {
  name: string;
  sections: string[];
}

// Omitted entirely rather than sent empty, so the request a caller makes with
// no template chosen is the same request Tower answered before templates
// existed.
function templateQuery(templateId?: string): string {
  return templateId ? `?template=${encodeURIComponent(templateId)}` : "";
}

export function listDocumentTemplates(): Promise<DocumentTemplate[]> {
  return getJson<DocumentTemplate[]>("/api/document-templates");
}

// Served rather than hardcoded here, so adding a section to a release document
// does not need the Viewer changed to know about it.
export function listDocumentSections(): Promise<DocumentSectionInfo[]> {
  return getJson<DocumentSectionInfo[]>("/api/document-templates/sections");
}

export function createDocumentTemplate(input: DocumentTemplateInput): Promise<DocumentTemplate> {
  return postJson<DocumentTemplate>("/api/document-templates", input);
}

export function updateDocumentTemplate(id: string, input: DocumentTemplateInput): Promise<DocumentTemplate> {
  return putJson<DocumentTemplate>(`/api/document-templates/${encodeURIComponent(id)}`, input);
}

export function deleteDocumentTemplate(id: string): Promise<void> {
  return deleteRequest(`/api/document-templates/${encodeURIComponent(id)}`);
}

// --- Portability (Epic 8, ADR-010) ------------------------------------------

export type ConflictStrategy = "SKIP" | "REPLACE" | "DUPLICATE";

export type ImportOutcome = "CREATED" | "REPLACED" | "SKIPPED" | "DUPLICATED" | "UNCHANGED";

export interface ImportReportEntry {
  type: string;
  id: string;
  name: string;
  outcome: ImportOutcome;
  detail: string | null;
}

export interface ImportReport {
  applied: boolean;
  sourceInstance: string;
  schemaVersion: number;
  entries: ImportReportEntry[];
}

// Only the fields the Viewer displays are typed. The rest of the file is passed
// back to the server untouched, so the client never becomes a second place that
// has to understand the export format.
export interface TowerExportFile {
  schemaVersion: number;
  exportedFrom: string;
  exportedAt: string;
  environments: unknown[];
  applications: unknown[];
  applicationVersions: unknown[];
  promotionPaths: unknown[];
  releasePacks: unknown[];
  observations: unknown[];
}

export interface TowerInstanceView {
  name: string;
}

export function getTowerInstance(): Promise<TowerInstanceView> {
  return getJson<TowerInstanceView>("/api/portability/instance");
}

// A plain link so the browser keeps the filename the server derives from the
// instance name.
export function towerExportDownloadUrl(includeObservations: boolean): string {
  return `/api/portability/export/download?includeObservations=${includeObservations}`;
}

export function previewImport(file: TowerExportFile, strategy: ConflictStrategy): Promise<ImportReport> {
  return postJson<ImportReport>(`/api/portability/import/preview?strategy=${strategy}`, file);
}

export function applyImport(file: TowerExportFile, strategy: ConflictStrategy): Promise<ImportReport> {
  return postJson<ImportReport>(`/api/portability/import?strategy=${strategy}`, file);
}

// --- Connectors: bindings, credentials, synchronization ---------------------
// Milestone 2 (issues #46 to #53). ADR-012 keeps the correspondence between
// Tower's concepts and a vendor's locators out of the Domain Model, so these
// are their own resources rather than fields on Environment or Application.

export interface EnvironmentBinding {
  environmentId: string;
  connectorId: string;
  target: string;
  scope: string;
}

export interface ApplicationBinding {
  applicationId: string;
  connectorId: string;
  image: string;
  versionPattern: string;
}

export interface VersionPreview {
  imageTag: string;
  versionPattern: string;
  matched: boolean;
  version: string | null;
}

// No field here can carry the secret. Credentials are write-only through the
// API (Implementation-Plan.md, Credential Handling), so the Viewer can render
// "configured" without any code path that returns a token.
export interface CredentialStatus {
  connectorId: string;
  target: string;
  configured: boolean;
  updatedAt: string | null;
}

export interface UnrecognizedWorkload {
  scope: string;
  name: string;
  imageReference: string;
  reason: string;
}

export interface SyncRun {
  id: string;
  connectorId: string;
  startedAt: string;
  finishedAt: string;
  outcome: "SUCCEEDED" | "PARTIALLY_SUCCEEDED" | "FAILED";
  workloadsRead: number;
  observationsAppended: number;
  foundNoChange: boolean;
  // Only a wholly successful run licenses "confirmed present as of" (ADR-011).
  confirmsLiveness: boolean;
  unrecognized: UnrecognizedWorkload[];
  failures: string[];
}

export interface ConnectionTest {
  connectorId: string;
  target: string;
  scope: string;
  reachable: boolean;
  message: string;
}

export function listEnvironmentBindings(): Promise<EnvironmentBinding[]> {
  return getJson<EnvironmentBinding[]>("/api/bindings/environments");
}

export function bindEnvironment(binding: EnvironmentBinding): Promise<EnvironmentBinding> {
  return putJson<EnvironmentBinding>("/api/bindings/environments", binding);
}

export function unbindEnvironment(environmentId: string, connectorId: string): Promise<void> {
  return deleteRequest(
    `/api/bindings/environments/${encodeURIComponent(environmentId)}?connectorId=${encodeURIComponent(connectorId)}`,
  );
}

export function listApplicationBindings(): Promise<ApplicationBinding[]> {
  return getJson<ApplicationBinding[]>("/api/bindings/applications");
}

export function bindApplication(binding: ApplicationBinding): Promise<ApplicationBinding> {
  return putJson<ApplicationBinding>("/api/bindings/applications", binding);
}

export function unbindApplication(applicationId: string, connectorId: string): Promise<void> {
  return deleteRequest(
    `/api/bindings/applications/${encodeURIComponent(applicationId)}?connectorId=${encodeURIComponent(connectorId)}`,
  );
}

export function previewVersion(versionPattern: string, imageTag: string): Promise<VersionPreview> {
  const pattern = versionPattern ? `versionPattern=${encodeURIComponent(versionPattern)}&` : "";
  return getJson<VersionPreview>(`/api/bindings/version-preview?${pattern}imageTag=${encodeURIComponent(imageTag)}`);
}

// --- Source control (issue #3, ADR-014) -------------------------------------

// Where an Application's code lives. Separate from the deployment binding
// above: one says which running image is this Application, the other says where
// its source is, and a team may configure either without the other.
export type RefSelection = "TAGS" | "BRANCHES" | "ALL";

export interface RepositoryBinding {
  applicationId: string;
  connectorId: string;
  repositoryUrl: string;
  refSelection: RefSelection;
  versionPattern: string;
}

// A candidate, not a version. BR-01 makes an Application Version immutable, so
// discovery never creates one — `alreadyRegistered` is reported rather than
// filtered out, because on the second look most candidates are already held and
// hiding them would read as Tower having lost them.
export interface DiscoveredVersion {
  version: string;
  // Which system proposed it — "git" for a ref, a CI connector for a build run
  // (ADR-020). The two are the same kind of proposal and share one list, but a
  // reader deciding whether to accept one needs to know which system said so.
  source: string;
  // Where within that source, in that source's own words: a ref name, or a job
  // and run.
  origin: string;
  branch: string | null;
  tag: string | null;
  // Null where the source does not record one. A ref always resolves to a
  // commit; a build run carries a build identifier instead, and deriving either
  // from the other would be a guess.
  commit: string | null;
  buildIdentifier: string | null;
  alreadyRegistered: boolean;
}

// `failure` is carried beside the candidates rather than folded into an empty
// list, because "could not look" and "looked and found nothing" lead to
// opposite next steps.
export interface VersionDiscovery {
  applicationId: string;
  repositoryUrl: string | null;
  candidates: DiscoveredVersion[];
  unmatched: string[];
  failure: string | null;
}

export function listRepositoryBindings(): Promise<RepositoryBinding[]> {
  return getJson<RepositoryBinding[]>("/api/bindings/repositories");
}

export function bindRepository(binding: RepositoryBinding): Promise<RepositoryBinding> {
  return putJson<RepositoryBinding>("/api/bindings/repositories", binding);
}

export function unbindRepository(applicationId: string, connectorId: string): Promise<void> {
  return deleteRequest(
    `/api/bindings/repositories/${encodeURIComponent(applicationId)}?connectorId=${encodeURIComponent(connectorId)}`,
  );
}

export function discoverSourceVersions(applicationId: string): Promise<VersionDiscovery> {
  return getJson<VersionDiscovery>(
    `/api/applications/${encodeURIComponent(applicationId)}/source-versions`,
  );
}

export function testRepositoryConnection(repositoryUrl: string): Promise<ConnectionTest> {
  return getJson<ConnectionTest>(
    `/api/source-versions/connection-test?repositoryUrl=${encodeURIComponent(repositoryUrl)}`,
  );
}

// Where the team's work items live (ADR-018). Keyed by Connector alone: a work
// item belongs to a release rather than to one Application, and a team has one
// tracker, so there is no Tower id on the left of this binding.
export interface IssueTrackerBinding {
  connectorId: string;
  locator: string;
}

// What the connection test answers for a tracker. Not a ConnectionTest: a
// tracker has no scope to report, and a record with a permanently empty field
// invites a screen to render one.
export interface IssueTrackerConnectionReport {
  connectorId: string;
  locator: string | null;
  reachable: boolean;
  message: string;
}

export function listIssueTrackerBindings(): Promise<IssueTrackerBinding[]> {
  return getJson<IssueTrackerBinding[]>("/api/bindings/issue-trackers");
}

export function bindIssueTracker(binding: IssueTrackerBinding): Promise<IssueTrackerBinding> {
  return putJson<IssueTrackerBinding>("/api/bindings/issue-trackers", binding);
}

export function unbindIssueTracker(connectorId: string): Promise<void> {
  return deleteRequest(`/api/bindings/issue-trackers/${encodeURIComponent(connectorId)}`);
}

export function testIssueTrackerConnection(connectorId: string): Promise<IssueTrackerConnectionReport> {
  return getJson<IssueTrackerConnectionReport>(
    `/api/work-items/connection-test?connectorId=${encodeURIComponent(connectorId)}`,
  );
}

// --- CI/CD (ADR-020) --------------------------------------------------------

// Which job's runs mean an Application reached an Environment.
//
// The widest binding in Tower and the only one naming two Tower concepts,
// because that is what a run of a deployment job actually asserts.
//
// versionSource says where in a run the version lives. A CI system used as most
// of them actually are does not record it anywhere a Connector could guess, and
// ADR-020 refuses to read the console log to find it — a wrong parse there would
// produce Observations that are immutable and therefore permanent. So the place
// is configuration.
export type VersionSource = "PARAMETER" | "RUN_NAME" | "JOB_PATH";

// Which job's runs build an Application (ADR-020).
//
// No Environment, and that absence is the design: a build says what was
// produced, not where it went, so a run of this yields a candidate version
// rather than an Observation. The job is part of the key — two build jobs for
// one Application are ordinary.
export interface BuildJobBinding {
  applicationId: string;
  connectorId: string;
  system: string;
  job: string;
  versionSource: VersionSource;
  versionKey: string;
  versionPattern: string;
}

export interface PipelineJobBinding {
  environmentId: string;
  applicationId: string;
  connectorId: string;
  system: string;
  job: string;
  versionSource: VersionSource;
  versionKey: string;
  versionPattern: string;
}

// A run Tower read and deliberately did not record (FR-078, FR-080).
//
// Two different things arrive here and both are reported rather than dropped: a
// run that did not succeed is not evidence anything was deployed, and a run
// whose version could not be found or recognised cannot be attributed without
// guessing.
export interface NotRecordedRun {
  job: string;
  runId: string;
  outcome: string;
  reason: string;
}

// What a pipeline read produced.
//
// Carries confirmsNothingWasDeployed rather than a confirmsLiveness, and the
// difference is the whole of ADR-020. A Deployment Platform reads what is
// running, so a clean run licenses "still present as of". A CI system reads what
// happened, so a clean read licenses only "nothing was deployed by these jobs
// since Tower last looked" — narrower, and about a different subject. A screen
// given the first would say something Tower does not know.
export interface PipelineSyncReport {
  id: string;
  connectorId: string;
  startedAt: string;
  finishedAt: string;
  jobsRead: number;
  runsRead: number;
  observationsAppended: number;
  readEverything: boolean;
  confirmsNothingWasDeployed: boolean;
  notRecorded: NotRecordedRun[];
  failures: string[];
}

export function listPipelineJobBindings(): Promise<PipelineJobBinding[]> {
  return getJson<PipelineJobBinding[]>("/api/bindings/pipeline-jobs");
}

export function bindPipelineJob(binding: PipelineJobBinding): Promise<PipelineJobBinding> {
  return putJson<PipelineJobBinding>("/api/bindings/pipeline-jobs", binding);
}

// Two Tower ids in the path, because both are part of the key: one job per
// Application per Environment per Connector.
export function unbindPipelineJob(
  environmentId: string,
  applicationId: string,
  connectorId: string,
): Promise<void> {
  return deleteRequest(
    `/api/bindings/pipeline-jobs/${encodeURIComponent(environmentId)}/${encodeURIComponent(applicationId)}` +
      `?connectorId=${encodeURIComponent(connectorId)}`,
  );
}

export function listBuildJobBindings(): Promise<BuildJobBinding[]> {
  return getJson<BuildJobBinding[]>("/api/bindings/build-jobs");
}

export function bindBuildJob(binding: BuildJobBinding): Promise<BuildJobBinding> {
  return putJson<BuildJobBinding>("/api/bindings/build-jobs", binding);
}

// The job is in the query rather than the path, unlike the pipeline job unbind:
// a job path contains slashes, and putting one in a path segment would need
// escaping that the server would then have to undo.
export function unbindBuildJob(
  applicationId: string,
  connectorId: string,
  job: string,
): Promise<void> {
  return deleteRequest(
    `/api/bindings/build-jobs/${encodeURIComponent(applicationId)}` +
      `?connectorId=${encodeURIComponent(connectorId)}&job=${encodeURIComponent(job)}`,
  );
}

export function synchronizePipelinesNow(): Promise<PipelineSyncReport[]> {
  return postJson<PipelineSyncReport[]>("/api/pipeline-sync", undefined);
}

export function listPipelineSyncReports(limit: number): Promise<PipelineSyncReport[]> {
  return getJson<PipelineSyncReport[]>(`/api/pipeline-sync/reports?limit=${limit}`);
}

// Takes a job as well as a system: a credential that reaches the server may
// still not see the job, and a test that only asked about the server would pass
// while every read failed.
export function testPipelineConnection(
  connectorId: string,
  system: string,
  job: string,
): Promise<ConnectionTest> {
  return getJson<ConnectionTest>(
    `/api/pipeline-sync/connection-test?connectorId=${encodeURIComponent(connectorId)}` +
      `&system=${encodeURIComponent(system)}&job=${encodeURIComponent(job)}`,
  );
}

// --- Artifacts (ADR-021) ----------------------------------------------------

// How to address one kind of artifact an Application Version produced.
//
// The template *composes* where every other binding's versionPattern
// *extracts*: a pattern turns a vendor's string into a version, this turns a
// version into a vendor's string. `{version}`, `{commit}` and `{shortCommit}`
// stand in for what Tower already holds.
//
// `kind` is the team's own word — "image", "chart", "installer" — and Tower
// never interprets it.
export interface ArtifactCoordinateBinding {
  applicationId: string;
  connectorId: string;
  kind: string;
  system: string;
  coordinateTemplate: string;
  shortCommitLength: number;
}

// What a template would make of a version, without saving anything. The
// counterpart of VersionPreview, and useful for a milder reason: a wrong
// template only produces a false "not found", so this is how false absence is
// told from real absence before somebody goes looking in the repository.
export interface CoordinatePreview {
  coordinateTemplate: string;
  composed: string | null;
  missing: string[];
}

// What the repository says right now, beside what Tower holds.
//
// Five distinct things, and none may be collapsed. ABSENT means the repository
// was read and has nothing there; UNREAD means Tower could not ask, so nobody
// knows. NOT_ADDRESSABLE means the template wants a commit this version does
// not carry, which is a configuration answer rather than a fact about the
// repository. DIVERGED means the bytes under the accepted name changed — a tag
// was pushed over — and is the single most useful thing this screen reports.
export type ArtifactState = "PRESENT" | "DIVERGED" | "ABSENT" | "NOT_ADDRESSABLE" | "UNREAD";

export interface ConfirmedArtifact {
  kind: string;
  connectorId: string;
  system: string;
  coordinate: string | null;
  digest: string | null;
  acceptedDigest: string | null;
  storedAt: string | null;
  sizeBytes: number;
  url: string | null;
  state: ArtifactState;
  detail: string | null;
  diverged: boolean;
}

export interface ArtifactConfirmation {
  applicationVersionId: string;
  version: string;
  commit: string | null;
  artifacts: ConfirmedArtifact[];
}

export interface AcceptedArtifactDigest {
  applicationVersionId: string;
  kind: string;
  coordinate: string;
  digest: string;
  acceptedAt: string;
}

export function listArtifactCoordinateBindings(): Promise<ArtifactCoordinateBinding[]> {
  return getJson<ArtifactCoordinateBinding[]>("/api/bindings/artifact-coordinates");
}

export function bindArtifactCoordinate(
  binding: ArtifactCoordinateBinding,
): Promise<ArtifactCoordinateBinding> {
  return putJson<ArtifactCoordinateBinding>("/api/bindings/artifact-coordinates", binding);
}

// The kind is in the path rather than the query, unlike every other unbind
// here: it is part of the key, because an Application may have several
// templates for one Connector.
export function unbindArtifactCoordinate(
  applicationId: string,
  kind: string,
  connectorId: string,
): Promise<void> {
  return deleteRequest(
    `/api/bindings/artifact-coordinates/${encodeURIComponent(applicationId)}/${encodeURIComponent(kind)}` +
      `?connectorId=${encodeURIComponent(connectorId)}`,
  );
}

export function previewCoordinate(
  coordinateTemplate: string,
  shortCommitLength: number,
  version: string,
  commit: string,
): Promise<CoordinatePreview> {
  const commitQuery = commit ? `&commit=${encodeURIComponent(commit)}` : "";
  return getJson<CoordinatePreview>(
    `/api/bindings/coordinate-preview?coordinateTemplate=${encodeURIComponent(coordinateTemplate)}` +
      `&shortCommitLength=${shortCommitLength}&version=${encodeURIComponent(version)}${commitQuery}`,
  );
}

// Reads the repositories. Answers 200 even when one could not be reached — the
// reason is on the artifact, and every bound template is still listed, marked
// unread. A version does not become less true because a repository is down.
export function confirmArtifacts(applicationVersionId: string): Promise<ArtifactConfirmation> {
  return getJson<ArtifactConfirmation>(
    `/api/application-versions/${encodeURIComponent(applicationVersionId)}/artifacts`,
  );
}

// Takes a digest the person is looking at as Tower's own. Always an explicit
// act, and always the digest they saw rather than whatever the repository says
// at this instant — between looking and pressing the button a tag can be pushed
// over, which is the exact situation this exists to make visible (FR-086).
export function acceptArtifactDigest(
  applicationVersionId: string,
  kind: string,
  coordinate: string,
  digest: string,
): Promise<AcceptedArtifactDigest> {
  return putJson<AcceptedArtifactDigest>(
    `/api/application-versions/${encodeURIComponent(applicationVersionId)}` +
      `/artifacts/${encodeURIComponent(kind)}/accepted-digest`,
    { coordinate, digest },
  );
}

export function withdrawArtifactDigest(applicationVersionId: string, kind: string): Promise<void> {
  return deleteRequest(
    `/api/application-versions/${encodeURIComponent(applicationVersionId)}` +
      `/artifacts/${encodeURIComponent(kind)}/accepted-digest`,
  );
}

export function testArtifactRepositoryConnection(
  connectorId: string,
  system: string,
): Promise<ConnectionTest> {
  return getJson<ConnectionTest>(
    `/api/artifacts/connection-test?connectorId=${encodeURIComponent(connectorId)}` +
      `&system=${encodeURIComponent(system)}`,
  );
}

export function getCredentialStatus(connectorId: string, target: string): Promise<CredentialStatus> {
  return getJson<CredentialStatus>(
    `/api/credentials?connectorId=${encodeURIComponent(connectorId)}&target=${encodeURIComponent(target)}`,
  );
}

// request() already maps 204 to undefined, so putJson<void> is the right shape
// for an endpoint that deliberately returns nothing.
export function storeCredential(connectorId: string, target: string, secret: string): Promise<void> {
  return putJson<void>("/api/credentials", { connectorId, target, secret });
}

export function forgetCredential(connectorId: string, target: string): Promise<void> {
  return deleteRequest(
    `/api/credentials?connectorId=${encodeURIComponent(connectorId)}&target=${encodeURIComponent(target)}`,
  );
}

export function synchronizeNow(): Promise<SyncRun[]> {
  return postJson<SyncRun[]>("/api/sync", undefined);
}

export function listSyncRuns(limit: number): Promise<SyncRun[]> {
  return getJson<SyncRun[]>(`/api/sync/runs?limit=${limit}`);
}

export function testConnection(connectorId: string, target: string, scope: string): Promise<ConnectionTest> {
  return getJson<ConnectionTest>(
    `/api/sync/connection-test?connectorId=${encodeURIComponent(connectorId)}` +
      `&target=${encodeURIComponent(target)}&scope=${encodeURIComponent(scope)}`,
  );
}
