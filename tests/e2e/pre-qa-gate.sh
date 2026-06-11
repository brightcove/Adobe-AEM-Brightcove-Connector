#!/usr/bin/env bash
#
# Pre-QA gate for the AEM Brightcove Connector.
# Run this BEFORE transitioning a ticket to Ready for QA. It stops at the first
# failing step. Rationale + the recurring failures it guards against:
# wiki aem-connector-local-dev.md -> "Pre-QA gate".
#
#   ./tests/e2e/pre-qa-gate.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AEM="${AEM_BASE:-http://localhost:4502}"
AUTH="${AEM_AUTH:-admin:admin}"
# AEM bundles need Java 11; find it portably (override with JAVA_HOME).
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 11 2>/dev/null || true)}"
PW="$ROOT/tests/e2e/node_modules/.bin/playwright"   # NOT npx (broken in the nvm/Node-22 env)

step() { echo; echo "── $1"; }
fail() { echo "❌ PRE-QA GATE FAILED: $1" >&2; exit 1; }

# 1. Version-bump check — current/** changed but the pom version didn't.
#    AEM/OSGi won't refresh a same-version bundle, so QA would test stale code.
step "1/4 version bump"
git -C "$ROOT" fetch origin --quiet 2>/dev/null || true
changed=$(git -C "$ROOT" diff --name-only origin/cloud-master -- current/ 2>/dev/null | grep -v 'pom.xml' | grep -c . || true)
ver_local=$(grep -m1 -oE '<version>[^<]+' "$ROOT/current/pom.xml" | sed 's/<version>//')
ver_base=$(git -C "$ROOT" show origin/cloud-master:current/pom.xml 2>/dev/null | grep -m1 -oE '<version>[^<]+' | sed 's/<version>//' || echo "")
if [ "${changed:-0}" -gt 0 ] && [ "$ver_local" = "$ver_base" ]; then
  fail "current/** changed ($changed files) but version is still $ver_local — bump it, or AEM won't load the new code."
fi
echo "   version $ver_local (base ${ver_base:-?}); $changed changed source files"

# 2. Build + install to local AEM.
step "2/4 build + install ($ver_local)"
[ -n "$JAVA_HOME" ] || fail "no Java 11 (set JAVA_HOME)"
( cd "$ROOT/current" && mvn clean install -PautoInstallPackage -Daem.port="${AEM##*:}" -q ) || fail "mvn build/install"

# 3. Confirm the deployed bundle is the new version AND Active (not a cached old build).
step "3/4 deployed bundle"
sleep 3
active=$(curl -s -u "$AUTH" "$AEM/system/console/bundles.json" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(next((b['version']+' '+b['state'] for b in d['data'] if b['symbolicName']=='brightcove.core'),'ABSENT'))" 2>/dev/null || echo "UNREACHABLE")
echo "   brightcove.core: $active"
echo "$active" | grep -q "$ver_local" || fail "deployed bundle is not $ver_local (got: $active) — clientlib/bundle cache served stale code."
echo "$active" | grep -qi Active || fail "brightcove.core is not Active (got: $active)."

# 4. Run the committed e2e/visual harness — the actual gate.
step "4/4 e2e specs"
[ -x "$PW" ] || fail "playwright not installed (cd tests/e2e && npm install && node_modules/.bin/playwright install chromium)"
( cd "$ROOT/tests/e2e" && "$PW" test --project=chromium ) || fail "e2e specs"

echo
echo "🎉 PRE-QA GATE PASSED for $ver_local — safe to transition to Ready for QA."
echo "   Still verify by hand: visual diff vs Figma for any UI change, and"
echo "   environment/account parity (account feature flags, data scale, scrollbar style)."
