#!/usr/bin/env bash
# Artifact Repository Connector acceptance (ADR-021, FR-082 to FR-086), executed against a
# running Tower and the stand-in Artifactory beside this script.
#
# Same discipline as scripts/acceptance.sh and scripts/acceptance-m2.sh: every write asserts
# its status code, because a discarded response code is how a verification run reports success
# against data that was never created.
#
# What this establishes and what it does not. It establishes that Tower composes a coordinate
# from a version it holds, asks for it, and reports what came back - through the binding, the
# service, the Collector, the Connector and real HTTP. It establishes that nothing but GET left
# the process, asserted from the far side of the socket rather than from anything the Connector
# says about itself.
#
# It establishes nothing about Artifactory. The stand-in answers bodies written from knowledge
# of the API rather than recorded off a real instance. The live check stays open in CHECKLIST.md
# and a green run here does not close it.
#
# Usage:
#   TOWER_HOME=$(mktemp -d) ./run.sh          # in one shell
#   ./scripts/acceptance-artifacts.sh          # in another
set -uo pipefail
B=http://127.0.0.1:8080
A=http://127.0.0.1:9099
J='Content-Type: application/json'
PASS=0; FAIL=0

ok()   { printf "    \033[32mPASS\033[0m  %s\n" "$1"; PASS=$((PASS+1)); }
bad()  { printf "    \033[31mFAIL\033[0m  %s\n" "$1"; FAIL=$((FAIL+1)); }
check(){ if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected '$3', got '$2')"; fi; }

code() { # method url body -> prints status, body in /tmp/_art
  curl -s -o /tmp/_art -w '%{http_code}' -X "$1" "$2" -H "$J" ${3:+-d "$3"}
}
jsonf() { python3 -c "import json;print(json.load(open('/tmp/_art'))$1)"; }

# One kind's field out of a confirmation, so the assertions below read as what they check
# rather than as a list comprehension.
kindf() { # kind field
  python3 -c "
import json, sys
artifacts = json.load(open('/tmp/_art'))['artifacts']
for artifact in artifacts:
    if artifact['kind'] == '$1':
        print(artifact['$2'])
        sys.exit(0)
print('no such kind')
"
}

STANDIN="$(dirname "$0")/stand-in-artifactory.py"
COMMIT=abc1234def5678901234567890abcdef12345678
SUFFIX=$RANDOM$RANDOM

# ---------------------------------------------------------------------------
# The stand-in, started here so the run needs nothing prepared by hand.
# ---------------------------------------------------------------------------
python3 "$STANDIN" 9099 >/tmp/_art-standin.log 2>&1 &
STANDIN_PID=$!
trap 'kill $STANDIN_PID 2>/dev/null' EXIT
for _ in $(seq 1 20); do
  curl -sf "$A/api/repositories" >/dev/null 2>&1 && break
  sleep 0.3
done
if ! curl -sf "$A/api/repositories" >/dev/null 2>&1; then
  echo "The stand-in Artifactory did not start on 9099. Is the port in use?" >&2
  exit 1
fi

echo
echo "=============================================================="
echo " COORDINATE TEMPLATES (ADR-021, FR-083) — composing, not extracting"
echo "=============================================================="

check "register an Application" "$(code POST $B/api/applications \
  "{\"name\":\"Artifact Check $SUFFIX\",\"description\":\"artifact acceptance\"}")" 201
APP=$(jsonf "['id']")

check "register a version carrying a commit" \
  "$(code POST $B/api/application-versions \
    "{\"applicationId\":\"$APP\",\"version\":\"2.5.0\",\"commit\":\"$COMMIT\"}")" 201
VER=$(jsonf "['id']")

check "register a version carrying no commit" \
  "$(code POST $B/api/application-versions \
    "{\"applicationId\":\"$APP\",\"version\":\"2.6.0\"}")" 201
BARE=$(jsonf "['id']")

check "bind a chart template" "$(code PUT $B/api/bindings/artifact-coordinates \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"artifactory\",\"kind\":\"chart\",\"system\":\"$A\",\
    \"coordinateTemplate\":\"helm-local/api-{version}-{shortCommit}.tgz\"}")" 200
check "the short commit length defaults to seven" "$(jsonf "['shortCommitLength']")" 7

check "bind an image template on the same Application" "$(code PUT $B/api/bindings/artifact-coordinates \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"artifactory\",\"kind\":\"image\",\"system\":\"$A\",\
    \"coordinateTemplate\":\"docker-local/acme/api:{version}-{shortCommit}\"}")" 200

# The one binding an Application may have several of for a single Connector. Three kinds is the
# ordinary case rather than an edge: a build publishes the application, an image and a chart.
check "bind a third kind, the team's own word for it" "$(code PUT $B/api/bindings/artifact-coordinates \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"artifactory\",\"kind\":\"installer\",\"system\":\"$A\",\
    \"coordinateTemplate\":\"generic-local/api-{version}.run\"}")" 200

