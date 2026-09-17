#!/usr/bin/env bash
#
# Pre-QA gate for the AEM Brightcove Connector.
# Run this BEFORE transitioning a ticket to Ready for QA. It stops at the first
# failing step. Rationale + the recurring failures it guards against:
# wiki aem-connector-local-dev.md -> "Pre-QA gate".
#
# Generalised for the unified cloud/on-prem build (ONPREM-PARITY-PLAN.md §3
# Phase 1): runs the same four checks against either or both AEM instances.
# Default behaviour probes both instances and gates on whichever answer.
#
#   ./tests/e2e/pre-qa-gate.sh [options]
#
#   --platform cloud|onprem|both   Which instance(s) to gate. Default: probe
#                                   both URLs and use "both" if both answer
#                                   /system/console/bundles.json, else
#                                   whichever one answers. Error if neither.
#   --base <ref>                   Git ref to diff current/** against for the
#                                   version-bump check. Default: $PARITY_BASE_REF
#                                   if set, else origin/cloud-master. (The base
#                                   branch is renamed to `main` in Phase 5.)
#   --aem-cloud <url>               Cloud AEM instance. Default http://localhost:4502
#   --aem-onprem <url>              On-prem AEM instance. Default http://localhost:4602
#   --skip-build                   Skip the mvn build+install step (assume the
#                                   instance(s) already run the code to gate).
#   --dry-run                      Print the resolved plan and exit; run nothing.
#   --help                         Show this help and exit.
#
# Env overrides: AEM_AUTH (default admin:admin), JAVA_HOME, PARITY_BASE_REF,
#   PARITY_MVN_FLAGS_ONPREM / PARITY_MVN_FLAGS_CLOUD: extra Maven flags per platform.
#   ⚠️ The local on-prem test bed is AEM 6.5.0 GA while the release floor is 6.5 LTS
#   (uber-jar 6.5.22), so to gate against the LOCAL instance compile for its floor:
#   PARITY_MVN_FLAGS_ONPREM="-Daem.uber.version=6.5.0 -Daem.uber.classifier=apis -Daem.uber.jackson.version=2.9.5"
#   Without it the -prem bundle imports jackson [2.16,3) and will not resolve on 6.5.0.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AUTH="${AEM_AUTH:-admin:admin}"
# AEM bundles need Java 11; find it portably (override with JAVA_HOME).
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 11 2>/dev/null || true)}"
PW="$ROOT/tests/e2e/node_modules/.bin/playwright"   # NOT npx (broken in the nvm/Node-22 env)

step() { echo; echo "── $1"; }
fail() { echo "❌ PRE-QA GATE FAILED: $1" >&2; exit 1; }

usage() {
  sed -n '2,29p' "$0" | sed 's/^# \{0,1\}//'
}

# ---- argument parsing (POSIX-ish; macOS bash 3.2 compatible: no assoc arrays) ----
PLATFORM_ARG=""
BASE_REF="${PARITY_BASE_REF:-origin/cloud-master}"
AEM_CLOUD_URL="http://localhost:4502"
AEM_ONPREM_URL="http://localhost:4602"
SKIP_BUILD=0
DRY_RUN=0

while [ $# -gt 0 ]; do
  case "$1" in
    --platform)
      [ $# -ge 2 ] || fail "--platform requires a value (cloud|onprem|both)"
      PLATFORM_ARG="$2"; shift 2 ;;
    --base)
      [ $# -ge 2 ] || fail "--base requires a value"
      BASE_REF="$2"; shift 2 ;;
    --aem-cloud)
      [ $# -ge 2 ] || fail "--aem-cloud requires a value"
      AEM_CLOUD_URL="$2"; shift 2 ;;
    --aem-onprem)
      [ $# -ge 2 ] || fail "--aem-onprem requires a value"
      AEM_ONPREM_URL="$2"; shift 2 ;;
    --skip-build)
      SKIP_BUILD=1; shift ;;
    --dry-run)
      DRY_RUN=1; shift ;;
    --help|-h)
      usage; exit 0 ;;
    *)
      usage
      fail "unknown argument: $1" ;;
  esac
done

case "$PLATFORM_ARG" in
  ""|cloud|onprem|both) : ;;
  *) fail "--platform must be one of cloud|onprem|both (got: $PLATFORM_ARG)" ;;
esac

# ---- resolve which port a URL uses (for -Daem.port) ----
port_of() {
  # $1 = URL. Strips scheme://host: leaving the port, or 80/443 if absent.
  no_scheme="${1#*://}"
  no_path="${no_scheme%%/*}"
  case "$no_path" in
    *:*) echo "${no_path##*:}" ;;
    *) case "$1" in https://*) echo 443 ;; *) echo 80 ;; esac ;;
  esac
}

