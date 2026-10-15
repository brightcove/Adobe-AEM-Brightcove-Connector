#!/usr/bin/env bash
# Self-test for tests/gate/lib.sh. Needs no AEM. Run: bash tests/gate/lib.test.sh
# Covers the exact bundle-version comparison and the stale-lock reclaim race.
set -u
here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=lib.sh
. "$here/lib.sh"

fails=0
ok()   { echo "PASS $1"; }
bad()  { echo "FAIL $1"; fails=$((fails + 1)); }

# ---- version comparison ----
expect_match() { # <expected> <deployed> <want: yes|no>
  if gate_version_matches "$1" "$2"; then got=yes; else got=no; fi
  [ "$got" = "$3" ] && ok "version $1 vs $2 -> $3" || bad "version $1 vs $2: expected $3, got $got"
}
expect_match 7.4.1 7.4.1 yes
expect_match 7.4.1 7.4.10 no
expect_match 7.4.1 7.4.1.SNAPSHOT no
expect_match 7.4.1-SNAPSHOT 7.4.1.SNAPSHOT yes
expect_match 7.4.1-SNAPSHOT 7.4.1-SNAPSHOT yes
expect_match 7.4.1 7.4.2 no

# gate_bundle_check against a fake report
check() { # <expected> <report> <want-rc>
  BUNDLE_REPORT="$2"
  gate_bundle_check "$1" test http://x >/dev/null 2>&1; rc=$?
  [ "$rc" = "$3" ] && ok "bundle_check $1 on '$(echo "$2" | tail -n1)' rc=$3" || bad "bundle_check $1 on '$2': expected rc $3, got $rc"
}
check 7.4.1 "$(printf '1\n696 7.4.1 Active')" 0
check 7.4.1 "$(printf '1\n696 7.4.10 Active')" 1
check 7.4.1 "$(printf '1\n696 7.4.1.SNAPSHOT Active')" 1
check 7.4.1-SNAPSHOT "$(printf '1\n696 7.4.1.SNAPSHOT Active')" 0
check 7.4.1 "$(printf '1\n696 7.4.1 Resolved')" 1

# ---- stale-lock reclaim race ----
export TMPDIR; TMPDIR=$(mktemp -d)
export GATE_LOCK_POLL=0.2
sleep 0 & deadpid=$!; wait "$deadpid" 2>/dev/null
url=http://race.test:4502
lockdir=$(gate_lock_dir_for "$url")
rounds=20
for i in $(seq 1 $rounds); do
  rm -rf "$lockdir" "$lockdir.reclaim"; rm -f "$TMPDIR"/res.* "$TMPDIR/release"
  mkdir -p "$lockdir"; echo "$deadpid" > "$lockdir/pid"
  for w in a b; do
    (
      . "$here/lib.sh"
      # racer b dawdles between seeing the stale lock and reclaiming it, so a without
      # the reclaim mutex would have acquired a fresh lock that b then moves aside
      [ "$w" = b ] && export GATE_LOCK_TEST_DELAY=0.6
      if gate_lock_acquire "$url" 2 "racer-$w" >/dev/null 2>&1; then echo held > "$TMPDIR/res.$w"
        while [ ! -e "$TMPDIR/release" ]; do sleep 0.1; done
        gate_lock_release_all
      else echo lost > "$TMPDIR/res.$w"; fi
    ) &
  done
  # wait for both to report
  for _ in $(seq 1 100); do [ -e "$TMPDIR/res.a" ] && [ -e "$TMPDIR/res.b" ] && break; sleep 0.1; done
  held=$(cat "$TMPDIR"/res.* 2>/dev/null | grep -c held)
  touch "$TMPDIR/release"; wait
  [ "$held" = 1 ] || { bad "race round $i: $held holders (want exactly 1)"; break; }
done
[ "$fails" = 0 ] && ok "lock reclaim: exactly one holder in $rounds rounds"
rm -rf "$TMPDIR"

[ "$fails" = 0 ] && echo "ALL PASS" || { echo "$fails FAILED"; exit 1; }
