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
    # To stderr, not stdout: the caller captures this function's output as an
    # id, and a failure message printed on stdout becomes the id — which then
    # goes into the next request body and fails it for an unrelated reason.
    # Every later assertion then reports the wrong cause.
    bad "$5 -> HTTP $code" >&2; cat /tmp/_b >&2; echo >&2; return 1
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

# The Scenarios use fixed names, so a second run against the same store gets 409
# on every create and every later assertion then fails for a reason that has
# nothing to do with what it is testing. Say so once, here, rather than let a
# reader work it out from fifty misleading failures.
if ! curl -sf "$B/api/health" > /dev/null; then
  echo "No Tower at $B. Start one with ./run.sh." >&2
  exit 2
fi
if curl -s "$B/api/environments" | grep -q '"name":"Dev1"'; then
  cat >&2 <<'STALE'
This Tower already holds the acceptance fixtures, so every create would be
refused as a duplicate and the run would report failures that are really
collisions. Start a Tower on an empty store and try again:

    TOWER_HOME=$(mktemp -d) ./run.sh
STALE
  exit 2
fi

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
echo " DOCUMENT TEMPLATES — OQ-010, ADR-013"
echo "=============================================================="
# Names are unique, so a rerun against the same instance needs its own.
TSTAMP=$(date +%s)
# Every section is named rather than counted. A count has to be bumped whenever
# a section is added — which says nothing about whether the right ones are
# there — and it passes just as happily if one section is swapped for another.
SECTIONS=$(curl -s "$B/api/document-templates/sections" | python3 -c '
import json,sys
print(",".join(sorted(s["name"] for s in json.load(sys.stdin))))')
check "the sections a template may choose from are served" "$SECTIONS" \
  "ARTIFACTS,CONTENTS,HANDOVER,ITERATIONS,PROMOTION_PATH,SIGHTINGS,STATUS,WORK_ITEMS"

BUILTIN=$(curl -s "$B/api/document-templates" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d[0]["builtIn"])')
check "the complete document heads the list" "$BUILTIN" "True"

TPL=$(mk POST $B/api/document-templates "{\"name\":\"Handover only $TSTAMP\",\"sections\":[\"STATUS\",\"HANDOVER\"]}" 201 "define a template")
REV=$(mk POST $B/api/document-templates "{\"name\":\"Reversed $TSTAMP\",\"sections\":[\"HANDOVER\",\"CONTENTS\"]}" 201 "define a reordered template")

curl -s "$B/api/release-packs/$PACK/documentation/markdown?template=$TPL" > /tmp/doc_t.md
grep -qF "## Handover" /tmp/doc_t.md && ok "the chosen section is present" || bad "the chosen section is missing"
grep -qF "## Contents" /tmp/doc_t.md && bad "an unchosen section was rendered" || ok "unchosen sections are absent"
grep -qF "not a source of truth" /tmp/doc_t.md && ok "the provenance note survives a template" || bad "the provenance note was dropped"
grep -qF "Release 2026.08" /tmp/doc_t.md && ok "the title survives a template" || bad "the title was dropped"
grep -qF "which does not include" /tmp/doc_t.md && ok "the document names what the template leaves out" || bad "omissions are silent"

# Ordering, which is the property a set-shaped design would lose.
curl -s "$B/api/release-packs/$PACK/documentation/markdown?template=$REV" > /tmp/doc_r.md
HPOS=$(grep -n "^## Handover" /tmp/doc_r.md | cut -d: -f1)
CPOS=$(grep -n "^## Contents" /tmp/doc_r.md | cut -d: -f1)
[ "$HPOS" -lt "$CPOS" ] && ok "sections render in the template's order" || bad "the template's order was not followed"

# NFR-025 for a templated document, not only a complete one.
sleep 1
curl -s "$B/api/release-packs/$PACK/documentation/markdown?template=$TPL" > /tmp/doc_t2.md
if diff -q /tmp/doc_t.md /tmp/doc_t2.md >/dev/null; then
  ok "a templated document regenerates byte-identically (sha $(sha256sum /tmp/doc_t.md | cut -c1-12))"
else
  bad "a templated document differs between generations"
fi

NAME=$(curl -sI "$B/api/release-packs/$PACK/documentation/html/download?template=$TPL" | grep -io 'filename="[^"]*"')
case "$NAME" in *handover-only*) ok "a templated download is named apart from the complete one" ;;
  *) bad "download filename does not distinguish the template ($NAME)" ;; esac

