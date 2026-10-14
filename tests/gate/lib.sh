#!/usr/bin/env bash
# Shared shell helpers for the connector gate (tests/e2e/pre-qa-gate.sh) and
# tests/gate/deploy-release.sh. Source it; it defines functions only and never exits.
# Context: docs/testing.md (the deployed-bundle and failed-activation traps).
#
# macOS bash 3.2 compatible: no associative arrays, no ${var,,}.

# gate_bundle_wait <aem-url> <user:pass> [max-seconds]
#   Polls /system/console/bundles.json until exactly one brightcove.core bundle is
#   present AND Active, or the wait runs out. Sets BUNDLE_REPORT (first line: count or
#   UNREACHABLE; then one "id version state" line per bundle) and BUNDLE_WAITED (seconds).
#
#   ⚠️ The JCR installer swaps bundles asynchronously after the package manager
#   returns: on a version UPGRADE the old bundle is gone and the new one not yet Active
#   for several seconds, and a single read there reported "no brightcove.core bundle
#   installed" (2026-09-17), so this polls instead of reading once.
gate_bundle_wait() {
  _url="$1"; _auth="$2"; _max="${3:-120}"
  BUNDLE_WAITED=0
  BUNDLE_REPORT=""
  while :; do
    # curl is decoupled from the python pipe on purpose: under `set -o pipefail` a failed
    # curl (connection refused) still lets python exit 0 (it handles empty stdin itself),
    # and pipefail then reports the pipeline as failed anyway, which made the
    # `|| echo UNREACHABLE` fallback fire on top of python's own "UNREACHABLE" line.
    # Capture curl's raw output first so there is exactly one pipe, and it never fails.
    _raw=$(curl -s -u "$_auth" "$_url/system/console/bundles.json" 2>/dev/null) || _raw=""
    BUNDLE_REPORT=$(printf '%s' "$_raw" | python3 -c "
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
    _settled=$(echo "$BUNDLE_REPORT" | python3 -c "
import sys
lines = sys.stdin.read().splitlines()
if not lines or lines[0] == 'UNREACHABLE': print('no'); sys.exit(0)
rows = [l.split() for l in lines[1:] if l.strip()]
print('yes' if lines[0] == '1' and rows and rows[0][2] == 'Active' else 'no')
")
    [ "$_settled" = "yes" ] && break
    [ "$BUNDLE_WAITED" -ge "$_max" ] && break
    sleep 5; BUNDLE_WAITED=$((BUNDLE_WAITED + 5))
  done
}

# gate_bundle_check <expected-version> <platform-label> [url]
#   Judges BUNDLE_REPORT from gate_bundle_wait. Returns 0 when there is exactly one
#   brightcove.core, it is the expected version and Active. Otherwise returns 1 with
#   BUNDLE_ERR set. Always sets BUNDLE_COUNT and BUNDLE_ACTIVE ("id version state") and
#   BUNDLE_VERSION / BUNDLE_STATE when a bundle was read.
gate_bundle_check() {
  _want="$1"; _label="$2"; _url="${3:-}"
  BUNDLE_ERR=""; BUNDLE_ACTIVE=""; BUNDLE_VERSION=""; BUNDLE_STATE=""
  if [ "$BUNDLE_REPORT" = "UNREACHABLE" ]; then
    BUNDLE_COUNT=0
    BUNDLE_ERR="$_label: could not read $_url/system/console/bundles.json"
    return 1
  fi
  BUNDLE_COUNT=$(echo "$BUNDLE_REPORT" | head -n1)
  BUNDLE_ACTIVE=$(echo "$BUNDLE_REPORT" | tail -n +2 | head -n1)
  BUNDLE_VERSION=$(echo "$BUNDLE_ACTIVE" | awk '{print $2}')
  BUNDLE_STATE=$(echo "$BUNDLE_ACTIVE" | awk '{print $3}')
  if [ "${BUNDLE_COUNT:-0}" -eq 0 ]; then
    BUNDLE_ERR="$_label: no brightcove.core bundle installed"; return 1
  fi
  if [ "${BUNDLE_COUNT:-0}" -gt 1 ]; then
    BUNDLE_ERR="$_label: $BUNDLE_COUNT brightcove.core bundles present (duplicate-bundle trap — uninstall the stale one and refreshPackages)"; return 1
  fi
  # OSGi writes a Maven qualifier with a dot (7.4.1-SNAPSHOT -> 7.4.1.SNAPSHOT): accept either form.
  if ! echo "$BUNDLE_ACTIVE" | grep -qF -e "$_want" -e "${_want/-/.}"; then
    BUNDLE_ERR="$_label: deployed bundle is not $_want (got: $BUNDLE_ACTIVE) — clientlib/bundle cache served stale code."; return 1
  fi
  if ! echo "$BUNDLE_ACTIVE" | grep -qi Active; then
    BUNDLE_ERR="$_label: brightcove.core is not Active (got: $BUNDLE_ACTIVE)."; return 1
  fi
  return 0
}

# gate_failed_components <aem-url> <user:pass>
#   Echoes "state | name" for every DS component in failed activation, nothing when
#   healthy, or the single word UNREADABLE when the list could not be read (NOT MEASURED,
#   never treated as clean).
#
#   ⚠️ Measured 2026-09-17 on an on-prem 6.5 instance: installing a package triggered an
#   OSGi refresh that cascaded into Sling's own scripting bundles and left sightly's
#   ExtensionRegistryService and JavaUseProvider in "failed activation". Every HTL script
#   using data-sly-use then fell back to its resourceSuperType, so /brightcove/admin.html
#   rendered an EMPTY Granite shell: 200 OK, no error, nearly every e2e spec timing out.
#   The connector bundle itself was Active and the right version throughout. Recovery is
#   a stop/start of the affected bundle, no reinstall needed. Healthy instances read zero.
gate_failed_components() {
  _raw=$(curl -s -u "$2" "$1/system/console/components.json" 2>/dev/null) || _raw=""
  [ -n "$_raw" ] || { echo UNREADABLE; return 0; }
  printf '%s' "$_raw" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin).get('data', [])
except Exception:
    print('UNREADABLE'); sys.exit(0)
for c in data:
    if 'fail' in str(c.get('state', '')).lower():
        print('%s | %s' % (c.get('state'), c.get('name')))
" 2>/dev/null || echo UNREADABLE
}

# ---- per-bed run lock ----
# Two agents driving e2e against the same AEM at once produced a spurious failure in a
# live playlist spec, so every run that mutates or drives a bed (build/install, bundle
# check, e2e, release deploy) holds an exclusive lock keyed on the AEM URL. mkdir is the
# atomic primitive (bash 3.2 safe, no flock on macOS). Owner pid, start time and command
# live inside the lock dir; a lock whose pid is dead is stale and is reclaimed.
# Context: docs/testing.md ("Bed lock").
GATE_LOCK_DIRS=""      # space-separated lock dirs this process holds
GATE_LOCK_WAITED=0     # total seconds spent waiting, across all gate_lock_acquire calls

gate_lock_dir_for() {
  _key=$(printf '%s' "$1" | sed 's#^[a-zA-Z]*://##; s#/*$##' | tr -c 'A-Za-z0-9.\n' '_')
  echo "${TMPDIR:-/tmp}/bcon-gate-locks/$_key.lock"
}

# gate_lock_acquire <aem-url> <timeout-secs> <command-description>
#   Returns 0 holding the lock, 1 on timeout (GATE_LOCK_ERR set).
gate_lock_acquire() {
  _url="$1"; _timeout="$2"; _cmd="$3"
  _dir=$(gate_lock_dir_for "$_url")
  mkdir -p "$(dirname "$_dir")"
  _start=$(date +%s); _last_msg=0; GATE_LOCK_ERR=""
  while :; do
    if mkdir "$_dir" 2>/dev/null; then
      printf '%s\n' "$$" > "$_dir/pid"
      date +%s > "$_dir/started"
      printf '%s\n' "$_cmd" > "$_dir/cmd"
      GATE_LOCK_DIRS="$GATE_LOCK_DIRS $_dir"
      GATE_LOCK_WAITED=$((GATE_LOCK_WAITED + $(date +%s) - _start))
      return 0
    fi
    _pid=$(cat "$_dir/pid" 2>/dev/null || true)
    _since=$(cat "$_dir/started" 2>/dev/null || true)
    _holder=$(cat "$_dir/cmd" 2>/dev/null || true)
    _stale=0
    if [ -n "$_pid" ]; then
      kill -0 "$_pid" 2>/dev/null || _stale=1
    else
      # mkdir done but owner file not yet written: give the writer a moment, then call it stale.
      _age=$(( $(date +%s) - $(stat -f %m "$_dir" 2>/dev/null || stat -c %Y "$_dir" 2>/dev/null || date +%s) ))
      [ "$_age" -gt 10 ] && _stale=1
    fi
    if [ "$_stale" = "1" ]; then
      echo "   reclaiming stale bed lock for $_url (holder pid ${_pid:-?} is gone)"
      _tomb="$_dir.stale.$$"
      mv "$_dir" "$_tomb" 2>/dev/null && rm -rf "$_tomb"
      continue
    fi
    _now=$(date +%s)
    if [ $((_now - _last_msg)) -ge 60 ]; then
      _when=$([ -n "$_since" ] && { date -r "$_since" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -d "@$_since" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "?"; })
      echo "   waiting on bed lock held by $_pid (${_holder:-?}) since $_when" >&2
      _last_msg=$_now
    fi
    if [ $((_now - _start)) -ge "$_timeout" ]; then
      GATE_LOCK_WAITED=$((GATE_LOCK_WAITED + _now - _start))
      GATE_LOCK_ERR="timed out after $((_now - _start))s waiting for the bed lock on $_url held by pid $_pid (${_holder:-?})"
      return 1
    fi
    sleep 5
  done
}

# gate_lock_release_all: release every lock this process holds (safe to call twice).
gate_lock_release_all() {
  for _d in $GATE_LOCK_DIRS; do
    [ "$(cat "$_d/pid" 2>/dev/null)" = "$$" ] && rm -rf "$_d"
  done
  GATE_LOCK_DIRS=""
}