check "reject a token Tower does not recognise" "$(code PUT $B/api/bindings/artifact-coordinates \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"artifactory\",\"kind\":\"typo\",\"system\":\"$A\",\
    \"coordinateTemplate\":\"docker-local/api:{version}-{shortcommit}\"}")" 400

check "reject a template that names no part of the version" "$(code PUT $B/api/bindings/artifact-coordinates \
  "{\"applicationId\":\"$APP\",\"connectorId\":\"artifactory\",\"kind\":\"fixed\",\"system\":\"$A\",\
    \"coordinateTemplate\":\"docker-local/api:latest\"}")" 400

echo
echo "--- coordinate preview (false absence caught before somebody goes looking) ---"

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' \
  "$B/api/bindings/coordinate-preview?coordinateTemplate=helm-local/api-%7Bversion%7D-%7BshortCommit%7D.tgz&version=2.5.0&commit=$COMMIT")
check "preview a template against a version" "$STATUS" 200
check "the preview composes the coordinate" "$(jsonf "['composed']")" \
  "helm-local/api-2.5.0-abc1234.tgz"

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' \
  "$B/api/bindings/coordinate-preview?coordinateTemplate=api:%7Bversion%7D-%7BshortCommit%7D&version=2.5.0")
check "preview names what a version without a commit is missing" "$(jsonf "['missing']")" "['shortCommit']"

echo
echo "=============================================================="
echo " CONFIRMATION (FR-082, FR-084, FR-085) — read, show, store nothing"
echo "=============================================================="

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' "$B/api/application-versions/$VER/artifacts")
check "confirm a version's artifacts" "$STATUS" 200
check "every bound kind is reported" "$(jsonf "['artifacts'].__len__()")" 3

check "the chart is present" \
  "$(kindf chart state)" PRESENT
check "with the repository's own digest" \
  "$(kindf chart digest)" "sha256:ccc"
check "and when the repository received it" \
  "$(kindf chart storedAt)" "2024-05-01T08:00:00Z"

# The tag is a folder and the manifest inside it is the bytes. That mapping is the only piece of
# Artifactory's own shape anywhere in Tower, and it lives inside the Connector.
check "the image is present, read at its manifest" \
  "$(kindf image state)" PRESENT

# Ordinary rather than alarming on its own: a build may not have run. It is also what a wrong
# template produces, which ADR-021 argues is the mildest failure any binding in Tower can cause.
check "an artifact the repository does not hold is absent, not an error" \
  "$(kindf installer state)" ABSENT

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' "$B/api/application-versions/$BARE/artifacts")
check "confirm a version carrying no commit" "$STATUS" 200
check "a template needing a commit is reported, not guessed at" \
  "$(kindf chart state)" NOT_ADDRESSABLE

echo
echo "--- accepted digests (FR-086) — what a document prints ---"

check "accept the digest somebody looked at" \
  "$(code PUT "$B/api/application-versions/$VER/artifacts/chart/accepted-digest" \
    '{"coordinate":"helm-local/api-2.5.0-abc1234.tgz","digest":"sha256:ccc"}')" 200

check "reject an acceptance carrying no digest" \
  "$(code PUT "$B/api/application-versions/$VER/artifacts/chart/accepted-digest" \
    '{"coordinate":"helm-local/api-2.5.0-abc1234.tgz","digest":""}')" 400

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' "$B/api/application-versions/$VER/artifacts")
check "the confirmation carries it" "$STATUS" 200
check "the accepted digest is shown beside the repository's" "$(kindf chart acceptedDigest)" "sha256:ccc"
check "and the two agree, so the state is unchanged" "$(kindf chart state)" PRESENT

# Standing in for a tag pushed over. Accepting bytes the repository does not have
# exercises exactly the comparison a re-push produces, and is the only way to
# produce one against a stand-in that serves fixed bodies.
check "accept a digest the repository no longer reports" \
  "$(code PUT "$B/api/application-versions/$VER/artifacts/image/accepted-digest" \
    '{"coordinate":"docker-local/acme/api:2.5.0-abc1234","digest":"sha256:the-bytes-we-shipped"}')" 200

curl -s -o /tmp/_art "$B/api/application-versions/$VER/artifacts"
check "a tag pushed over is reported" "$(kindf image state)" DIVERGED
check "and the accepted digest is not quietly corrected" \
  "$(kindf image acceptedDigest)" "sha256:the-bytes-we-shipped"
check "while the repository's own answer is shown too" "$(kindf image digest)" "sha256:eee"

check "withdraw an acceptance made in error" \
  "$(code DELETE "$B/api/application-versions/$VER/artifacts/image/accepted-digest" "")" 204

curl -s -o /tmp/_art "$B/api/application-versions/$VER/artifacts"
check "nothing to have drifted from, so no divergence is claimed" "$(kindf image state)" PRESENT

echo
echo "--- what a release document prints (FR-086, NFR-025) ---"

