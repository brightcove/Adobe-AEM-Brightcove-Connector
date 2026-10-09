#!/usr/bin/env bash
#
# setup-local-dev.sh — prepare a local AEM author for Brightcove component
# development. Idempotent.
#
# What it does:
#   1. Confirms the AEM author is reachable.
#   2. Verifies the target site's content-page template + responsivegrid
#      policy exist (default site: test-site).
#   3. Adds "group:Brightcove" to the responsivegrid policy so the Brightcove
#      Player and Playlist Player components can be dropped onto pages.
#   4. Creates a test page with all three Brightcove components placed
#      (player + playlist + experiences, plus a second player further down)
#      interleaved with filler text so the page is tall enough to scroll.
#      Open it in the editor and exercise edit/insert/copy/delete listeners
#      with components both above and below the fold (BGS-1690 scroll test).
#   5. Sets sling:resourceType on the inner responsivegrid node (the parsys),
#      which raw Sling POSTs leave unset — that's the silent failure that
#      makes scripted pages render empty in the editor.
#
# Usage:
#   ./setup-local-dev.sh
#   AEM_HOST=localhost AEM_PORT=4502 AEM_USER=admin AEM_PASS=admin \
#     SITE=test-site PAGE=bgs-test-page ./setup-local-dev.sh
#
# Re-running is safe. The policy update merges in idempotently; the page is
# replaced if it already exists.

set -euo pipefail

AEM_HOST="${AEM_HOST:-localhost}"
AEM_PORT="${AEM_PORT:-4502}"
AEM_USER="${AEM_USER:-admin}"
AEM_PASS="${AEM_PASS:-admin}"
SITE="${SITE:-test-site}"
PAGE="${PAGE:-bgs-test-page}"

BASE="http://${AEM_HOST}:${AEM_PORT}"
AUTH="${AEM_USER}:${AEM_PASS}"
SITE_ROOT="/content/${SITE}"
PAGE_PATH="${SITE_ROOT}/${PAGE}"
POLICY_PATH="/conf/${SITE}/settings/wcm/policies/wcm/foundation/components/responsivegrid/default-policy"
TEMPLATE_PATH="/conf/${SITE}/settings/wcm/templates/content-page"

note() { printf '\033[36m[setup]\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m[ok]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[err]\033[0m %s\n' "$*" >&2; exit 1; }

req() {
  # req METHOD URL [extra curl args ...]
  # Echoes HTTP status to stdout.
  local method="$1"; shift
  local url="$1"; shift
  curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" -X "$method" "$@" "$url"
}

req_get_status() { req GET "$1"; }

