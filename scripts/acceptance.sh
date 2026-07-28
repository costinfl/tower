#!/usr/bin/env bash
# Milestone 1 acceptance pass — the documented Scenarios, executed against a
# running Tower. Every write's status is asserted: a discarded response code is
# how a verification run reports success against data that was never created.
set -uo pipefail
B=http://127.0.0.1:8080
J='Content-Type: application/json'
PASS=0; FAIL=0

ok()   { printf "    \033[32mPASS\033[0m  %s\n" "$1"; PASS=$((PASS+1)); }
bad()  { printf "    \033[31mFAIL\033[0m  %s\n" "$1"; FAIL=$((FAIL+1)); }
check(){ if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected '$3', got '$2')"; fi; }

# Every create asserts its status before returning an id.
mk() { # method url body expected_code label
  local code body
  body=$(curl -s -o /tmp/_b -w '%{http_code}' -X "$1" "$2" -H "$J" ${3:+-d "$3"})
  code=$body
  if [ "$code" != "$4" ]; then
    bad "$5 -> HTTP $code"; cat /tmp/_b; echo; return 1
  fi
  python3 -c 'import json;print(json.load(open("/tmp/_b"))["id"])'
}
post() { # url body expected label  (no id needed)
  local code
  code=$(curl -s -o /tmp/_b -w '%{http_code}' -X POST "$1" -H "$J" ${2:+-d "$2"})
  check "$4" "$code" "$3"
}
put() {
  local code
  code=$(curl -s -o /tmp/_b -w '%{http_code}' -X PUT "$1" -H "$J" -d "$2")
  check "$4" "$code" "$3"
}
state() { curl -s "$B/api/release-packs/$1/state" | python3 -c 'import json,sys;print(json.load(sys.stdin)["state"])'; }

echo
echo "=============================================================="
echo " SCENARIO 1 — Standard Release"
echo "=============================================================="
DEV1=$(mk POST $B/api/environments '{"name":"Dev1","stage":"DEVELOPMENT"}' 201 "create Dev1")
SIT1=$(mk POST $B/api/environments '{"name":"SIT1","stage":"VALIDATION"}' 201 "create SIT1")
UAT=$(mk POST $B/api/environments '{"name":"UAT","stage":"VALIDATION"}' 201 "create UAT")
PREPROD=$(mk POST $B/api/environments '{"name":"PreProd","stage":"PRE_PRODUCTION"}' 201 "create PreProd")
PROD=$(mk POST $B/api/environments '{"name":"Production","stage":"PRODUCTION"}' 201 "create Production")
CUST=$(mk POST $B/api/applications '{"name":"Customer API","description":"Customer facing"}' 201 "create Customer API")
ORD=$(mk POST $B/api/applications '{"name":"Orders API","description":"Order handling"}' 201 "create Orders API")
CV=$(mk POST $B/api/application-versions "{\"applicationId\":\"$CUST\",\"version\":\"2.5.0\",\"tag\":\"v2.5.0\",\"commit\":\"abc1234\"}" 201 "register Customer API 2.5.0")
OV=$(mk POST $B/api/application-versions "{\"applicationId\":\"$ORD\",\"version\":\"1.4.0\"}" 201 "register Orders API 1.4.0")
REG=$(mk POST $B/api/promotion-paths "{\"name\":\"Regular\",\"environmentIds\":[\"$DEV1\",\"$SIT1\",\"$UAT\",\"$PREPROD\",\"$PROD\"]}" 201 "create Regular path")
PACK=$(mk POST $B/api/release-packs '{"name":"Release 2026.08","description":"August business features"}' 201 "create Release Pack")

post "$B/api/release-packs/$PACK/versions/$CV" "" 200 "add Customer API 2.5.0 to pack"
post "$B/api/release-packs/$PACK/versions/$OV" "" 200 "add Orders API 1.4.0 to pack"
put  "$B/api/release-packs/$PACK/promotion-path" "{\"pathId\":\"$REG\",\"versionNumber\":1}" 200 "assign Regular v1"
put  "$B/api/release-packs/$PACK/handover" '{"deploymentInstructions":"Deploy customer-api before orders-api.","shellCommands":"kubectl rollout status deploy/customer-api","databaseMigrations":"V37__add_customer_index.sql","rollbackProcedure":"Roll back orders-api first.","validationNotes":"Smoke test checkout.","operationalNotes":"Expect brief latency."}' 200 "record Handover"

