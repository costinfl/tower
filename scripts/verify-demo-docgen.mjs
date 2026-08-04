// Compares the demo's release-document renderer against the real one.
//
// tower-web/src/demo/server.ts contains a second implementation of
// MarkdownReleaseDocumentRenderer, written so the published demo can preview a
// real document without a backend. Two implementations of the same output drift
// — that is not a risk, it is what happens — and the drift is invisible, because
// nobody compares a demo page against a running instance by eye.
//
// So this does compare them, and it does it the only way that proves anything:
// the same sequence of API calls is replayed against both, and the documents
// they produce are diffed byte for byte.
//
// Both sides are driven through `api(method, path, body)`. For the real backend
// that is fetch; for the demo it is the same `handle()` the fetch shim calls in
// the browser. The fixture below is written once and runs against both, so a
// difference in the output cannot be a difference in the input.
//
// Two kinds of value are normalised away, and only two: generated Observation
// ids, and the actor an Observation was recorded by. Neither can match between
// two independent stores — the real backend names the operating-system user,
// the demo names "you" — and both reach the page because every claim in the
// document cites the fact behind it. Both are replaced by position rather than
// by pattern, so a genuine change in which Observation is cited, or in how a
// source is written, still shows up as a diff. Everything else differing is
// drift, and is reported as such.

import { createHash } from "node:crypto";

const shaOf = (text) => createHash("sha256").update(text).digest("hex").slice(0, 12);

const BASE = process.env.TOWER_BASE ?? "http://127.0.0.1:8080";
const DEMO_BUNDLE = process.argv[2];

if (!DEMO_BUNDLE) {
  console.error("usage: node verify-demo-docgen.mjs <path-to-bundled-demo-server.mjs>");
  process.exit(2);
}

const demo = await import(DEMO_BUNDLE);

// One suffix for both runs. Names reach the document, so the two sides must use
// the same ones; a per-side suffix would produce a diff that means nothing.
const RUN = process.env.TOWER_RUN_ID ?? `parity-${Date.now().toString(36)}`;

// --- the two transports -----------------------------------------------------

