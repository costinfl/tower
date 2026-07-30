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

export function getEnvironmentState(environmentId: string): Promise<EnvironmentStateView> {
  return getJson<EnvironmentStateView>(`/api/environments/${encodeURIComponent(environmentId)}/state`);
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

export function getReleasePackState(id: string): Promise<ReleasePackStateView> {
  return getJson<ReleasePackStateView>(`/api/release-packs/${encodeURIComponent(id)}/state`);
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
  refName: string;
  branch: string | null;
  tag: string | null;
  commit: string;
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
