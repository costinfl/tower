#!/usr/bin/env bash
# Checks that the demo's release-document renderer still produces what
# tower-docgen produces.
#
# tower-web/src/demo/server.ts carries a second implementation of
# MarkdownReleaseDocumentRenderer so the published demo can preview a real
# document without a backend. Two implementations of one output drift, and this
# particular drift is invisible: nobody holds a demo page beside a running
# instance and compares them line by line.
#
# The comparison replays one identical sequence of API calls against both and
# diffs the documents. Whichever side changed, the other has to follow — a
# visitor is entitled to believe what the demo shows them.
#
# Usage:
#   scripts/verify-demo-docgen.sh              # against http://127.0.0.1:8080
#   TOWER_BASE=http://host:port scripts/verify-demo-docgen.sh
#
# Requires a running Tower. The demo side needs no server: its backend is a
# module, and this bundles it and calls it directly.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB="$ROOT/tower-web"
BASE="${TOWER_BASE:-http://127.0.0.1:8080}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

if ! curl -sf "$BASE/api/health" > /dev/null; then
  echo "No Tower at $BASE. Start one with ./run.sh, or set TOWER_BASE." >&2
  exit 2
fi

if [ ! -d "$WEB/node_modules" ]; then
  echo "tower-web/node_modules is missing. Run 'npm ci' in tower-web first." >&2
  exit 2
fi

# esbuild rather than tsc: the demo backend is TypeScript with an import of its
# own seed, and this needs one runnable ES module out of the pair. esbuild ships
# with Vite, so it is already installed and needs no network.
if ! (cd "$WEB" && npx --no-install esbuild src/demo/server.ts \
        --bundle --format=esm --platform=node --log-level=warning \
        --outfile="$OUT/demo-server.mjs"); then
  echo "Could not bundle the demo backend." >&2
  exit 2
fi

node "$ROOT/scripts/verify-demo-docgen.mjs" "$OUT/demo-server.mjs"
