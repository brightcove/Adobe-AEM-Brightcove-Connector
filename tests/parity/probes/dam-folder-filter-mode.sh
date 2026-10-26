#!/usr/bin/env bash
# Probe: how a ui.content install treats the default DAM folder's jcr:content
# (/content/dam/brightcove_assets/jcr:content: jcr:title, cq:conf, metadataSchema).
# Context: current/docs/core-folder-sync.md "Default folder properties".
#
# Usage: dam-folder-filter-mode.sh <aem-url> <ui.content zip> <expect: update|merge|merge_properties>
#   update            a customer's title is overwritten on install (the 7.4.0-pre behaviour)
#   merge_properties  (cloud) customer values are kept, missing ones are added, a deleted node is recreated
#   merge             (on-prem) customer values are kept, missing ones stay missing (the documented
#                     upgrade gap), a deleted node is recreated. ⚠️ On-prem must not use
#                     merge_properties: FileVault 3.2.8 on 6.5 SPs rejects it.
# Every install must return packmgr status 200, or that is a FAIL.
# Mutates ONLY that one node, and restores the shipped values at the end. Localhost only.
# Prints PASS / FAIL / NOT MEASURED per check; exits non-zero on anything but PASS.
set -u
AEM="${1:?aem url}"; ZIP="${2:?ui.content zip}"; EXPECT="${3:?update|merge|merge_properties}"
AUTH="${AEM_AUTH:-admin:admin}"
NODE="/content/dam/brightcove_assets/jcr:content"
SHIPPED_TITLE="Brightcove Connector"
SHIPPED_CONF="/conf/brightcove"
SHIPPED_SCHEMA="/conf/global/settings/dam/adminui-extension/metadataschema/brightcove"
CUSTOM_TITLE="e2e-throwaway customer title"
CUSTOM_SCHEMA="/conf/e2e-throwaway/custom-schema"
fails=0; nm=0
case "$EXPECT" in update|merge|merge_properties) ;; *) echo "bad expect: $EXPECT"; exit 2 ;; esac

case "$AEM" in
  http://localhost:*|http://127.0.0.1:*) ;;
  *) echo "GUARD: refusing non-local AEM $AEM"; echo "VERDICT: NOT MEASURED"; exit 2 ;;
esac

pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; fails=$((fails+1)); }
notm() { echo "NOT MEASURED $*"; nm=$((nm+1)); }

prop() {  # prints the property value, or <absent>; <no-node> when the node is missing
  curl -s -u "$AUTH" "$AEM$NODE.json" | python3 -c '
import json,sys
try: d=json.load(sys.stdin)
except Exception: print("<no-node>"); sys.exit()
print(d.get(sys.argv[1],"<absent>"))' "$1"
}
post() { curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" "$AEM$NODE" "$@"; }
install() {  # install <label>: upload + install the package, print the packmgr status line;
              # anything but 200 is a FAIL (runs in this shell so the counter sticks)
  local st; st="$(curl -s -u "$AUTH" -F "file=@$ZIP" -F force=true -F install=true \
    "$AEM/crx/packmgr/service.jsp" | grep -o '<status code="[0-9]*">[^<]*' | head -1)"
  echo "$1 install: ${st:-<no status>}"
  case "$st" in '<status code="200">'*) ;; *) fail "$1 install did not return status 200" ;; esac
}
check() {  # check <label> <property> <expected>
  local got; got="$(prop "$2")"
  if [ "$got" = "$3" ]; then pass "$1: $2=$got"; else fail "$1: $2 expected '$3' got '$got'"; fi
}

echo "== $AEM  zip=$(basename "$ZIP")  expect=$EXPECT"
before_pp="$(prop processingProfile)"

# Scenario 1: the customer renamed the folder and picked their own schema.
post -F "jcr:title=$CUSTOM_TITLE" -F "metadataSchema=$CUSTOM_SCHEMA" >/dev/null
[ "$(prop jcr:title)" = "$CUSTOM_TITLE" ] || notm "s1 precondition: could not set the custom title"
install s1
if [ "$EXPECT" = update ]; then
  check "s1 update overwrites the customer title" jcr:title "$SHIPPED_TITLE"
  check "s1 update overwrites the customer schema" metadataSchema "$SHIPPED_SCHEMA"
else
  check "s1 customer title kept" jcr:title "$CUSTOM_TITLE"
  check "s1 customer schema kept" metadataSchema "$CUSTOM_SCHEMA"
fi
check "s1 other properties untouched (processingProfile)" processingProfile "$before_pp"

if [ "$EXPECT" != update ]; then
  # Scenario 2: an existing folder that never had a schema or cq:conf (a 7.2.x / 6.0.x upgrade).
  post -F "metadataSchema@Delete=" -F "cq:conf@Delete=" >/dev/null
  [ "$(prop metadataSchema)" = "<absent>" ] || notm "s2 precondition: metadataSchema still present"
  install s2
  if [ "$EXPECT" = merge_properties ]; then
    check "s2 missing schema added" metadataSchema "$SHIPPED_SCHEMA"
    check "s2 missing cq:conf added" cq:conf "$SHIPPED_CONF"
  else
    # merge leaves an existing node alone: the documented on-prem upgrade gap.
    check "s2 merge leaves the missing schema missing (known gap)" metadataSchema "<absent>"
    check "s2 merge leaves the missing cq:conf missing (known gap)" cq:conf "<absent>"
  fi
  check "s2 customer title still kept" jcr:title "$CUSTOM_TITLE"

  # Scenario 3: no jcr:content at all (fresh folder).
  post -F ":operation=delete" >/dev/null
  [ "$(prop jcr:title)" = "<no-node>" ] || notm "s3 precondition: node still present"
  install s3
  check "s3 node recreated: title" jcr:title "$SHIPPED_TITLE"
  check "s3 node recreated: cq:conf" cq:conf "$SHIPPED_CONF"
  check "s3 node recreated: schema" metadataSchema "$SHIPPED_SCHEMA"
fi

# Restore the shipped values (and the processingProfile seen at start, if any).
restore=(-F "jcr:title=$SHIPPED_TITLE" -F "cq:conf=$SHIPPED_CONF" -F "metadataSchema=$SHIPPED_SCHEMA")
[ "$before_pp" != "<absent>" ] && [ "$before_pp" != "<no-node>" ] && restore+=(-F "processingProfile=$before_pp")
post "${restore[@]}" >/dev/null
echo "restored: title=$(prop jcr:title) schema=$(prop metadataSchema) processingProfile=$(prop processingProfile)"

if [ "$fails" -gt 0 ]; then echo "VERDICT: FAIL ($fails)"; exit 1; fi
if [ "$nm" -gt 0 ]; then echo "VERDICT: NOT MEASURED ($nm)"; exit 2; fi
echo "VERDICT: PASS"