UNKNOWN=$(curl -s -o /dev/null -w '%{http_code}' "$B/api/release-packs/$PACK/documentation/markdown?template=99999999-9999-9999-9999-999999999999")
check "an unknown template is refused rather than substituted" "$UNKNOWN" "404"
DUP=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/document-templates" -H "$J" -d "{\"name\":\"Handover only $TSTAMP\",\"sections\":[\"CONTENTS\"]}")
check "a duplicate template name is refused" "$DUP" "409"
BADSEC=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/document-templates" -H "$J" -d '{"name":"Odd","sections":["APPENDIX"]}')
check "an unknown section is refused rather than dropped" "$BADSEC" "400"
EMPTY=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/document-templates" -H "$J" -d '{"name":"Empty","sections":[]}')
check "a template with no sections is refused" "$EMPTY" "400"
RESERVED=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/document-templates" -H "$J" -d '{"name":"complete document","sections":["CONTENTS"]}')
check "the built-in name cannot be taken" "$RESERVED" "400"
DELBUILT=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/document-templates/00000000-0000-0000-0000-000000000001")
check "the complete document cannot be deleted" "$DELBUILT" "400"

echo
echo "=============================================================="
echo " MILESTONE 4 — Snapshots and historical comparison (ADR-017)"
echo "=============================================================="
# Scenario 1 observed Customer API 2.5.0 in Dev1 on 20 July and in UAT on
# 22 July; Scenario 3 observed 2.5.1 in Production on 25 July.