# Create a minimal editable-template scaffold under /conf/<SITE> when no site
# exists yet: a content-page template (page -> root(responsivegrid) ->
# responsivegrid) + a responsivegrid policy that allows group:Brightcove, and
# a /content/<SITE> root. Mirrors the standard AEM archetype structure so the
# script runs on a clean author with no pre-existing site. Idempotent.
bootstrap_conf() {
  local tt="/conf/${SITE}/settings/wcm/template-types/content-page"
  note "No '$SITE' site found — bootstrapping a minimal conf scaffold under /conf/${SITE}"

  # Mark /conf/<SITE> as a configuration container.
  req POST "$BASE/conf/${SITE}" \
    -F "jcr:primaryType=sling:Folder" \
    -F "jcr:mixinTypes=cq:Conf" >/dev/null || true

  # template-type stub (the template references it).
  req POST "$BASE${tt}" \
    -F "jcr:primaryType=cq:Template" \
    -F "jcr:content/jcr:primaryType=cq:PageContent" \
    -F "jcr:content/jcr:title=Content Page" \
    -F "jcr:content/status=enabled" >/dev/null || true

  # responsivegrid policy that permits the Brightcove component group.
  # The whole subtree is created in one POST with explicit jcr:primaryType on
  # every level: the policies node is a cq:Page, whose residual child type is
  # cq:Page, so auto-created intermediates would conflict with the
  # nt:unstructured policy node (HTTP 409) unless each level is set explicitly.
  local pp="wcm/foundation/components/responsivegrid/default-policy"
  s=$(req POST "$BASE/conf/${SITE}/settings/wcm/policies" \
    -F "jcr:primaryType=cq:Page" \
    -F "jcr:content/jcr:primaryType=cq:PageContent" \
    -F "wcm/jcr:primaryType=nt:unstructured" \
    -F "wcm/foundation/jcr:primaryType=nt:unstructured" \
    -F "wcm/foundation/components/jcr:primaryType=nt:unstructured" \
    -F "wcm/foundation/components/responsivegrid/jcr:primaryType=nt:unstructured" \
    -F "${pp}/jcr:primaryType=nt:unstructured" \
    -F "${pp}/sling:resourceType=wcm/core/components/policy/policy" \
    -F "${pp}/jcr:title=Brightcove-enabled responsivegrid" \
    -F "${pp}/components=group:General" \
    -F "${pp}/components=group:Form" \
    -F "${pp}/components=group:Brightcove" \
    -F "${pp}/components@TypeHint=String[]")
  [[ "$s" =~ ^20 ]] || die "policy create failed (HTTP $s)"

  # template node + enabled jcr:content.
  s=$(req POST "$BASE${TEMPLATE_PATH}" \
    -F "jcr:primaryType=cq:Template" \
    -F "jcr:content/jcr:primaryType=cq:PageContent" \
    -F "jcr:content/jcr:title=Content Page" \
    -F "jcr:content/jcr:description=Brightcove component test pages" \
    -F "jcr:content/status=enabled" \
    -F "jcr:content/cq:templateType=${tt}")
  [[ "$s" =~ ^20 ]] || die "template create failed (HTTP $s)"

  # structure: locked page + root(responsivegrid) + editable responsivegrid.
  s=$(req POST "$BASE${TEMPLATE_PATH}/structure" \
    -F "jcr:primaryType=cq:Page" \
    -F "jcr:content/jcr:primaryType=nt:unstructured" \
    -F "jcr:content/sling:resourceType=wcm/foundation/components/page" \
    -F "jcr:content/jcr:title=Content Page" \
    -F "jcr:content/cq:template=${TEMPLATE_PATH}" \
    -F "jcr:content/root/jcr:primaryType=nt:unstructured" \
    -F "jcr:content/root/sling:resourceType=wcm/foundation/components/responsivegrid" \
    -F "jcr:content/root/layout=responsiveGrid" \
    -F "jcr:content/root/responsivegrid/jcr:primaryType=nt:unstructured" \
    -F "jcr:content/root/responsivegrid/sling:resourceType=wcm/foundation/components/responsivegrid" \
    -F "jcr:content/root/responsivegrid/layout=responsiveGrid" \
    -F "jcr:content/root/responsivegrid/editable=true")
  [[ "$s" =~ ^20 ]] || die "template structure create failed (HTTP $s)"

  # policy mapping: associate root + responsivegrid with the policy above.
  s=$(req POST "$BASE${TEMPLATE_PATH}/policies" \
    -F "jcr:primaryType=cq:Page" \
    -F "jcr:content/jcr:primaryType=nt:unstructured" \
    -F "jcr:content/sling:resourceType=wcm/core/components/policies/mappings" \
    -F "jcr:content/root/jcr:primaryType=nt:unstructured" \
    -F "jcr:content/root/sling:resourceType=wcm/core/components/policies/mapping" \
    -F "jcr:content/root/cq:policy=wcm/foundation/components/responsivegrid/default-policy" \
    -F "jcr:content/root/responsivegrid/jcr:primaryType=nt:unstructured" \
    -F "jcr:content/root/responsivegrid/sling:resourceType=wcm/core/components/policies/mapping" \
    -F "jcr:content/root/responsivegrid/cq:policy=wcm/foundation/components/responsivegrid/default-policy")
  [[ "$s" =~ ^20 ]] || die "template policy mapping create failed (HTTP $s)"

  # content root so /content/<SITE>/<page> has a proper cq:Page parent.
  req POST "$BASE/content/${SITE}" \
    -F "jcr:primaryType=cq:Page" \
    -F "jcr:content/jcr:primaryType=cq:PageContent" \
    -F "jcr:content/jcr:title=${SITE}" \
    -F "jcr:content/sling:resourceType=wcm/foundation/components/page" >/dev/null || true

  ok "Conf scaffold created under /conf/${SITE}"
}

