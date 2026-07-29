#!/usr/bin/env bash
# Milestone 2 acceptance — credentials and External Bindings (issues #46, #47),
# executed against a running Tower.
#
# Same discipline as scripts/acceptance.sh: every write asserts its status code.
# A discarded response code is how a verification run reports success against
# data that was never created.
#
# Covers only what needs no external system. The checks that need a real cluster
# are listed under "Milestone 2 exit" in docs/planning/Implementation-Plan.md and
# cannot be automated here — no assertion we write about our own Connector is
# evidence of what it did to someone else's cluster.
set -uo pipefail
B=http://127.0.0.1:8080
J='Content-Type: application/json'
PASS=0; FAIL=0

ok()   { printf "    \033[32mPASS\033[0m  %s\n" "$1"; PASS=$((PASS+1)); }
bad()  { printf "    \033[31mFAIL\033[0m  %s\n" "$1"; FAIL=$((FAIL+1)); }
check(){ if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected '$3', got '$2')"; fi; }

code() { # method url body -> prints status, body in /tmp/_m2
  curl -s -o /tmp/_m2 -w '%{http_code}' -X "$1" "$2" -H "$J" ${3:+-d "$3"}
}
jsonf() { python3 -c "import json;print(json.load(open('/tmp/_m2'))$1)"; }

TOKEN='sha256~acceptance-only-not-a-real-token'
CLUSTER='https://api.cluster.example:6443'

echo
echo "=============================================================="
echo " CREDENTIALS (#46) — write-only through the API"
echo "=============================================================="

check "store a credential" "$(code PUT $B/api/credentials \
  "{\"connectorId\":\"kubernetes\",\"target\":\"$CLUSTER\",\"secret\":\"$TOKEN\"}")" 204

check "reject a credential with no secret" "$(code PUT $B/api/credentials \
  "{\"connectorId\":\"kubernetes\",\"target\":\"$CLUSTER\",\"secret\":\"\"}")" 400

STATUS=$(curl -s -o /tmp/_m2 -w '%{http_code}' \
  "$B/api/credentials?connectorId=kubernetes&target=$(python3 -c "import urllib.parse;print(urllib.parse.quote('$CLUSTER'))")")
check "read credential status" "$STATUS" 200
check "status says configured" "$(jsonf "['configured']")" True

if grep -q "$TOKEN" /tmp/_m2; then
  bad "status response must not contain the token"
else
  ok "status response does not contain the token"
fi

echo
echo "=============================================================="
echo " BINDINGS (#47) — ADR-012"
echo "=============================================================="

# Unique per run. Environment and Application names must be unique, so fixed
# names would make a second run fail on 409 and report defects that are not there.
RUN=$$-$(date +%s)

mkfixture() { # url body label -> prints id, or fails loudly
  local status
  status=$(curl -s -o /tmp/_m2 -w '%{http_code}' -X POST "$1" -H "$J" -d "$2")
  if [ "$status" != "201" ]; then
    bad "$3 -> HTTP $status"; cat /tmp/_m2; echo; return 1
  fi
  python3 -c 'import json;print(json.load(open("/tmp/_m2"))["id"])'
}

ENV=$(mkfixture $B/api/environments \
  "{\"name\":\"M2-UAT-$RUN\",\"stage\":\"VALIDATION\"}" "create fixture Environment") || exit 1
APP=$(mkfixture $B/api/applications \
  "{\"name\":\"M2 Customer API $RUN\",\"description\":\"acceptance\"}" "create fixture Application") || exit 1
ok "fixtures created"

check "bind an Environment to a namespace" "$(code PUT $B/api/bindings/environments \
  "{\"environmentId\":\"$ENV\",\"connectorId\":\"kubernetes\",\"target\":\"$CLUSTER\",\"scope\":\"customer-uat\"}")" 200

check "re-point the Environment (replaces, not conflicts)" "$(code PUT $B/api/bindings/environments \
  "{\"environmentId\":\"$ENV\",\"connectorId\":\"kubernetes\",\"target\":\"$CLUSTER\",\"scope\":\"customer-uat2\"}")" 200

check "reject binding an Environment that does not exist" "$(code PUT $B/api/bindings/environments \
  "{\"environmentId\":\"00000000-0000-0000-0000-000000000000\",\"connectorId\":\"kubernetes\",\"target\":\"$CLUSTER\",\"scope\":\"x\"}")" 404

check "bind an Application to an image" "$(code PUT $B/api/bindings/applications \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"kubernetes\",\"image\":\"registry.example/acme/customer-api\",\"versionPattern\":\"^release-(.+)\$\"}")" 200

check "reject a version pattern that does not compile" "$(code PUT $B/api/bindings/applications \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"kubernetes\",\"image\":\"acme/api\",\"versionPattern\":\"^release-(.+\$\"}")" 400

check "reject a version pattern with no capturing group" "$(code PUT $B/api/bindings/applications \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"kubernetes\",\"image\":\"acme/api\",\"versionPattern\":\"^release-.+\$\"}")" 400

BINDS=$(curl -s -o /tmp/_m2 -w '%{http_code}' $B/api/bindings/environments)
check "list Environment bindings" "$BINDS" 200

# Counts bindings for this Environment only, so the assertion holds whether or
# not earlier runs left data behind.
MINE=$(python3 -c "
import json
print(sum(1 for b in json.load(open('/tmp/_m2')) if b['environmentId'] == '$ENV'))")
check "re-pointing replaced rather than duplicated" "$MINE" 1

SCOPE=$(python3 -c "
import json
print(next(b['scope'] for b in json.load(open('/tmp/_m2')) if b['environmentId'] == '$ENV'))")
check "the surviving binding is the re-pointed one" "$SCOPE" customer-uat2

echo
echo "--- version preview (the mistake ADR-012 cannot undo) ---"

PV=$(curl -s -o /tmp/_m2 -w '%{http_code}' \
  "$B/api/bindings/version-preview?versionPattern=%5Erelease-(.%2B)%24&imageTag=release-2026.08.1")
check "preview a matching tag" "$PV" 200
check "preview yields the version" "$(jsonf "['version']")" 2026.08.1

curl -s -o /tmp/_m2 "$B/api/bindings/version-preview?versionPattern=%5Erelease-(.%2B)%24&imageTag=latest"
check "preview reports a non-match without inventing a version" "$(jsonf "['matched']")" False

echo
echo "--- cleanup ---"
check "unbind the Environment" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/bindings/environments/$ENV?connectorId=kubernetes")" 204
check "forget the credential" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/credentials?connectorId=kubernetes&target=$(python3 -c "import urllib.parse;print(urllib.parse.quote('$CLUSTER'))")")" 204

echo
echo "=============================================================="
printf " RESULT: \033[32m%d passed\033[0m, \033[31m%d failed\033[0m\n" "$PASS" "$FAIL"
echo "=============================================================="
[ "$FAIL" -eq 0 ]