# ---- probe whether an instance answers ----
probe() {
  # $1 = URL. Echoes 1 if reachable, 0 otherwise. Never fails the script.
  code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' -u "$AUTH" "$1/system/console/bundles.json" 2>/dev/null || echo "000")
  if [ "$code" = "200" ]; then echo 1; else echo 0; fi
}

# ---- resolve the platform list ----
PLATFORMS=""
if [ -n "$PLATFORM_ARG" ]; then
  PLATFORMS="$PLATFORM_ARG"
else
  cloud_up=$(probe "$AEM_CLOUD_URL")
  onprem_up=$(probe "$AEM_ONPREM_URL")
  if [ "$cloud_up" = "1" ] && [ "$onprem_up" = "1" ]; then
    PLATFORMS="both"
  elif [ "$cloud_up" = "1" ]; then
    PLATFORMS="cloud"
    echo "   (on-prem $AEM_ONPREM_URL not reachable; gating cloud only)"
  elif [ "$onprem_up" = "1" ]; then
    PLATFORMS="onprem"
    echo "   (cloud $AEM_CLOUD_URL not reachable; gating on-prem only)"
  else
    fail "no AEM instance reachable at $AEM_CLOUD_URL or $AEM_ONPREM_URL — start one or pass --platform with --aem-cloud/--aem-onprem"
  fi
fi

if [ "$PLATFORMS" = "both" ]; then
  PLATFORM_LIST="cloud onprem"
else
  PLATFORM_LIST="$PLATFORMS"
fi

RUN_DATE="$(date +%Y-%m-%d)"

# ---- per-platform config lookups (no assoc arrays: case statements) ----
url_for() {
  case "$1" in
    cloud) echo "$AEM_CLOUD_URL" ;;
    onprem) echo "$AEM_ONPREM_URL" ;;
  esac
}
maven_platform_flag_for() {
  case "$1" in
    onprem) echo "-Daem.platform=onprem ${PARITY_MVN_FLAGS_ONPREM:-}" ;;
    *) echo "" ;;
  esac
}

if [ "$DRY_RUN" = "1" ]; then
  echo "PRE-QA GATE — dry run"
  echo "  base ref:      $BASE_REF"
  echo "  platforms:     $PLATFORM_LIST"
  for p in $PLATFORM_LIST; do
    url=$(url_for "$p")
    port=$(port_of "$url")
    mflag=$(maven_platform_flag_for "$p")
    out="$ROOT/tests/parity/runs/$RUN_DATE/gate-$p-<pomversion>.json"
    echo
    echo "  [$p]"
    echo "    url:          $url"
    echo "    build:        $([ "$SKIP_BUILD" = "1" ] && echo "SKIPPED" || echo "mvn clean install -PautoInstallPackage ${mflag:+$mflag }-Daem.port=$port -q (in current/)")"
    echo "    bundle check: brightcove.core Active, version-matched, single instance"
    echo "    content check: /apps/brightcove/clientlibs/clientlib-tools/js.txt has brcTransport.js, not com.iskitz"
    echo "    e2e:          AEM_BASE=$url, JSON report -> $out"
  done
  exit 0
fi

# ---- version-bump check (unchanged logic; platform-independent, computed once) ----
git -C "$ROOT" fetch origin --quiet 2>/dev/null || true
changed=$(git -C "$ROOT" diff --name-only "$BASE_REF" -- current/ 2>/dev/null | grep -v 'pom.xml' | grep -c . || true)
ver_local=$(grep -m1 -oE '<version>[^<]+' "$ROOT/current/pom.xml" | sed 's/<version>//')
ver_base=$(git -C "$ROOT" show "$BASE_REF:current/pom.xml" 2>/dev/null | grep -m1 -oE '<version>[^<]+' | sed 's/<version>//' || echo "")