check "state before any Observation is PLANNED" "$(state $PACK)" "PLANNED"
post "$B/api/observations" "{\"environmentId\":\"$DEV1\",\"applicationVersionId\":\"$CV\",\"observedAt\":\"2026-07-20T09:00:00Z\"}" 201 "observe in Dev1"
check "after Dev1, state is DEVELOPMENT" "$(state $PACK)" "DEVELOPMENT"
post "$B/api/observations" "{\"environmentId\":\"$UAT\",\"applicationVersionId\":\"$CV\",\"observedAt\":\"2026-07-22T09:00:00Z\"}" 201 "observe in UAT"
check "after UAT, state is VALIDATION" "$(state $PACK)" "VALIDATION"
post "$B/api/release-packs/$PACK/iterations" '{"name":"SIT Iteration 1","notes":"first pass"}' 201 "start SIT Iteration 1"

echo
echo "=============================================================="
echo " SCENARIO 3 — Production Hotfix (shares UAT and Production)"
echo "=============================================================="
DEVH=$(mk POST $B/api/environments '{"name":"Dev-Hotfix","stage":"DEVELOPMENT"}' 201 "create Dev-Hotfix")
SIT=$(mk POST $B/api/environments '{"name":"SIT","stage":"VALIDATION"}' 201 "create SIT")
HOTFIX=$(mk POST $B/api/promotion-paths "{\"name\":\"Hotfix\",\"environmentIds\":[\"$DEVH\",\"$SIT\",\"$UAT\",\"$PROD\"]}" 201 "create Hotfix path")
HV=$(mk POST $B/api/application-versions "{\"applicationId\":\"$CUST\",\"version\":\"2.5.1\",\"tag\":\"v2.5.1\"}" 201 "register hotfix 2.5.1")
HPACK=$(mk POST $B/api/release-packs '{"name":"Hotfix 2026.08.1","description":"Checkout defect"}' 201 "create Hotfix pack")
post "$B/api/release-packs/$HPACK/versions/$HV" "" 200 "add 2.5.1 to hotfix pack"
put  "$B/api/release-packs/$HPACK/promotion-path" "{\"pathId\":\"$HOTFIX\",\"versionNumber\":1}" 200 "assign Hotfix v1"