note "Target: $BASE  site=$SITE  page=$PAGE"

# 1. Reachability
status=$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" "$BASE/libs/granite/core/content/login.html" || echo 000)
if [[ "$status" != "200" ]]; then
  die "AEM not reachable at $BASE (got HTTP $status). Is the author quickstart running?"
fi
ok "AEM author reachable"

# 2. Site template + policy. If either is missing, bootstrap a minimal scaffold
#    so this works on a clean AEM with no pre-existing site. (Set SITE=<name>
#    to instead target a site you already have.)
tmpl_status=$(req_get_status "$BASE${TEMPLATE_PATH}.json")
pol_status=$(req_get_status "$BASE${POLICY_PATH}.json")
if [[ "$tmpl_status" != "200" || "$pol_status" != "200" ]]; then
  bootstrap_conf
  tmpl_status=$(req_get_status "$BASE${TEMPLATE_PATH}.json")
  pol_status=$(req_get_status "$BASE${POLICY_PATH}.json")
  [[ "$tmpl_status" == "200" && "$pol_status" == "200" ]] \
    || die "conf scaffold still missing after bootstrap (template=$tmpl_status policy=$pol_status)"
else
  ok "Site '$SITE' template + policy present"
fi

# 3. Policy: ensure group:Brightcove is in the components list.
# Sling POST with multi-value field replaces the list; we re-send the
# canonical superset so the script is safe to run repeatedly.
note "Updating $POLICY_PATH to allow group:Brightcove"
status=$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" \
  -F "components=group:General" \
  -F "components=group:Form" \
  -F "components=group:Brightcove" \
  -F "components@TypeHint=String[]" \
  "$BASE$POLICY_PATH")
if [[ "$status" != "200" && "$status" != "201" ]]; then
  die "Policy update failed (HTTP $status)"
fi
ok "Policy now allows General + Form + Brightcove groups"

# 4. Create / replace the test page.
# Delete first if it exists, then create from scratch — simpler than diffing.
if [[ "$(req_get_status "$BASE${PAGE_PATH}.json")" == "200" ]]; then
  note "Existing page at $PAGE_PATH — replacing"
  curl -s -o /dev/null -u "$AUTH" -X DELETE "$BASE$PAGE_PATH"
fi

note "Creating page $PAGE_PATH with Brightcove components + filler text for scroll testing"

# Reusable filler text. Plain prose only — embedding HTML markup in the
# Sling POST body (angle brackets) makes AEM hang the request, and semicolons
# in the value get eaten by curl's -F attribute parsing. Plain text is wide
# enough when wrapped that one block fills ~half a viewport.
FILLER='Filler content so the page is tall enough that scrolling is meaningful. BGS-1690 is about preserving your scroll position across component-edit interactions. To test that, this page deliberately puts Brightcove components both above and below the fold so you can scroll to an off-screen one, edit it, and confirm the page does not jump back to the top. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.'