check "create a Release Pack for this version" "$(code POST $B/api/release-packs \
  "{\"name\":\"Artifact Release $SUFFIX\",\"description\":\"artifact acceptance\"}")" 201
PACK=$(jsonf "['id']")
check "add the version to it" "$(code POST "$B/api/release-packs/$PACK/versions/$VER" "")" 200

curl -s "$B/api/release-packs/$PACK/documentation/markdown" > /tmp/_art-doc-1.md
if grep -qF "sha256:ccc" /tmp/_art-doc-1.md; then
  ok "the document prints the digest somebody accepted"
else
  bad "the accepted digest is missing from the document"
fi
if grep -qF "helm-local/api-2.5.0-abc1234.tgz" /tmp/_art-doc-1.md; then
  ok "and the coordinate it was found at, so the digest is followable"
else
  bad "the coordinate is missing from the document"
fi

# The whole reason a digest is accepted rather than read. A document that
# consulted the repository would stop regenerating identically the day a tag was
# pushed over - and one was pushed over above, on the image.
if grep -qF "sha256:the-bytes-we-shipped" /tmp/_art-doc-1.md; then
  bad "the document printed a withdrawn acceptance"
else
  ok "a withdrawn acceptance is not printed"
fi
if grep -qF "sha256:eee" /tmp/_art-doc-1.md; then
  bad "the document printed what the repository says now rather than what was accepted"
else
  ok "the document prints nothing the repository merely reports"
fi

curl -s "$B/api/release-packs/$PACK/documentation/markdown" > /tmp/_art-doc-2.md
if diff -q /tmp/_art-doc-1.md /tmp/_art-doc-2.md >/dev/null; then
  ok "regenerating the document produces identical bytes (NFR-025)"
else
  bad "the document is not reproducible"
fi

echo
echo "--- connection test (FR-061) — reads, never writes ---"

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' \
  "$B/api/artifacts/connection-test?connectorId=artifactory&system=$A")
check "test the repository" "$STATUS" 200
check "it answers" "$(jsonf "['reachable']")" True

STATUS=$(curl -s -o /tmp/_art -w '%{http_code}' \
  "$B/api/artifacts/connection-test?connectorId=nexus&system=$A")
check "a Connector that is not installed is reported, not thrown" "$(jsonf "['reachable']")" False

echo
echo "=============================================================="
echo " READ-ONLY (ADR-001, CM-01) — observed from the far side of the socket"
echo "=============================================================="

METHODS=$(curl -s "$A/methods")
if echo "$METHODS" | python3 -c "import json,sys;m=json.load(sys.stdin);sys.exit(0 if all(r.startswith('GET ') for r in m) else 1)"; then
  ok "the repository saw nothing but GET"
else
  bad "the repository saw a method other than GET: $METHODS"
fi

# ADR-021 turned down AQL because its search is a POST. This is where that decision is enforced
# rather than merely written down.
if echo "$METHODS" | grep -q "/search"; then
  bad "the Connector reached for a search endpoint"
else
  ok "no search endpoint was asked for"
fi

echo
echo "--- nothing was stored (FR-084) ---"

# There is no artifact table in the schema and none is meant to be. The evidence available from
# outside is that a second confirmation asks the repository again rather than answering from
# something Tower kept.
BEFORE=$(curl -s "$A/methods" | python3 -c "import json,sys;print(len(json.load(sys.stdin)))")
curl -s -o /dev/null "$B/api/application-versions/$VER/artifacts"
AFTER=$(curl -s "$A/methods" | python3 -c "import json,sys;print(len(json.load(sys.stdin)))")
if [ "$AFTER" -gt "$BEFORE" ]; then
  ok "a second confirmation asks the repository again rather than answering from a cache"
else
  bad "the second confirmation answered without asking, so something was stored"
fi

echo
echo "--- cleanup ---"
check "withdraw the remaining acceptance" \
  "$(code DELETE "$B/api/application-versions/$VER/artifacts/chart/accepted-digest" "")" 204
check "unbind the chart template" \
  "$(code DELETE "$B/api/bindings/artifact-coordinates/$APP/chart?connectorId=artifactory" "")" 204
check "unbind the image template" \
  "$(code DELETE "$B/api/bindings/artifact-coordinates/$APP/image?connectorId=artifactory" "")" 204
check "unbind the installer template" \
  "$(code DELETE "$B/api/bindings/artifact-coordinates/$APP/installer?connectorId=artifactory" "")" 204

echo
echo "=============================================================="
if [ "$FAIL" -eq 0 ]; then
  printf " RESULT: \033[32m%d passed\033[0m, %d failed\n" "$PASS" "$FAIL"
else
  printf " RESULT: %d passed, \033[31m%d failed\033[0m\n" "$PASS" "$FAIL"
fi
echo "=============================================================="
echo
echo " Nothing above has spoken to a real Artifactory. CHECKLIST.md keeps that check open."
echo

[ "$FAIL" -eq 0 ]