# Nobody captured a snapshot on 21 July. The answer must still exist — that is
# the whole argument for deriving rather than storing.
UNCAPTURED=$(curl -s "$B/api/environments/$DEV1/state?at=2026-07-21T00:00:00Z" | python3 -c "
import json,sys
print(','.join(sorted(d['applicationVersion']['version'] for d in json.load(sys.stdin)['deployed'])))")
check "an instant nobody captured is answerable" "$UNCAPTURED" "2.5.0"

BEFORE=$(curl -s "$B/api/environments/$DEV1/state?at=2026-07-19T00:00:00Z" | python3 -c "
import json,sys; print(json.load(sys.stdin)['hasBeenObserved'])")
check "before anything was observed, state is empty rather than current" "$BEFORE" "False"

# At, not before: asking as of the moment of an Observation includes it.
INCLUSIVE=$(curl -s "$B/api/environments/$DEV1/state?at=2026-07-20T09:00:00Z" | python3 -c "
import json,sys; print(len(json.load(sys.stdin)['deployed']))")
check "the instant itself is included" "$INCLUSIVE" "1"

BADINSTANT=$(curl -s -o /dev/null -w '%{http_code}' "$B/api/environments/$DEV1/state?at=yesterday")
check "an unreadable instant is refused, not a server error" "$BADINSTANT" "400"

# Two Environments at one instant, and the kind of each difference named rather
# than left to be inferred from a null version.
KINDS=$(curl -s "$B/api/environments/$DEV1/state/comparison?against=$PROD" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(','.join(sorted(x['kind'] for x in d['differences'])))")
check "comparing two Environments names each kind of difference" "$KINDS" "CHANGED"

PACKNAMED=$(curl -s "$B/api/environments/$DEV1/state/comparison?against=$PROD" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(','.join(sorted(n for x in d['differences'] for n in x['releasePacks'])))")
check "a difference names the Release Pack holding that version" "$PACKNAMED" "Hotfix 2026.08.1"

SELF=$(curl -s "$B/api/environments/$UAT/state/comparison" | python3 -c "
import json,sys; print(json.load(sys.stdin)['identical'])")
check "an Environment compared with itself is identical" "$SELF" "True"

# Release 2026.08 holds 2.5.0 and 1.4.0. Only 2.5.0 was ever observed, so every
# arrival is partial and must name what is outstanding rather than only count.
PARTIAL=$(curl -s "$B/api/release-packs/$PACK/progression" | python3 -c "
import json,sys
d=json.load(sys.stdin)
a=[x for x in d['arrivals'] if x['environmentName']=='UAT'][0]
print('%s|%s|%s|%s' % (a['complete'], a['observedCount'], a['packedCount'],
                       ','.join(m['version'] for m in a['missing'])))")
check "a partly arrived release names what is missing" "$PARTIAL" "False|1|2|1.4.0"

# PreProd was never observed at all. It must be absent, not reported as empty:
# nobody looked there, which is not the same as the release not being there.
UNSEEN=$(curl -s "$B/api/release-packs/$PACK/progression" | python3 -c "
import json,sys
print(any(a['environmentName']=='PreProd' for a in json.load(sys.stdin)['arrivals']))")
check "an Environment never observed does not appear at all" "$UNSEEN" "False"

# The hotfix was seen whole in each Environment, so each arrival is complete.
WHOLE=$(curl -s "$B/api/release-packs/$HPACK/progression" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(','.join('%s:%s' % (a['environmentName'], a['complete']) for a in d['arrivals']))")
check "a release seen whole reports complete, oldest Environment first" "$WHOLE" \
  "Dev-Hotfix:True,SIT:True,Production:True"

echo
echo "=============================================================="
echo " MILESTONE 5 — Operational dashboard (issue #7)"
echo "=============================================================="
# Regular goes Dev1 → SIT1 → UAT → PreProd → Production; Hotfix goes
# Dev-Hotfix → SIT → UAT → Production. They share UAT and Production, so those
# two are the Environments both releases are heading for.

CONTESTED=$(curl -s "$B/api/dashboard" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(','.join(sorted(e['name'] for e in d['environments'] if e['contested'])))")
check "names the Environments two releases are heading for" "$CONTESTED" "Production,UAT"

check "counts them too" \
  "$(curl -s "$B/api/dashboard" | python3 -c "import json,sys;print(json.load(sys.stdin)['summary']['contestedEnvironments'])")" \
  "2"

# The order must not be a ranking: alphabetical, whatever the progress.
ORDER=$(curl -s "$B/api/dashboard" | python3 -c "
import json,sys
d=json.load(sys.stdin)
uat=[e for e in d['environments'] if e['name']=='UAT'][0]
print(','.join(c['name'] for c in uat['converging']))")
check "lists converging releases by name, not by how far along they are" "$ORDER" \
  "Hotfix 2026.08.1,Release 2026.08"

# The hotfix reached Production but was never observed in UAT. That must read
# as "nobody said", not as "not deployed".
STANDING=$(curl -s "$B/api/dashboard" | python3 -c "
import json,sys
d=json.load(sys.stdin)
uat=[e for e in d['environments'] if e['name']=='UAT'][0]
print(','.join('%s=%s' % (c['name'], c['standing']) for c in uat['converging']))")
check "names each standing for what Tower was told" "$STANDING" \
  "Hotfix 2026.08.1=NOT_OBSERVED_HERE,Release 2026.08=PARTLY_OBSERVED"

# A release only appears against Environments its own pinned path names. The
# Hotfix path has no PreProd, so it must not appear there.
NOHOTFIX=$(curl -s "$B/api/dashboard" | python3 -c "
import json,sys
d=json.load(sys.stdin)
pre=[e for e in d['environments'] if e['name']=='PreProd'][0]
print(any(c['name'].startswith('Hotfix') for c in pre['converging']))")
check "a release is not heading for an Environment its path omits" "$NOHOTFIX" "False"

FURTHEST=$(curl -s "$B/api/dashboard" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(','.join('%s->%s' % (p['name'], '/'.join(p['furthestEnvironments'])) for p in d['releasePacks']))")
check "reports how far each release got, releases listed by name" "$FURTHEST" \
  "Hotfix 2026.08.1->Production,Release 2026.08->UAT"

# SIT1 and PreProd have had nothing recorded against them.
check "counts Environments Tower has been told nothing about" \
  "$(curl -s "$B/api/dashboard" | python3 -c "import json,sys;print(json.load(sys.stdin)['summary']['environmentsNeverObserved'])")" \
  "2"

# The constraint the milestone turns on: no control here changes anything.
check "the dashboard refuses a write rather than half-accepting one" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/dashboard")" "405"

echo
echo "=============================================================="
echo " ISSUE TRACKER — a work item reference is Intent (ADR-018)"
echo "=============================================================="
# Nothing here reaches a tracker. The point of these checks is what Tower does
# with references on its own: a release names what it delivers whether or not a
# tracker is configured, reachable, or installed at all. Reading a live tracker
# is exercised by the Connector's own tests, which do not depend on somebody
# else's service being up while CI runs.
post "$B/api/release-packs/$PACK/work-items" '{"identifier":"#3","title":""}' 200 \
  "a release can name a work item"
post "$B/api/release-packs/$PACK/work-items" '{"identifier":"PROJ-12","title":"Save the basket"}' 200 \
  "a reference may carry a title somebody accepted"
DUPITEM=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/release-packs/$PACK/work-items" \
  -H "$J" -d '{"identifier":"#3","title":""}')
check "the same item is not delivered twice" "$DUPITEM" "409"

# UNRESOLVED, not NOT_FOUND: Tower never asked. The difference is the whole
# reason the state has four values rather than two.
UNASKED=$(curl -s "$B/api/release-packs/$PACK/work-items/resolved" | python3 -c '
import json,sys
d=json.load(sys.stdin)
print("%s|%s" % (d["reachedTracker"], ",".join(sorted(i["state"] for i in d["items"]))))')
check "with no tracker configured, items are unresolved rather than missing" "$UNASKED" \
  "False|UNRESOLVED,UNRESOLVED"

put "$B/api/bindings/issue-trackers" '{"connectorId":"github-issues","locator":"acme/retail"}' 200 \
  "a tracker is bound once, to a Connector"
BOUND=$(curl -s "$B/api/bindings/issue-trackers" | python3 -c '
import json,sys
print(",".join("%s@%s" % (b["connectorId"], b["locator"]) for b in json.load(sys.stdin)))')
check "the binding is held" "$BOUND" "github-issues@acme/retail"

BADBIND=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$B/api/bindings/issue-trackers" \
  -H "$J" -d '{"connectorId":"github-issues","locator":""}')
check "a binding with nowhere to look is refused" "$BADBIND" "400"

# The Connector is read-only, so there is no endpoint that could write to a
# tracker. Asserted the only way that means anything: by trying.
WRITE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/work-items/connection-test?connectorId=github-issues")
check "the tracker connection test refuses a write" "$WRITE" "405"

DELBIND=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/bindings/issue-trackers/github-issues")
check "a tracker can be unbound" "$DELBIND" "204"

# What a document prints, which is the property ADR-018 turns on: the title
# somebody accepted, never a title fetched behind their back.
curl -s "$B/api/release-packs/$PACK/documentation/markdown" > /tmp/doc_w.md
grep -qF "| PROJ-12 | Save the basket |" /tmp/doc_w.md \
  && ok "a document prints the accepted title" || bad "the accepted title is missing"
grep -qF "_no title accepted_" /tmp/doc_w.md \
  && ok "a reference with no accepted title says so rather than showing blank" \
  || bad "an unaccepted title renders as nothing"

# The same rule for a digest (ADR-021, FR-086). A tag is mutable, so a document
# prints what somebody accepted; nobody has accepted one here, and the section
# says that rather than leaving a reader to guess whether anything was built.
grep -qF "_No artifact digests have been accepted for this release._" /tmp/doc_w.md \
  && ok "a release with no accepted digest says so rather than showing blank" \
  || bad "the Artifacts section is missing or renders as nothing"

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