async function realApi(method, path, body) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${method} ${path} -> ${response.status} ${text.slice(0, 300)}`);
  }
  if (response.status === 204 || text === "") return undefined;
  return response.headers.get("content-type")?.includes("json") ? JSON.parse(text) : text;
}

// The demo is synchronous, but is wrapped in the same async shape so the
// fixture below cannot tell the two apart.
async function demoApi(method, path, body) {
  try {
    return demo.handle(path, method, body === undefined ? null : body);
  } catch (e) {
    if (e instanceof demo.ApiFailure) {
      throw new Error(`${method} ${path} -> ${e.status} ${e.message}`);
    }
    throw e;
  }
}

// --- the fixture ------------------------------------------------------------

// Deliberately broad: an Application Version with every optional attribute and
// one with none (the dash rendering), a Handover with a blank field among
// prepared ones, an Iteration still in progress beside a completed one, and a
// note containing a pipe, which is the character that breaks a Markdown table.
//
// A second, entirely empty Release Pack is built alongside it, and it is not an
// afterthought. A populated pack never reaches the branches that state an
// absence — "No Application Versions have been added to this Release Pack" and
// its five siblings — and those are long prose strings duplicated across two
// implementations, which is precisely what drifts. A comparison that only ever
// rendered a full document would pass while every one of them disagreed.
//
// Dates are fixed and in the past. An Observation dated in the future is
// refused by the real backend, so a fixture built from "now" would fail on the
// wrong side of midnight rather than on a real disagreement.
async function buildFixture(api) {
  const observationIds = [];
  const actors = new Set();

  const env = async (name, stage) => (await api("POST", "/api/environments", { name, stage })).id;
  const dev1 = await env(`Dev1 ${RUN}`, "DEVELOPMENT");
  const sit1 = await env(`SIT1 ${RUN}`, "VALIDATION");
  const uat = await env(`UAT ${RUN}`, "VALIDATION");
  const prod = await env(`Production ${RUN}`, "PRODUCTION");

  const path = await api("POST", "/api/promotion-paths", {
    name: `Regular ${RUN}`,
    environmentIds: [dev1, sit1, uat, prod],
  });

  const customer = await api("POST", "/api/applications", {
    name: `Customer API ${RUN}`,
    description: "Customer-facing API",
  });
  const orders = await api("POST", "/api/applications", {
    name: `Orders API ${RUN}`,
    description: "",
  });

  const customerVersion = await api("POST", "/api/application-versions", {
    applicationId: customer.id,
    version: "2.5.0",
    branch: "release/2.5",
    tag: "v2.5.0",
    commit: "abc1234",
    buildIdentifier: "build-991",
  });
  // Every optional attribute absent, so the em-dash rendering is compared too.
  const ordersVersion = await api("POST", "/api/application-versions", {
    applicationId: orders.id,
    version: "1.9.0",
  });

  const pack = await api("POST", "/api/release-packs", {
    name: `Docgen Parity ${RUN}`,
    description: "Comparing the demo renderer against the real one.",
  });

  await api("PUT", `/api/release-packs/${pack.id}/promotion-path`, {
    pathId: path.id,
    versionNumber: 1,
  });
  await api("POST", `/api/release-packs/${pack.id}/versions/${customerVersion.id}`);
  await api("POST", `/api/release-packs/${pack.id}/versions/${ordersVersion.id}`);

  await api("PUT", `/api/release-packs/${pack.id}/handover`, {
    deploymentInstructions: "Deploy customer-api before orders-api.",
    shellCommands: "kubectl rollout status deploy/customer-api\nkubectl get pods | grep -v Running",
    databaseMigrations: "V37__add_index.sql",
    // Left blank on purpose: a prepared Handover with one empty field must say
    // "Not prepared." for that field rather than omit the heading.
    rollbackProcedure: "",
    validationNotes: "Smoke test checkout.",
    operationalNotes: "Expect brief latency during the rollout.",
  });

  const withIteration = await api("POST", `/api/release-packs/${pack.id}/iterations`, {
    name: "SIT Iteration 1",
    startedAt: "2026-06-08T09:00:00Z",
    // A pipe and a newline: the two characters that break a Markdown table.
    notes: "Signed off | no defects\noutstanding.",
  });
  const firstIteration = withIteration.iterations[0].id;
  await api("POST", `/api/release-packs/${pack.id}/iterations/${firstIteration}/complete`, {
    completedAt: "2026-06-12T17:00:00Z",
  });
  // A second, still open, so "in progress" is compared as well.
  await api("POST", `/api/release-packs/${pack.id}/iterations`, {
    name: "UAT Iteration 1",
    startedAt: "2026-06-15T09:00:00Z",
    notes: "",
  });

  // An accepted artifact digest on one version and not the other (ADR-021,
  // FR-086). Both cases reach the Artifacts section: a row that prints, and a
  // version with nothing accepted that must not.
  //
  // Accepted rather than confirmed, deliberately. Confirming reads a repository
  // and neither side has one; what a document prints is what somebody accepted,
  // and that is exactly the value both implementations must agree on.
  await api("PUT", `/api/application-versions/${customerVersion.id}/artifacts/image/accepted-digest`, {
    coordinate: "docker-local/acme/customer-api:2.5.0-abc1234",
    digest: "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  });
  await api("PUT", `/api/application-versions/${customerVersion.id}/artifacts/chart/accepted-digest`, {
    coordinate: "helm-local/customer-api-2.5.0-abc1234.tgz",
    digest: "sha1:2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
  });

  const observe = async (environmentId, applicationVersionId, observedAt) => {
    const observation = await api("POST", "/api/observations", {
      environmentId,
      applicationVersionId,
      observedAt,
    });
    observationIds.push(observation.id);
    // Whoever this instance credits the Observation to. Captured rather than
    // assumed, because the real backend uses the operating-system user and the
    // demo uses a fixed name; comparing them would fail on every machine.
    if (observation.source?.actor) actors.add(observation.source.actor);
  };
  await observe(dev1, customerVersion.id, "2026-06-01T10:00:00Z");
  await observe(dev1, ordersVersion.id, "2026-06-01T10:05:00Z");
  await observe(uat, customerVersion.id, "2026-06-10T14:00:00Z");

  // Nothing assigned, nothing added, nothing prepared, nothing observed.
  const emptyPack = await api("POST", "/api/release-packs", {
    name: `Docgen Parity Empty ${RUN}`,
    description: "",
  });

  return {
    packId: pack.id,
    emptyPackId: emptyPack.id,
    observationIds,
    actors: [...actors].sort(),
  };
}

// Named distinctively and removed first, because the template's name reaches
// the document's closing note: a suffix would make the two sides differ for a
// reason that is not drift.
const TEMPLATES = [
  { name: "Docgen parity handover", sections: ["STATUS", "HANDOVER"] },
  { name: "Docgen parity reversed", sections: ["SIGHTINGS", "HANDOVER", "CONTENTS"] },
];

async function defineTemplates(api) {
  const existing = await api("GET", "/api/document-templates");
  for (const template of existing) {
    if (!template.builtIn && TEMPLATES.some((t) => t.name === template.name)) {
      await api("DELETE", `/api/document-templates/${template.id}`);
    }
  }
  const created = [];
  for (const template of TEMPLATES) {
    created.push(await api("POST", "/api/document-templates", template));
  }
  return created;
}

// --- comparison -------------------------------------------------------------

async function documentsFrom(api) {
  const { packId, emptyPackId, observationIds, actors } = await buildFixture(api);
  const templates = await defineTemplates(api);

  const markdown = async (pack, templateId) =>
    api("GET", `/api/release-packs/${pack}/documentation/markdown`
      + (templateId ? `?template=${templateId}` : ""));

  const documents = {
    "complete document": await markdown(packId, null),
    "complete document, empty Release Pack": await markdown(emptyPackId, null),
    "Docgen parity handover": await markdown(packId, templates[0].id),
    "Docgen parity handover, empty Release Pack": await markdown(emptyPackId, templates[0].id),
    "Docgen parity reversed": await markdown(packId, templates[1].id),
  };

  // The surrounding "manual (…)" stays under comparison; only the identity
  // inside it is replaced, so a side that stopped naming its collector, or
  // dropped the parentheses, would still be caught.
  const normalise = (text) => {
    let out = observationIds.reduce(
      (acc, id, index) => acc.split(id).join(`<observation-${index + 1}>`),
      text,
    );
    for (const actor of actors) {
      out = out.split(`(${actor})`).join("(<actor>)");
    }
    return out;
  };

  return Object.fromEntries(
    Object.entries(documents).map(([label, text]) => [label, normalise(text)]),
  );
}

function report(label, expected, actual) {
  if (expected === actual) {
    const sha = shaOf(expected);
    console.log(`    PASS  ${label} — identical (${expected.length} chars, sha ${sha})`);
    return true;
  }
  console.log(`    FAIL  ${label} — the demo and the real renderer disagree`);
  const a = expected.split("\n");
  const b = actual.split("\n");
  let shown = 0;
  for (let i = 0; i < Math.max(a.length, b.length) && shown < 12; i++) {
    if (a[i] !== b[i]) {
      console.log(`          line ${i + 1}`);
      console.log(`            real: ${JSON.stringify(a[i] ?? "<missing>")}`);
      console.log(`            demo: ${JSON.stringify(b[i] ?? "<missing>")}`);
      shown++;
    }
  }
  if (shown === 12) console.log("          … further differences suppressed");
  return false;
}

console.log();
console.log("==============================================================");
console.log(" DEMO DOCGEN PARITY — the demo renderer against the real one");
console.log("==============================================================");
console.log(`    backend: ${BASE}`);
console.log(`    fixture: ${RUN}`);
console.log();

const real = await documentsFrom(realApi);
const fake = await documentsFrom(demoApi);

let failures = 0;
for (const label of Object.keys(real)) {
  if (!report(label, real[label], fake[label])) failures++;
}

console.log();
console.log("==============================================================");
console.log(`    RESULT: ${Object.keys(real).length - failures} matched, ${failures} drifted`);
console.log("==============================================================");
console.log();

if (failures > 0) {
  console.log("The demo renderer in tower-web/src/demo/server.ts no longer produces what");
  console.log("tower-docgen produces. Whichever changed, the other has to follow: the");
  console.log("published demo shows a document a visitor is entitled to believe.");
  console.log();
}

process.exit(failures === 0 ? 0 : 1);
