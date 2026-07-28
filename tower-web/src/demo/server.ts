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
interface PackRec {
  id: string; name: string; description: string; archived: boolean;
  promotionPathId: string | null; promotionPathVersion: number | null;
  contents: { applicationId: string; applicationVersionId: string }[];
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

const state: {
  environments: Env[]; applications: App[]; versions: Ver[];
  paths: PathRec[]; packs: PackRec[]; observations: Obs[];
} = {
  environments: seed.environments.map((e) => ({ ...e })),
  applications: seed.applications.map((a) => ({ ...a })),
  versions: seed.applicationVersions.map((v) => ({ ...v })),
  paths: seed.promotionPaths.map((p) => ({ ...p, versions: p.versions.map((v) => ({ ...v })) })),
  packs: seed.releasePacks.map((p) => ({
    ...p,
    contents: p.contents.map((c) => ({ ...c })),
    handover: { ...p.handover },
    iterations: p.iterations.map((i) => ({ ...i })),
  })),
  observations: seed.observations.map((o) => ({ ...o, source: { ...o.source } })),
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
function environmentStateView(environmentId: string) {
  const e = env(environmentId);
  if (!e) throw notFound(`Environment ${environmentId} does not exist.`);

  const latest = new Map<string, Obs>();
  for (const o of state.observations.filter((x) => x.environmentId === environmentId)) {
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
// Mirrors MarkdownReleaseDocumentRenderer. Deterministic for the same reason:
// no generation timestamp, stable ordering, absences stated rather than
// omitted. `scripts/verify-demo-docgen.sh` checks this against the real
// renderer, so drift is caught rather than discovered.

const dash = (v: string | null | undefined) => (v == null || v === "" ? "—" : v);
const oneLine = (v: string) =>
  !v || !v.trim() ? "—" : v.replace(/\|/g, "\\|").replace(/\s*\r?\n\s*/g, " ").trim();

function releaseMarkdown(packId: string): string {
  const p = pack(packId);
  if (!p) throw notFound(`Release Pack ${packId} does not exist.`);
  const view = packView(p);
  const derived = packStateView(packId);
  const out: string[] = [];

  out.push(`# Release Pack: ${p.name}\n`);
  if (p.description.trim()) out.push(`${p.description}\n`);
  out.push("| | |\n|---|---|");
  out.push(`| Observed state | ${derived.state} |`);
  out.push(`| Lifecycle | ${p.archived ? "Archived" : "Active"} |\n`);
  out.push("> Observed state is derived from Observations and records where this release has been");
  out.push("> seen. Lifecycle is a decision by the team and says nothing about deployment.\n");

  out.push("## Promotion Path\n");
  if (!view.promotionPath) {
    out.push("_No Promotion Path has been assigned to this Release Pack._\n");
  } else {
    out.push(`**${view.promotionPath.pathName}** — version ${view.promotionPath.versionNumber}\n`);
    out.push(`${view.promotionPath.environments.map((e) => (e as Env).name).join(" → ")}\n`);
    out.push(`_This release follows version ${view.promotionPath.versionNumber} of the path. Later versions may define a different sequence; this is the`);
    out.push("topology the release was planned against (ADR-007)._\n");
  }

  out.push("## Contents\n");
  if (!view.contents.length) {
    out.push("_No Application Versions have been added to this Release Pack._\n");
  } else {
    out.push("| Application | Version | Branch | Tag | Commit | Build |");
    out.push("|---|---|---|---|---|---|");
    [...view.contents]
      .sort((a, b) => a.applicationName.localeCompare(b.applicationName) || a.version.localeCompare(b.version))
      .forEach((c) => out.push(
        `| ${c.applicationName} | ${c.version} | ${dash(c.branch)} | ${dash(c.tag)} | ${dash(c.commit)} | ${dash(c.buildIdentifier)} |`));
    out.push("");
  }

  out.push("## Handover\n");
  const h = p.handover;
  const prepared = Object.values(h).some((x) => (x ?? "").trim() !== "");
  if (!prepared) {
    out.push("_No Handover information has been prepared for this Release Pack._\n");
  } else {
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

  out.push("## Validation Iterations\n");
  if (!p.iterations.length) {
    out.push("_No validation Iterations have been recorded against this Release Pack._\n");
  } else {
    out.push("| Iteration | Started | Completed | Notes |\n|---|---|---|---|");
    [...p.iterations]
      .sort((a, b) => a.startedAt.localeCompare(b.startedAt) || a.name.localeCompare(b.name))
      .forEach((i) => out.push(
        `| ${i.name} | ${i.startedAt} | ${i.completedAt ?? "_in progress_"} | ${oneLine(i.notes)} |`));
    out.push("");
  }

  out.push("## Where this release has been observed\n");
  if (!derived.sightings.length) {
    out.push("_This release has not been observed in any Environment._\n");
  } else {
    out.push("| Environment | Application | Version | Observed | Source | Observation |");
    out.push("|---|---|---|---|---|---|");
    derived.sightings.forEach((s) => {
      const o = state.observations.find(
        (x) => x.environmentId === (s.environment as Env).id
          && x.applicationVersionId === (s.applicationVersion as { id: string }).id
          && x.observedAt === s.observedAt);
      const src = o ? (o.source.actor ? `${o.source.collector} (${o.source.actor})` : o.source.collector) : "—";
      out.push(`| ${(s.environment as Env).name} | ${(s.application as { name: string }).name} | `
        + `${(s.applicationVersion as { version: string }).version} | ${s.observedAt} | ${src} | ${o?.id ?? "—"} |`);
    });
    out.push("");
  }

  out.push("---\n");
  out.push("_Generated by Tower from the Canonical Model. This document is a view of that model,");
  out.push("not a source of truth (BR-07), and can be regenerated at any time._");
  return out.join("\n") + "\n";
}

// --- routing ----------------------------------------------------------------

const seg = (p: string) => p.split("?")[0].split("/").filter(Boolean);

export function handle(pathname: string, method: string, body: Json | null): unknown {
  const s = seg(pathname);
  if (s[0] !== "api") throw notFound(`No route for ${pathname}`);
  const [, area, a, b, c, d] = s;

  if (area === "health") return { status: "UP", version: "demo" };

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
    if (a && b === "state") return environmentStateView(a);
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
    if (b === "documentation") {
      if (c === "markdown") return releaseMarkdown(p.id);
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
    if (b === "handover") { p.handover = { ...p.handover, ...(body as object) }; return packView(p); }
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
