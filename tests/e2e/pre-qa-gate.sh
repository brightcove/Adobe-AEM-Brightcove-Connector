#!/usr/bin/env bash
#
# Pre-QA gate for the AEM Brightcove Connector.
# Run this BEFORE transitioning a ticket to Ready for QA. It stops at the first
# failing step. Rationale + the recurring failures it guards against:
# wiki aem-connector-local-dev.md -> "Pre-QA gate".
#
# Stages per platform: unit -> (unit_java21) -> version_bump -> build -> deployed_bundle ->
# failed_components -> clientlib -> e2e -> e2e_live. Every stage ends PASS, FAIL or
# NOT_MEASURED (docs/testing.md). Exit 0 only for PASS, or NOT_MEASURED that an explicit
# flag waived (--allow-no-live, --skip-build).
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
#   --aem-cloud <url>               Cloud AEM instance. Default $BRC_GATE_AEM_CLOUD, else http://localhost:4502
#   --aem-onprem <url>              On-prem AEM instance. Default $BRC_GATE_AEM_ONPREM, else http://localhost:4602
#   --skip-build                   Skip the mvn build+install step (assume the
#                                   instance(s) already run the code to gate).
#   --allow-no-live                Permit a run WITHOUT the Video Cloud credentials
#                                   (BRIGHTCOVE_ACCOUNT_ID / BRIGHTCOVE_CLIENT_ID /
#                                   BRIGHTCOVE_CLIENT_SECRET). Without them every live
#                                   spec skips (measured: 59 passed / 25 skipped vs
#                                   74 / 10 with credentials), so by default the gate
#                                   FAILS when they are absent. With this flag the gate
#                                   reports live coverage as NOT MEASURED and ends with
#                                   "PASSED (live specs NOT MEASURED)", not "PASSED".
#   --unit-only                    Run ONLY the unit stage (mvn -pl core test per platform,
#                                   surefire + JaCoCo parsed). No AEM, no credentials, no
#                                   build/install: ~10s per platform, for gating a Java change.
#                                   Platform defaults to "both" (no probing) in this mode.
#   --java21                       Also run the unit tests forked onto a Java 21 JVM, as CI
#                                   does (surefire -Djvm), and verify from the surefire XML
#                                   that they really ran on 21. Needs JDK 21 (JAVA_HOME_21_X64
#                                   or /usr/libexec/java_home -v 21).
#   --json <path>                  Write the machine-readable result (git sha, branch, version,
#                                   AEM url + deployed bundle per platform, per-stage verdict
#                                   PASS / FAIL / NOT_MEASURED with reason, per-test results
#                                   for unit and e2e, coverage, DEFECT pin counts). Written on
#                                   failure too. Compare two with tests/gate/compare.js.
#   --baseline <version>           Gate a BASELINE release deployed with tests/gate/deploy-release.sh,
#                                   to feed tests/gate/compare.js. The deployed bundle must be
#                                   <version> (not the checkout's pom version); the unit stage,
#                                   version-bump check and clientlib check are not run as gates
#                                   (a release is judged by the current specs, which may fail on
#                                   it, so a non-zero exit is expected: read the --json). Implies
#                                   --skip-build.
#   --lock-timeout <secs>          Per-bed run lock (exclusive per AEM URL, held from the version
#                                   check to the end of e2e; docs/testing.md "Bed lock"). Wait this
#                                   long for another run on the same instance, default 1800, then
#                                   FAIL. --unit-only takes no lock. The wait is recorded in --json.
#   --dry-run                      Print the resolved plan and exit; run nothing, contact
#                                   no AEM. Also reports the live-spec status, and exits
#                                   non-zero when a real run would fail on it.
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
# shellcheck source=../gate/lib.sh
. "$ROOT/tests/gate/lib.sh"
AUTH="${AEM_AUTH:-admin:admin}"
# AEM bundles need Java 11, and CI's default unit job runs on it, so the build and the default
# unit run use a JDK 11 regardless of the ambient JAVA_HOME (an ambient 21 would make the
# "default" run and --java21 the same JVM). Search order: JAVA_HOME_11_X64 (CI), the ambient
# JAVA_HOME when it is 11, macOS java_home -v 11. None found: keep the ambient JAVA_HOME and say
# so; the unit reason line records the JVM the tests really ran on.
java11_home() {
  [ -n "${JAVA_HOME_11_X64:-}" ] && [ -x "$JAVA_HOME_11_X64/bin/java" ] && { echo "$JAVA_HOME_11_X64"; return; }
  if [ -n "${JAVA_HOME:-}" ] && grep -q 'JAVA_VERSION="11\.' "$JAVA_HOME/release" 2>/dev/null; then echo "$JAVA_HOME"; return; fi
  /usr/libexec/java_home -v 11 2>/dev/null || true
}
JAVA11="$(java11_home)"
if [ -n "$JAVA11" ]; then export JAVA_HOME="$JAVA11"; else echo "⚠️ no JDK 11 found; using ambient JAVA_HOME=${JAVA_HOME:-unset}" >&2; fi
PW="$ROOT/tests/e2e/node_modules/.bin/playwright"   # NOT npx (broken in the nvm/Node-22 env)

