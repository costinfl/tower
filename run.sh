#!/usr/bin/env bash
#
# Runs Tower locally: http://127.0.0.1:8080
#
# Why this script exists (issue #9)
# --------------------------------
# `mvn -pl tower-api spring-boot:run` resolves tower-domain and the other modules
# from the *local repository*, not from the reactor. Edit a module and re-run that
# command without `mvn install`, and the application starts against the previously
# installed jar.
#
# That failure is dangerous because it is usually silent. Adding a new class fails
# loudly with NoClassDefFoundError, which is luck; change the *body* of an existing
# method and the application boots happily and runs the old logic. Every check made
# against it is then worthless while looking entirely healthy. This bit twice during
# Milestone 2 — once producing a "fixed" error message that was still the old one.
#
# So the install always runs. There is deliberately no flag to skip it: an escape
# hatch would make the unsafe path a supported option, which is the trap this script
# exists to close. If you truly want to skip the rebuild, invoke Maven yourself and
# own the choice.
#
# Usage
# -----
#   ./run.sh                      # build everything, then run
#   ./run.sh --debug              # extra arguments are passed to spring-boot:run
#   TOWER_HOME=/tmp/x ./run.sh    # ADR-009: application data directory
#
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is not on the PATH. Tower needs Maven and a JDK 21." >&2
  exit 1
fi

echo "==> Building and installing all modules (so spring-boot:run cannot pick up a stale jar)"
mvn install -DskipTests

echo
echo "==> Starting Tower on http://127.0.0.1:8080"
echo "    Data directory: ${TOWER_HOME:-$HOME/.tower}"
echo
exec mvn -pl tower-api spring-boot:run ${1+"$@"}
