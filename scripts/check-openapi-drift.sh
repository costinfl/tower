#!/usr/bin/env bash
# Re-slices each vendored API description from upstream and reports what changed.
#
# Deliberately not part of the build. scripts/acceptance.sh was kept network-free
# for the same reason: a build that goes red because a vendor edited its
# description is a build that went red for something the diff did not do.
# Refreshing a slice should be a decision somebody makes, having read what moved.
set -uo pipefail
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
DRIFTED=0

check() { # name  out-path  slicer-args...
  local name=$1 committed=$2; shift 2
  if [ ! -f "$committed" ]; then
    printf "    \033[33mSKIP\033[0m  %s — %s is not committed\n" "$name" "$committed"
    return
  fi
  local fresh="$TMP/$(basename "$committed")"
  if ! python3 scripts/slice-openapi.py "$@" --out "$fresh" 2>"$TMP/err"; then
    printf "    \033[33mSKIP\033[0m  %s — could not be fetched\n" "$name"
    sed 's/^/           /' "$TMP/err"
    return
  fi
  if diff -q "$committed" "$fresh" >/dev/null; then
    printf "    \033[32mSAME\033[0m  %s\n" "$name"
  else
    printf "    \033[31mDRIFT\033[0m %s\n" "$name"
    diff -u "$committed" "$fresh" | head -60 | sed 's/^/           /'
    DRIFTED=$((DRIFTED+1))
  fi
}

echo
echo "=============================================================="
echo " VENDORED API DESCRIPTIONS — committed against upstream"
echo "=============================================================="

check "GitHub" specs/github/api.github.com.slice.json \
  --from https://raw.githubusercontent.com/github/rest-api-description/main/descriptions/api.github.com/api.github.com.json \
  --path '/repos/{owner}/{repo}' \
  --path '/repos/{owner}/{repo}/issues/{issue_number}' \
  --title 'GitHub REST API — the slice Tower reads'

check "Jira" specs/jira/jira-cloud.slice.json \
  --from https://developer.atlassian.com/cloud/jira/platform/swagger-v3.v3.json \
  --path '/rest/api/3/issue/{issueIdOrKey}' \
  --path '/rest/api/3/serverInfo' \
  --path '/rest/api/3/myself' \
  --title 'Jira Cloud REST API — the slice Tower reads'

echo
if [ "$DRIFTED" -eq 0 ]; then
  echo " Nothing has drifted."
else
  echo " $DRIFTED description(s) drifted. Read the diff, then re-slice to accept it."
  echo " A schema that gained a required field may mean a fixture is now wrong."
fi
echo "=============================================================="
exit 0