# ---- result bookkeeping: every stage lands in $WORK/stages.tsv, summarized by tests/gate/summarize.js ----
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"; gate_lock_release_all' EXIT
: > "$WORK/stages.tsv"; : > "$WORK/aem.tsv"; : > "$WORK/e2e.tsv"
JSON_OUT=""
CUR_PLATFORM=""; CUR_STAGE=""; MODE=full; JAVA21=0
ALLOW_NO_LIVE=0; MISSING_LIVE=""
PLATFORM_LIST=""; ver_local=""

# record <platform> <stage> <PASS|FAIL|NOT_MEASURED|PENDING> <reason> [waived]
record() { printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "${5:-}" >> "$WORK/stages.tsv"; }

# Build the summary (also prints the verdict table). Returns summarize.js's exit code.
SUMMARY_WRITTEN=0
finish() {
  SUMMARY_WRITTEN=1
  _out="$WORK/summary.json"; [ -z "$JSON_OUT" ] || _out="$JSON_OUT"
  sha=$(git -C "$ROOT" rev-parse --short=12 HEAD 2>/dev/null || echo "")
  branch=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  node "$ROOT/tests/gate/summarize.js" --work "$WORK" --out "$_out" --mode "$MODE" \
    --platforms "$PLATFORM_LIST" --java21 "$JAVA21" --allow-no-live "$ALLOW_NO_LIVE" \
    --live-missing "$MISSING_LIVE" --sha "$sha" --branch "$branch" --version "${BASELINE_VER:-$ver_local}" \
    --e2e-root "$ROOT/tests/e2e" \
    --lock-wait "$([ "$LOCK_TRIED" = "1" ] && echo "$GATE_LOCK_WAITED" || echo "")" --lock-beds "$LOCK_BEDS"
}

step() { echo; echo "── $1"; }
fail() {
  echo "❌ PRE-QA GATE FAILED: $1" >&2
  # A failure is recorded against its stage, or against "setup" when it happens outside any stage
  # (bad arguments, no AEM, no JDK), so --json always says which stage failed and why and a stale
  # PASS file can never survive. Skipped when finish already wrote the summary (unit/e2e failures).
  if [ "$SUMMARY_WRITTEN" = "0" ]; then
    record "${CUR_PLATFORM:-setup}" "${CUR_STAGE:-setup}" FAIL "$(printf '%s' "$1" | tr '\n\t' '  ' | cut -c1-400)"
    finish >&2 || true
  fi
  exit 1
}

usage() {
  sed -n '2,74p' "$0" | sed 's/^# \{0,1\}//'
}

