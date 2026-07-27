// Typed client for tower-api. The Viewer communicates exclusively with
// tower-api (see docs/planning/Implementation-Plan.md, "tower-web").
//
// In development, Vite proxies "/api" to the backend at 127.0.0.1:8080
// (see vite.config.ts). In production the built assets are served by
// tower-api itself, so "/api" resolves against the same origin.

export interface HealthStatus {
  status: string;
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`Request to ${path} failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

export function getHealth(): Promise<HealthStatus> {
  return getJson<HealthStatus>("/api/health");
}