run_platform() {
  platform="$1"
  url=$(url_for "$platform")
  port=$(port_of "$url")
  mflag=$(maven_platform_flag_for "$platform")

  echo
  echo "════ platform: $platform ($url) ════"

  # 1. Version-bump check — current/** changed but the pom version didn't.
  #    AEM/OSGi won't refresh a same-version bundle, so QA would test stale code.
  step "1/4 [$platform] version bump"
  if [ "${changed:-0}" -gt 0 ] && [ "$ver_local" = "$ver_base" ]; then
    fail "current/** changed ($changed files) but version is still $ver_local — bump it, or AEM won't load the new code."
  fi
  echo "   version $ver_local (base ${ver_base:-?}); $changed changed source files"

  # 2. Build + install to local AEM.
  step "2/4 [$platform] build + install ($ver_local)"
  if [ "$SKIP_BUILD" = "1" ]; then
    echo "   skipped (--skip-build)"
  else
    [ -n "$JAVA_HOME" ] || fail "no Java 11 (set JAVA_HOME)"
    ( cd "$ROOT/current" && mvn clean install -PautoInstallPackage $mflag -Daem.port="$port" -q ) \
      || fail "mvn build/install ($platform)"
  fi

  # 3. Confirm the deployed bundle is the new version AND Active (not a cached
  #    old build), and that there is exactly one brightcove.core bundle
  #    (Phase 0 trap: an orphaned duplicate co-Active bundle broke the
  #    accounts servlet after an uninstall without refreshPackages).
  step "3/4 [$platform] deployed bundle"
  sleep 3
  # curl is decoupled from the python pipe on purpose: under `set -o pipefail`
  # a failed curl (connection refused) still lets python exit 0 (it handles
  # empty stdin itself), and pipefail then reports the pipeline as failed
  # anyway, which used to make the `|| echo UNREACHABLE` fallback fire on top
  # of python's own "UNREACHABLE" line and duplicate it. Capture curl's raw
  # output first so there is exactly one pipe, and it never fails.
  raw_bundles=$(curl -s -u "$AUTH" "$url/system/console/bundles.json" 2>/dev/null) || raw_bundles=""
  bundle_report=$(printf '%s' "$raw_bundles" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print('UNREACHABLE')
    sys.exit(0)
bundles = [b for b in d.get('data', []) if b.get('symbolicName') == 'brightcove.core']
print(len(bundles))
for b in bundles:
    print(b.get('id'), b.get('version'), b.get('state'))
")

  if [ "$bundle_report" = "UNREACHABLE" ]; then
    fail "$platform: could not read $url/system/console/bundles.json"
  fi

  count=$(echo "$bundle_report" | head -n1)
  echo "   brightcove.core bundle count: $count"
  echo "$bundle_report" | tail -n +2 | while read -r line; do
    [ -n "$line" ] && echo "     - $line"
  done

  if [ "${count:-0}" -eq 0 ]; then
    fail "$platform: no brightcove.core bundle installed"
  fi
  if [ "${count:-0}" -gt 1 ]; then
    fail "$platform: $count brightcove.core bundles present (duplicate-bundle trap — uninstall the stale one and refreshPackages)"
  fi

  active=$(echo "$bundle_report" | tail -n +2 | head -n1)
  echo "   brightcove.core: $active"
  echo "$active" | grep -q "$ver_local" || fail "$platform: deployed bundle is not $ver_local (got: $active) — clientlib/bundle cache served stale code."
  echo "$active" | grep -qi Active || fail "$platform: brightcove.core is not Active (got: $active)."

  # 4a. Content-package check: the on-prem 6.0.12 clientlib shipped the
  #     legacy com.iskitz.ajile vendor bundle; the unified source ships
  #     brcTransport.js instead (BGS-1706). Confirm the deployed clientlib
  #     actually has the new file list, not a stale/mismatched package.
  step "4/4 [$platform] content package + e2e"
  js_txt=$(curl -s -u "$AUTH" "$url/apps/brightcove/clientlibs/clientlib-tools/js.txt" 2>/dev/null || echo "")
  echo "$js_txt" | grep -q 'brcTransport.js' || fail "$platform: clientlib-tools/js.txt does not list brcTransport.js (got: $(echo "$js_txt" | tr '\n' ' '))"
  echo "$js_txt" | grep -qi 'iskitz' && fail "$platform: clientlib-tools/js.txt still references com.iskitz (legacy ajile vendor bundle)"
  echo "   clientlib-tools/js.txt: brcTransport.js present, no com.iskitz"

  # 4b. Run the committed e2e/visual harness — the actual gate.
  [ -x "$PW" ] || fail "playwright not installed (cd tests/e2e && npm install && node_modules/.bin/playwright install chromium)"
  out_dir="$ROOT/tests/parity/runs/$RUN_DATE"
  mkdir -p "$out_dir"
  out_json="$out_dir/gate-$platform-$ver_local.json"
  ( cd "$ROOT/tests/e2e" && AEM_BASE="$url" PLAYWRIGHT_JSON_OUTPUT_NAME="$out_json" "$PW" test --project=chromium --reporter=list,json ) \
    || fail "e2e specs ($platform); report: $out_json"
  echo "   e2e report: $out_json"
}

for p in $PLATFORM_LIST; do
  run_platform "$p"
done

echo
echo "🎉 PRE-QA GATE PASSED for $ver_local on: $PLATFORM_LIST — safe to transition to Ready for QA."
echo "   Still verify by hand: visual diff vs Figma for any UI change, and"
echo "   environment/account parity (account feature flags, data scale, scrollbar style)."
