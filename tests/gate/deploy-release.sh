#!/usr/bin/env bash
#
# Install a published connector release onto an AEM instance, to get a known baseline
# for a regression comparison (tests/gate/compare.js, docs/testing.md).
#
#   tests/gate/deploy-release.sh [options] <release-tag> <aem-url>
#
#   <release-tag>   e.g. 7.2.3-cloud, 7.4.1-cloud, 6.0.12-prem
#   <aem-url>       e.g. http://localhost:4502
#   --repo <o/r>            GitHub repo. Default brightcove/Adobe-AEM-Brightcove-Connector.
#   --expect-version <v>    Bundle version to wait for. Default: the tag minus its
#                           -cloud/-prem suffix.
#   --lock-timeout <secs>   How long to wait for the per-bed run lock held by another gate or
#                           deploy on the same AEM URL. Default 1800 (30 min), then fail.
#   --dry-run               Resolve and print the asset and target; download and install
#                           nothing, contact no AEM.
#   --help
#
# Asset choice: brightcove.all-*.zip (the full content package) if the release has one,
# else the single other brightcove*.zip (the 6.0.x line ships only
# brightcove_connector.ui.apps-*.zip). Exits non-zero, loudly, unless exactly one
# brightcove.core bundle ends up Active at the expected version.
#
# ⚠️ This REPLACES whatever connector is installed on <aem-url>. Never point it at a
# shared instance. Env: AEM_AUTH (default admin:admin).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
. "$HERE/lib.sh"

AUTH="${AEM_AUTH:-admin:admin}"
REPO="brightcove/Adobe-AEM-Brightcove-Connector"
EXPECT=""
LOCK_TIMEOUT=1800
DRY_RUN=0
TAG=""; URL=""

fail() { echo "DEPLOY FAILED: $1" >&2; exit 1; }
usage() { sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --repo) [ $# -ge 2 ] || fail "--repo requires a value"; REPO="$2"; shift 2 ;;
    --expect-version) [ $# -ge 2 ] || fail "--expect-version requires a value"; EXPECT="$2"; shift 2 ;;
    --lock-timeout) [ $# -ge 2 ] || fail "--lock-timeout requires seconds"; LOCK_TIMEOUT="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --help|-h) usage; exit 0 ;;
    -*) usage; fail "unknown argument: $1" ;;
    *) if [ -z "$TAG" ]; then TAG="$1"; elif [ -z "$URL" ]; then URL="$1"; else fail "unexpected argument: $1"; fi; shift ;;
  esac
done
[ -n "$TAG" ] && [ -n "$URL" ] || { usage; fail "need <release-tag> and <aem-url>"; }
URL="${URL%/}"

case "$TAG" in
  *-cloud) PLATFORM=cloud ;;
  *-prem) PLATFORM=onprem ;;
  *) PLATFORM=unknown ;;
esac
[ -n "$EXPECT" ] || { EXPECT="${TAG%-cloud}"; EXPECT="${EXPECT%-prem}"; }

command -v gh >/dev/null || fail "gh CLI not found"
assets=$(gh release view "$TAG" --repo "$REPO" --json assets -q '.assets[].name') \
  || fail "cannot read release $TAG in $REPO"
ASSET=$(echo "$assets" | grep -E '^brightcove\.all-.*\.zip$' | head -n1 || true)
if [ -z "$ASSET" ]; then
  n=$(echo "$assets" | grep -cE '^brightcove.*\.zip$' || true)
  [ "$n" = "1" ] || fail "release $TAG has no brightcove.all-*.zip and $n other brightcove*.zip assets; cannot pick one"
  ASSET=$(echo "$assets" | grep -E '^brightcove.*\.zip$')
fi

echo "release:   $TAG ($PLATFORM) in $REPO"
echo "asset:     $ASSET"
echo "target:    $URL"
echo "expect:    brightcove.core $EXPECT, Active, exactly one"
if [ "$DRY_RUN" = "1" ]; then
  echo "dry run: nothing downloaded, nothing installed, no AEM contacted"
  exit 0
fi

case "$URL" in
  http://localhost*|http://127.0.0.1*|https://localhost*|https://127.0.0.1*) : ;;
  *) [ "${ALLOW_REMOTE:-0}" = "1" ] || fail "$URL is not a loopback AEM; set ALLOW_REMOTE=1 to override" ;;
esac

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"; gate_lock_release_all' EXIT
# Held until this script exits: another gate run on the same bed must not see a half-installed connector.
echo "taking the bed lock for $URL..."
gate_lock_acquire "$URL" "$LOCK_TIMEOUT" "deploy-release.sh $TAG" || fail "$GATE_LOCK_ERR"
gh release download "$TAG" --repo "$REPO" --pattern "$ASSET" --dir "$WORK" || fail "download of $ASSET"
[ -s "$WORK/$ASSET" ] || fail "downloaded $ASSET is empty"

echo "installing via package manager..."
resp=$(curl -s -u "$AUTH" -F "file=@$WORK/$ASSET" -F force=true -F install=true "$URL/crx/packmgr/service.jsp") \
  || fail "package manager upload to $URL"
echo "$resp" | grep -q 'status code="200"' || fail "package manager did not answer 200: $(echo "$resp" | tr '\n' ' ' | cut -c1-300)"

BUNDLE_WAIT_SECS="${BUNDLE_WAIT_SECS:-180}"
gate_bundle_wait "$URL" "$AUTH" "$BUNDLE_WAIT_SECS"
[ "$BUNDLE_WAITED" -gt 0 ] && echo "(waited ${BUNDLE_WAITED}s for the installer to settle)"
if ! gate_bundle_check "$EXPECT" "$PLATFORM" "$URL"; then
  fail "$BUNDLE_ERR"
fi
echo "brightcove.core: $BUNDLE_ACTIVE"

failed=$(gate_failed_components "$URL" "$AUTH")
if [ "$failed" = "UNREADABLE" ]; then
  fail "could not read the component list; failed-activation check NOT MEASURED"
elif [ -n "$failed" ]; then
  echo "$failed" | sed 's/^/  x /'
  fail "OSGi components in failed activation after the install (see docs/testing.md)"
fi
echo "DEPLOYED $TAG on $URL: brightcove.core $BUNDLE_VERSION $BUNDLE_STATE, no failed components"
