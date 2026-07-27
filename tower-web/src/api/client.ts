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