# ---- argument parsing (POSIX-ish; macOS bash 3.2 compatible: no assoc arrays) ----
PLATFORM_ARG=""
BASE_REF="${PARITY_BASE_REF:-origin/cloud-master}"
# Defaults are the conventional ports; BRC_GATE_AEM_CLOUD / BRC_GATE_AEM_ONPREM override them (the
# --aem-* flags override those). A local on-prem bed on another port (e.g. 6.5 LTS) is set once there.
AEM_CLOUD_URL="${BRC_GATE_AEM_CLOUD:-http://localhost:4502}"
AEM_ONPREM_URL="${BRC_GATE_AEM_ONPREM:-http://localhost:4602}"
SKIP_BUILD=0
DRY_RUN=0
UNIT_ONLY=0
BASELINE_VER=""
LOCK_TIMEOUT=1800
LOCK_BEDS=""
LOCK_TRIED=0

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
    --json)
      [ $# -ge 2 ] || fail "--json requires a path"
      JSON_OUT="$2"; rm -f "$JSON_OUT"; shift 2 ;;   # never leave a previous run's verdict behind
    --lock-timeout)
      [ $# -ge 2 ] || fail "--lock-timeout requires seconds"
      LOCK_TIMEOUT="$2"; shift 2 ;;
    --unit-only)
      UNIT_ONLY=1; MODE=unit-only; shift ;;
    --java21)
      JAVA21=1; shift ;;
    --skip-build)
      SKIP_BUILD=1; shift ;;
    --baseline)
      [ $# -ge 2 ] || fail "--baseline requires a version"
      BASELINE_VER="$2"; SKIP_BUILD=1; shift 2
      case "$BASELINE_VER" in
        [0-6].*) fail "--baseline $BASELINE_VER is pre-7.x: that line ships a different bundle (com.coresecure.brightcove.cq5.brightcove-services) and is not a supported baseline. Use the 6.0.12 column of tests/parity/matrix.md." ;;
      esac ;;
    --allow-no-live)
      ALLOW_NO_LIVE=1; shift ;;
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
# A relative --json path must survive the later cd into current/ and tests/e2e.
case "$JSON_OUT" in ""|/*) : ;; *) JSON_OUT="$PWD/$JSON_OUT" ;; esac
[ -z "$JSON_OUT" ] || rm -f "$JSON_OUT"   # a stale PASS file must not survive an early exit

# ---- live (credential-dependent) specs ----
# Presence only: the values are never printed. Missing creds make every live spec skip,
# which Playwright reports as a pass, so absence is a gate failure unless opted out.
[ -n "${BRIGHTCOVE_ACCOUNT_ID:-}" ] || MISSING_LIVE="$MISSING_LIVE BRIGHTCOVE_ACCOUNT_ID"
[ -n "${BRIGHTCOVE_CLIENT_ID:-}" ] || MISSING_LIVE="$MISSING_LIVE BRIGHTCOVE_CLIENT_ID"
[ -n "${BRIGHTCOVE_CLIENT_SECRET:-}" ] || MISSING_LIVE="$MISSING_LIVE BRIGHTCOVE_CLIENT_SECRET"
MISSING_LIVE="${MISSING_LIVE# }"
LIVE_MEASURED=1
[ -z "$MISSING_LIVE" ] || LIVE_MEASURED=0
live_fail_msg() {
  echo "live specs did not run: $(echo "$MISSING_LIVE" | tr ' ' '/') not set. Every credential-dependent spec would skip and the run would look green. Source the account env file, or pass --allow-no-live to accept live coverage as NOT MEASURED."
}

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
elif [ "$UNIT_ONLY" = "1" ]; then
  PLATFORMS="both"   # no AEM involved, so nothing to probe
else
  cloud_up=$(probe "$AEM_CLOUD_URL")
  onprem_up=$(probe "$AEM_ONPREM_URL")
  if [ "$cloud_up" = "1" ] && [ "$onprem_up" = "1" ]; then
    PLATFORMS="both"
  elif [ "$cloud_up" = "1" ]; then
    PLATFORMS="cloud"
    echo "   (on-prem $AEM_ONPREM_URL not reachable; gating cloud only. If your on-prem bed is on another port, pass --aem-onprem <url> or set BRC_GATE_AEM_ONPREM)"
  elif [ "$onprem_up" = "1" ]; then
    PLATFORMS="onprem"
    echo "   (cloud $AEM_CLOUD_URL not reachable; gating on-prem only. If your cloud bed is on another port, pass --aem-cloud <url> or set BRC_GATE_AEM_CLOUD)"
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
# Unit tests compile against the platform API, so the on-prem profile flag alone (no
# PARITY_MVN_FLAGS_ONPREM, which is for deploying to a 6.5.0 GA test bed) is what CI uses.
unit_profile_for() {
  case "$1" in
    onprem) echo "-Daem.platform=onprem" ;;
    *) echo "" ;;
  esac
}
java21_home() {
  if [ -n "${JAVA_HOME_21_X64:-}" ]; then echo "$JAVA_HOME_21_X64"; return; fi
  /usr/libexec/java_home -v 21 2>/dev/null || true
}

ver_local=$(grep -m1 -oE '<version>[^<]+' "$ROOT/current/pom.xml" | sed 's/<version>//')
# The version the deployed bundle must report: the checkout's, or the baseline release's.
EXPECT_VER="${BASELINE_VER:-$ver_local}"

if [ "$DRY_RUN" = "1" ]; then
  echo "PRE-QA GATE — dry run"
  echo "  mode:          $MODE"
  echo "  base ref:      $BASE_REF"
  echo "  platforms:     $PLATFORM_LIST"
  echo "  json:          ${JSON_OUT:-<none>}"
  for p in $PLATFORM_LIST; do
    url=$(url_for "$p")
    port=$(port_of "$url")
    mflag=$(maven_platform_flag_for "$p")
    uflag=$(unit_profile_for "$p")
    out="$ROOT/tests/parity/runs/$RUN_DATE/gate-$p-$port-<pomversion>.json"
    echo
    echo "  [$p]"
    echo "    unit:         mvn ${uflag:+$uflag }-pl core clean test (in current/), surefire + JaCoCo parsed"
    if [ "$JAVA21" = "1" ]; then
      echo "    unit (java21): same, forked with -Djvm=<JDK 21>/bin/java ($([ -n "$(java21_home)" ] && echo "JDK 21 found" || echo "NO JDK 21 FOUND"))"
    fi
    [ "$UNIT_ONLY" = "1" ] && continue
    echo "    url:          $url"
    echo "    build:        $([ "$SKIP_BUILD" = "1" ] && echo "SKIPPED" || echo "mvn clean install -PautoInstallPackage ${mflag:+$mflag }-Daem.port=$port -q (in current/)")"
    echo "    bundle check: brightcove.core Active, version-matched, single instance"
    echo "    content check: /apps/brightcove/clientlibs/clientlib-tools/js.txt has brcTransport.js, not com.iskitz"
    echo "    e2e:          AEM_BASE=$url, JSON report -> $out"
  done
  echo
  if [ "$UNIT_ONLY" = "1" ]; then
    echo "  live specs:    not part of --unit-only"
  elif [ "$LIVE_MEASURED" = "1" ]; then
    echo "  live specs:    credentials present (BRIGHTCOVE_* set); live coverage will be measured"
  elif [ "$ALLOW_NO_LIVE" = "1" ]; then
    echo "  live specs:    NOT MEASURED (missing: $MISSING_LIVE); allowed by --allow-no-live, final line will read 'PASSED (live specs NOT MEASURED)'"
  else
    echo "  live specs:    WOULD FAIL, missing: $MISSING_LIVE (pass --allow-no-live to accept NOT MEASURED)"
    fail "$(live_fail_msg)"
  fi
  exit 0
fi

# Fail before the (slow) build, not after it.
if [ "$UNIT_ONLY" = "0" ] && [ "$LIVE_MEASURED" = "0" ] && [ "$ALLOW_NO_LIVE" = "0" ]; then
  CUR_PLATFORM="${PLATFORM_LIST%% *}"; CUR_STAGE=e2e_live
  fail "$(live_fail_msg)"
fi
if [ "$UNIT_ONLY" = "0" ] && [ "$LIVE_MEASURED" = "0" ]; then
  echo "⚠️ live specs NOT MEASURED (missing: $MISSING_LIVE); running with --allow-no-live"
fi

# ---- unit stage: mvn -pl core test, once per platform ----
# `clean` so the second platform's surefire/JaCoCo output never mixes with the first's.
# Artifacts are copied out to $WORK because the e2e build below runs `clean` again.
run_unit() {
  platform="$1"; stage="$2"; extra="$3"; dest="$4"
  CUR_PLATFORM="$platform"; CUR_STAGE=""   # a unit FAIL is judged from the artifacts, not recorded by fail()
  [ -n "${JAVA_HOME:-}" ] || fail "no Java 11 (set JAVA_HOME)"
  uflag=$(unit_profile_for "$platform")
  step "unit [$platform] ${extra:+(java21) }mvn ${uflag:+$uflag }-pl core clean test"
  rc=0
  ( cd "$ROOT/current" && mvn $uflag -pl core clean test $extra -q ) || rc=$?
  mkdir -p "$WORK/$dest"
  cp "$ROOT"/current/core/target/surefire-reports/TEST-*.xml "$WORK/$dest/" 2>/dev/null || true
  cp "$ROOT/current/core/target/site/jacoco/jacoco.xml" "$WORK/$dest/" 2>/dev/null || true
  record "$platform" "$stage" PENDING "exit=$rc"
  [ "$rc" = "0" ] || return 1
}

for p in $PLATFORM_LIST; do
  if [ -n "$BASELINE_VER" ]; then
    # The unit stage measures the checkout's source, which is not what is deployed.
    record "$p" unit NOT_MEASURED "baseline run: unit tests measure the checkout, not release $BASELINE_VER" waived
    [ "$JAVA21" = "0" ] || record "$p" unit_java21 NOT_MEASURED "baseline run" waived
    continue
  fi
  if ! run_unit "$p" unit "" "unit-$p"; then
    finish || true
    fail "unit tests ($p): see the table above and the surefire output"
  fi
  if [ "$JAVA21" = "1" ]; then
    j21=$(java21_home)
    [ -n "$j21" ] && [ -x "$j21/bin/java" ] || fail "--java21: no JDK 21 found (set JAVA_HOME_21_X64)"
    if ! run_unit "$p" unit_java21 "-Djvm=$j21/bin/java" "unit-$p-java21"; then
      finish || true
      fail "unit tests on Java 21 ($p): see the table above"
    fi
  fi
done
# Resolve the PENDING unit stages now so a clean pass reads as such in the log.
if [ "$UNIT_ONLY" = "1" ]; then
  echo
  rc=0; finish || rc=$?
  if [ "$rc" = "0" ]; then
    echo "🎉 UNIT GATE PASSED for $ver_local on: $PLATFORM_LIST (no AEM contacted; run the full gate before Ready for QA)."
    exit 0
  fi
  echo "❌ UNIT GATE did not pass (see the table above)." >&2
  exit 1
fi

# ---- bed lock: one run per AEM at a time, held to the end of the script (EXIT trap releases) ----
# Fixed order (cloud, then onprem) so two gates cannot deadlock. Taken after the unit stage:
# unit tests touch no AEM, so they never queue behind someone else's e2e.
step "bed lock"
LOCK_TRIED=1
CUR_PLATFORM="${PLATFORM_LIST%% *}"; CUR_STAGE=bed_lock
for p in cloud onprem; do
  case " $PLATFORM_LIST " in *" $p "*) : ;; *) continue ;; esac
  bed_url=$(url_for "$p")
  gate_lock_acquire "$bed_url" "$LOCK_TIMEOUT" "pre-qa-gate.sh --platform $PLATFORM_LIST (pid $$)" || fail "$GATE_LOCK_ERR"
  LOCK_BEDS="$LOCK_BEDS $bed_url"
done
LOCK_BEDS="${LOCK_BEDS# }"
echo "   holding the bed lock for: $LOCK_BEDS (waited ${GATE_LOCK_WAITED}s)"
for p in $PLATFORM_LIST; do record "$p" bed_lock PASS "held for $(url_for "$p"); waited ${GATE_LOCK_WAITED}s in total"; done
CUR_STAGE=""

# ---- version-bump check (unchanged logic; platform-independent, computed once) ----
git -C "$ROOT" fetch origin --quiet 2>/dev/null || true
# Unit-test sources never ship in the bundle, so a test-only diff needs no version bump.
changed=$(git -C "$ROOT" diff --name-only "$BASE_REF" -- current/ ':(exclude,glob)current/**/src/test/**' 2>/dev/null | grep -v 'pom.xml' | grep -c . || true)
ver_base=$(git -C "$ROOT" show "$BASE_REF:current/pom.xml" 2>/dev/null | grep -m1 -oE '<version>[^<]+' | sed 's/<version>//' || echo "")

run_platform() {
  platform="$1"
  url=$(url_for "$platform")
  port=$(port_of "$url")
  mflag=$(maven_platform_flag_for "$platform")
  CUR_PLATFORM="$platform"; CUR_STAGE=""

  echo
  echo "════ platform: $platform ($url) ════"
  printf '%s\t%s\t\t\n' "$platform" "$url" >> "$WORK/aem.tsv"

  # 1. Version-bump check — current/** changed but the pom version didn't.
  #    AEM/OSGi won't refresh a same-version bundle, so QA would test stale code.
  step "1/4 [$platform] version bump"
  CUR_STAGE=version_bump
  if [ -n "$BASELINE_VER" ]; then
    echo "   skipped (--baseline $BASELINE_VER): the source version is not what is deployed"
    record "$platform" version_bump NOT_MEASURED "baseline run" waived
  else
    if [ "${changed:-0}" -gt 0 ] && [ "$ver_local" = "$ver_base" ]; then
      fail "current/** changed ($changed files) but version is still $ver_local — bump it, or AEM won't load the new code."
    fi
    echo "   version $ver_local (base ${ver_base:-?}); $changed changed source files"
    if [ -z "$ver_base" ]; then
      record "$platform" version_bump NOT_MEASURED "base ref $BASE_REF has no current/pom.xml, so the bump could not be compared"
    else
      record "$platform" version_bump PASS "version $ver_local, base $ver_base, $changed changed source files"
    fi
  fi

  # 2. Build + install to local AEM.
  step "2/4 [$platform] build + install ($ver_local)"
  CUR_STAGE=build
  if [ "$SKIP_BUILD" = "1" ]; then
    echo "   skipped (--skip-build)"
    record "$platform" build NOT_MEASURED "skipped (--skip-build); deployed_bundle still proves which version runs" waived
  else
    [ -n "${JAVA_HOME:-}" ] || fail "no Java 11 (set JAVA_HOME)"
    ( cd "$ROOT/current" && mvn clean install -PautoInstallPackage $mflag -Daem.port="$port" -q ) \
      || fail "mvn build/install ($platform)"
    record "$platform" build PASS "mvn clean install -PautoInstallPackage"
  fi

  # 3. Confirm the deployed bundle is the new version AND Active (not a cached
  #    old build), and that there is exactly one brightcove.core bundle
  #    (Phase 0 trap: an orphaned duplicate co-Active bundle broke the
  #    accounts servlet after an uninstall without refreshPackages).
  #    The wait/judge logic is shared with tests/gate/deploy-release.sh (tests/gate/lib.sh).
  step "3/4 [$platform] deployed bundle"
  CUR_STAGE=deployed_bundle
  gate_bundle_wait "$url" "$AUTH" "${BUNDLE_WAIT_SECS:-120}" "$EXPECT_VER"
  [ "$BUNDLE_WAITED" -gt 0 ] && echo "   (waited ${BUNDLE_WAITED}s for the installer to settle)"
  gate_bundle_check "$EXPECT_VER" "$platform" "$url" || true
  echo "   brightcove.core bundle count: ${BUNDLE_COUNT:-0}"
  echo "$BUNDLE_REPORT" | tail -n +2 | while read -r line; do
    [ -n "$line" ] && echo "     - $line"
  done
  if [ -n "$BUNDLE_VERSION" ]; then
    printf '%s\t%s\t%s\t%s\n' "$platform" "$url" "$BUNDLE_VERSION" "$BUNDLE_STATE" >> "$WORK/aem.tsv"
  fi
  [ -z "$BUNDLE_ERR" ] || fail "$BUNDLE_ERR"
  echo "   brightcove.core: $BUNDLE_ACTIVE"
  record "$platform" deployed_bundle PASS "brightcove.core $BUNDLE_VERSION $BUNDLE_STATE, single instance"

  # 3b. No DS component anywhere may be in "failed activation" (trap and recovery in lib.sh).
  CUR_STAGE=failed_components
  failed_components=$(gate_failed_components "$url" "$AUTH")
  if [ "$failed_components" = "UNREADABLE" ]; then
    # Not measured, and said so: never silently treated as clean.
    echo "   ⚠️ could not read the component list; failed-activation check NOT MEASURED"
    record "$platform" failed_components NOT_MEASURED "components.json unreadable"
  elif [ -n "$failed_components" ]; then
    echo "$failed_components" | while read -r line; do echo "     ✗ $line"; done
    fail "$platform: OSGi components in failed activation (above). A package install can
   cascade an OSGi refresh into Sling's own bundles; if sightly/scripting components are
   listed, every HTL data-sly-use script falls back to its resourceSuperType and pages
   render empty with HTTP 200. Recover with a stop/start of the owning bundle:
     curl -u $AUTH -X POST -d action=stop  $url/system/console/bundles/<id>
     curl -u $AUTH -X POST -d action=start $url/system/console/bundles/<id>"
  else
    echo "   OSGi components in failed activation: 0"
    record "$platform" failed_components PASS "0 components in failed activation"
  fi

  # 4a. Content-package check: the on-prem 6.0.12 clientlib shipped the
  #     legacy com.iskitz.ajile vendor bundle; the unified source ships
  #     brcTransport.js instead (BGS-1706). Confirm the deployed clientlib
  #     actually has the new file list, not a stale/mismatched package.
  step "4/4 [$platform] content package + e2e"
  CUR_STAGE=clientlib
  js_txt=$(curl -s -u "$AUTH" "$url/apps/brightcove/clientlibs/clientlib-tools/js.txt" 2>/dev/null || echo "")
  if [ -n "$BASELINE_VER" ]; then
    # An older release may legitimately ship the legacy vendor bundle; record, do not stop the run.
    if echo "$js_txt" | grep -q 'brcTransport.js' && ! echo "$js_txt" | grep -qi 'iskitz'; then
      record "$platform" clientlib PASS "brcTransport.js listed, no com.iskitz"
    else
      record "$platform" clientlib NOT_MEASURED "baseline $BASELINE_VER ships a different clientlib file list (informational)" waived
    fi
  else
    echo "$js_txt" | grep -q 'brcTransport.js' || fail "$platform: clientlib-tools/js.txt does not list brcTransport.js (got: $(echo "$js_txt" | tr '\n' ' '))"
    echo "$js_txt" | grep -qi 'iskitz' && fail "$platform: clientlib-tools/js.txt still references com.iskitz (legacy ajile vendor bundle)"
    echo "   clientlib-tools/js.txt: brcTransport.js present, no com.iskitz"
    record "$platform" clientlib PASS "brcTransport.js listed, no com.iskitz"
  fi

  # 4b. Run the committed e2e/visual harness — the actual gate. Specs are discovered by
  #     Playwright (testDir ./specs), never listed here, so new specs join automatically.
  CUR_STAGE=""
  [ -x "$PW" ] || { CUR_STAGE=e2e; fail "playwright not installed (cd tests/e2e && npm install && node_modules/.bin/playwright install chromium)"; }
  # A leftover swapped proxy (crashed run) makes every live spec fail silently: refuse to start.
  CUR_STAGE=proxy_config
  proxy_out=$(AEM_BASE="$url" node "$ROOT/tests/gate/check-proxy.js" 2>&1) && proxy_rc=0 || proxy_rc=$?
  echo "   proxy preflight: $proxy_out"
  case "$proxy_rc" in
    0) record "$platform" proxy_config PASS "$proxy_out" ;;
    10) fail "$platform: environment: $proxy_out" ;;
    *) record "$platform" proxy_config NOT_MEASURED "$proxy_out" ;;
  esac
  CUR_STAGE=""
  out_dir="$ROOT/tests/parity/runs/$RUN_DATE"
  mkdir -p "$out_dir"
  out_json="$out_dir/gate-$platform-$port-$EXPECT_VER.json"
  rm -f "$out_json"
  printf '%s\t%s\n' "$platform" "$out_json" >> "$WORK/e2e.tsv"
  rc=0
  ( cd "$ROOT/tests/e2e" && AEM_BASE="$url" PLAYWRIGHT_JSON_OUTPUT_NAME="$out_json" "$PW" test --project=chromium --reporter=list,json ) || rc=$?
  record "$platform" e2e PENDING "exit=$rc"
  if [ "$LIVE_MEASURED" = "1" ]; then
    # Credentials only make the live specs eligible; summarize.js derives the verdict from the
    # live-tagged (markLive) test results in the Playwright report.
    record "$platform" e2e_live PENDING "credentials present"
  else
    record "$platform" e2e_live NOT_MEASURED "missing: $MISSING_LIVE; every credential-dependent spec skipped"
  fi
  echo "   e2e report: $out_json"
  if [ "$rc" != "0" ]; then
    # A baseline run is expected to have failing specs (older code): record it and measure the next
    # platform. The final exit code still reflects the failed stage, and the JSON covers every platform.
    if [ -n "$BASELINE_VER" ]; then
      echo "   e2e exited $rc on $platform (expected for an older baseline); continuing"
      return 0
    fi
    finish || true
    fail "e2e specs ($platform); report: $out_json"
  fi
}

for p in $PLATFORM_LIST; do
  run_platform "$p"
done

echo
rc=0; finish || rc=$?
if [ -n "$BASELINE_VER" ]; then
  echo "Baseline $BASELINE_VER measured on: $PLATFORM_LIST (exit code is not the verdict here; compare the JSON with tests/gate/compare.js)."
  exit "$rc"
fi
if [ "$rc" != "0" ]; then
  echo "❌ PRE-QA GATE did not pass (see the table above)." >&2
  exit 1
fi
if [ "$LIVE_MEASURED" = "1" ]; then
  echo "🎉 PRE-QA GATE PASSED for $ver_local on: $PLATFORM_LIST — safe to transition to Ready for QA."
else
  echo "⚠️ live coverage: NOT MEASURED (missing: $MISSING_LIVE). The credential-dependent specs skipped."
  echo "🎉 PRE-QA GATE PASSED (live specs NOT MEASURED) for $ver_local on: $PLATFORM_LIST — not sufficient on its own for Ready for QA."
fi
echo "   Still verify by hand: visual diff vs Figma for any UI change, and"
echo "   environment/account parity (account feature flags, data scale, scrollbar style)."