# Layout (top -> bottom):
#   1. title             "BGS-1690 — Brightcove component test page"
#   2. filler text
#   3. brightcove_player_top
#   4. filler text
#   5. brightcove_playlist
#   6. filler text
#   7. brightcove_player_mid     (second player — edit this one with the first
#                                 above the fold to verify the unrelated player
#                                 does NOT flicker)
#   8. filler text
#   9. brightcove_experiences
#  10. filler text                (bottom)
status=$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" \
  -F "jcr:primaryType=cq:Page" \
  -F "jcr:content/jcr:primaryType=cq:PageContent" \
  -F "jcr:content/jcr:title=Brightcove component test ($PAGE)" \
  -F "jcr:content/cq:template=$TEMPLATE_PATH" \
  -F "jcr:content/sling:resourceType=wcm/foundation/components/page" \
  -F "jcr:content/root/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/sling:resourceType=wcm/foundation/components/responsivegrid" \
  -F "jcr:content/root/responsivegrid/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/sling:resourceType=wcm/foundation/components/responsivegrid" \
  \
  -F "jcr:content/root/responsivegrid/title_top/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/title_top/sling:resourceType=wcm/foundation/components/title" \
  -F "jcr:content/root/responsivegrid/title_top/jcr:title=BGS-1690 — Brightcove component test page" \
  \
  -F "jcr:content/root/responsivegrid/filler_a/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/filler_a/sling:resourceType=wcm/foundation/components/text" \
  -F "jcr:content/root/responsivegrid/filler_a/text=$FILLER" \
  \
  -F "jcr:content/root/responsivegrid/brightcove_player_top/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/brightcove_player_top/sling:resourceType=brightcove/components/content/brightcoveplayer" \
  \
  -F "jcr:content/root/responsivegrid/filler_b/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/filler_b/sling:resourceType=wcm/foundation/components/text" \
  -F "jcr:content/root/responsivegrid/filler_b/text=$FILLER" \
  \
  -F "jcr:content/root/responsivegrid/brightcove_playlist/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/brightcove_playlist/sling:resourceType=brightcove/components/content/brightcoveplayer-playlist" \
  \
  -F "jcr:content/root/responsivegrid/filler_c/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/filler_c/sling:resourceType=wcm/foundation/components/text" \
  -F "jcr:content/root/responsivegrid/filler_c/text=$FILLER" \
  \
  -F "jcr:content/root/responsivegrid/brightcove_player_mid/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/brightcove_player_mid/sling:resourceType=brightcove/components/content/brightcoveplayer" \
  \
  -F "jcr:content/root/responsivegrid/filler_d/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/filler_d/sling:resourceType=wcm/foundation/components/text" \
  -F "jcr:content/root/responsivegrid/filler_d/text=$FILLER" \
  \
  -F "jcr:content/root/responsivegrid/brightcove_experiences/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/brightcove_experiences/sling:resourceType=brightcove/components/content/brightcoveexperiences" \
  \
  -F "jcr:content/root/responsivegrid/filler_e/jcr:primaryType=nt:unstructured" \
  -F "jcr:content/root/responsivegrid/filler_e/sling:resourceType=wcm/foundation/components/text" \
  -F "jcr:content/root/responsivegrid/filler_e/text=$FILLER" \
  \
  "$BASE$PAGE_PATH")
if [[ "$status" != "201" ]]; then
  die "Page creation failed (HTTP $status)"
fi
ok "Page created (1 title + 4 Brightcove components + 5 filler text blocks)"

# 5. Belt-and-braces: explicitly POST sling:resourceType on the inner parsys.
# The combined POST above sets it, but if you ever create a page another way
# (curl one-liner, Sling Editor, etc) and components don't render, this is
# the property that's almost always missing.
curl -s -o /dev/null -u "$AUTH" \
  -F "sling:resourceType=wcm/foundation/components/responsivegrid" \
  "$BASE$PAGE_PATH/jcr:content/root/responsivegrid"

ok "Inner parsys sling:resourceType confirmed"

cat <<EOF

Open in the editor:
  $BASE/editor.html$PAGE_PATH.html

Page renders (top -> bottom): title, filler, Brightcove Player, filler,
Brightcove Playlist Player, filler, second Brightcove Player, filler,
Brightcove Experiences, filler. Long enough to scroll.

To exercise BGS-1690 scroll preservation:
  1. Scroll to the second Brightcove Player (mid-page).
  2. Open its configure dialog, change a field, save.
  3. Expected: the page does NOT jump back to the top; the second Player
     re-renders in place; the top Brightcove Player above the fold does
     NOT visibly flicker.

Insert / copy / delete on any component should also preserve scroll.

EOF