# ADR-005: both paths reference the same UAT and Production ids.
SHARED=$(curl -s "$B/api/promotion-paths" | python3 -c "
import json,sys
paths={p['name']:{e['id'] for e in p['currentVersion']['environments']} for p in json.load(sys.stdin)}
print(len(paths['Regular'] & paths['Hotfix']))")
check "Regular and Hotfix share environments (ADR-005)" "$SHARED" "2"

post "$B/api/observations" "{\"environmentId\":\"$DEVH\",\"applicationVersionId\":\"$HV\",\"observedAt\":\"2026-07-23T09:00:00Z\"}" 201 "observe hotfix in Dev-Hotfix"
post "$B/api/observations" "{\"environmentId\":\"$SIT\",\"applicationVersionId\":\"$HV\",\"observedAt\":\"2026-07-24T09:00:00Z\"}" 201 "observe hotfix in SIT"
post "$B/api/observations" "{\"environmentId\":\"$PROD\",\"applicationVersionId\":\"$HV\",\"observedAt\":\"2026-07-25T09:00:00Z\"}" 201 "observe hotfix in Production"
check "hotfix reaches PRODUCTION with no PreProd in its path (ADR-008)" "$(state $HPACK)" "PRODUCTION"
HASPREPROD=$(curl -s "$B/api/promotion-paths" | python3 -c "
import json,sys
h=[p for p in json.load(sys.stdin) if p['name']=='Hotfix'][0]
print(any(e['stage']=='PRE_PRODUCTION' for e in h['currentVersion']['environments']))")
check "Hotfix path genuinely contains no PRE_PRODUCTION environment" "$HASPREPROD" "False"

echo
echo "=============================================================="
echo " SCENARIO 2 — Parallel Releases progress independently"
echo "=============================================================="
check "Regular pack state unaffected by hotfix progress" "$(state $PACK)" "VALIDATION"
check "Hotfix pack state unaffected by regular pack" "$(state $HPACK)" "PRODUCTION"
IND=$(curl -s "$B/api/release-packs" | python3 -c "
import json,sys
ps={p['name']:p for p in json.load(sys.stdin)}
a,b=ps['Release 2026.08'],ps['Hotfix 2026.08.1']
print('ok' if (a['promotionPath']['pathName']!=b['promotionPath']['pathName']
   and len(a['iterations'])!=len(b['iterations'])
   and a['handover']['deploymentInstructions']!=b['handover']['deploymentInstructions']) else 'shared')")
check "each pack owns its own path, handover and iterations" "$IND" "ok"

echo
echo "=============================================================="
echo " SCENARIO 7 — Environment Drift is reported, not judged"
echo "=============================================================="
DRIFT=$(mk POST $B/api/application-versions "{\"applicationId\":\"$ORD\",\"version\":\"9.9.9-experimental\"}" 201 "register a version in no pack")
post "$B/api/observations" "{\"environmentId\":\"$UAT\",\"applicationVersionId\":\"$DRIFT\",\"observedAt\":\"2026-07-26T09:00:00Z\"}" 201 "observe the stray version in UAT"
REPORTED=$(curl -s "$B/api/environments/$UAT/state" | python3 -c "
import json,sys
d=json.load(sys.stdin)['deployed']
print('yes' if any(x['applicationVersion']['version']=='9.9.9-experimental' for x in d) else 'no')")
check "UAT reports the drifting version" "$REPORTED" "yes"
check "the Regular pack's state is not altered by a version it does not contain" "$(state $PACK)" "VALIDATION"
JUDGED=$(curl -s "$B/api/environments/$UAT/state" | grep -icE '"(warning|error|invalid|unexpected|drift)"' || true)
check "Tower attaches no judgement to the drift" "$JUDGED" "0"

echo
echo "=============================================================="
echo " SCENARIO 8 — Documentation Generation"
echo "=============================================================="
curl -s "$B/api/release-packs/$PACK/documentation/markdown" > /tmp/doc_a.md
sleep 1
curl -s "$B/api/release-packs/$PACK/documentation/markdown" > /tmp/doc_b.md
if diff -q /tmp/doc_a.md /tmp/doc_b.md >/dev/null; then
  ok "regeneration is byte-identical ($(wc -c < /tmp/doc_a.md) bytes, sha $(sha256sum /tmp/doc_a.md | cut -c1-12))"
else
  bad "regeneration differs"; diff /tmp/doc_a.md /tmp/doc_b.md | head
fi
for needle in "Release 2026.08" "Customer API" "Orders API" "Regular" "version 1" "Deploy customer-api before orders-api." "SIT Iteration 1" "not a source of truth"; do
  grep -qF "$needle" /tmp/doc_a.md && ok "document contains: $needle" || bad "document missing: $needle"
done
TRACE=$(grep -cE '\| (Dev1|UAT) \| Customer API \|' /tmp/doc_a.md || true)
check "sightings cite environment and application" "$TRACE" "2"
grep -qE 'manual \(' /tmp/doc_a.md && ok "each sighting names its source" || bad "sightings lack provenance"

echo
echo "=============================================================="
echo " READ-ONLY — Tower observes and never acts (ADR-001)"
echo "=============================================================="
ACTION=$(curl -s "$B/api/release-packs" | grep -icE '"(promote|deploy|trigger|execute|rollback)"' || true)
check "no action verb appears in the API surface" "$ACTION" "0"
FUT=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/observations" -H "$J" -d "{\"environmentId\":\"$UAT\",\"applicationVersionId\":\"$CV\",\"observedAt\":\"2030-01-01T00:00:00Z\"}")
check "a future-dated Observation is refused" "$FUT" "409"
DELREF=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/environments/$UAT")
check "deleting a referenced Environment is refused" "$DELREF" "409"
DELPACK=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/release-packs/$PACK")
check "deleting a pack carrying validation history is refused" "$DELPACK" "409"

echo
echo "=============================================================="
printf " RESULT: %d passed, %d failed\n" "$PASS" "$FAIL"
echo "=============================================================="
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
