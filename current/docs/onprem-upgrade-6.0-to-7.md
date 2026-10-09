# Upgrading an on-prem AEM 6.5 install from connector 6.0.x to 7.x

Companion to `../ONPREM-PARITY-PLAN.md` (Phase 4). Facts here were measured on AEM 6.5.0 GA on
2026-09-17, first while upgrading a 6.0.12 install in place to the unified 7.3.0 build, then
re-verified by installing the real `brightcove_connector.ui.apps` 6.0.12 package back over the
new build and upgrading again to 7.3.8. Two bullets below were WRONG before that second pass
and are corrected: the workflow-model names and the legacy clientlib paths.

## What the 7.x package does for you

- **Removes the 6.0.x bundle and its runmode configs.** The package owns `/apps/brightcove/install`
  and `/apps/brightcove/runmodes` with no content, so both are cleared on install. Without this
  the old `brightcove-services` bundle stayed Active next to `brightcove.core` and the old
  service-user mapping (naming the old bundle) shadowed the new one.
- **Keeps your accounts working.** Configurations written for 6.0.x live under the factory PID
  `com.coresecure.brightcove.wrapper.sling.BrcServiceImpl` with snake_case keys. 7.x reads them
  through `LegacyConfigurationServiceImpl` and logs one WARN per account. Recreate each account
  under **Brightcove Service** (`…ConfigurationServiceImpl`, camelCase keys) when convenient; if
  both exist for the same account id, the new one wins. New settings (`defaultTagInclude`, …) are
  only available on the new PID.
- **Removes the 6.0.x clientlibs under `/etc`.** The package owns `/etc/designs/cs/brightcove`
  and `/etc/clientlibs/brightcove` with no content. ⚠️ This matters more than it looks: the
  6.0.x libraries declare the SAME clientlib categories as the new ones under
  `/apps/brightcove/clientlibs` (`brc.html5-player`, `brc.smart-player`, `cq.shared`), and
  nothing else in either package deletes them, so before this an upgraded instance had two
  libraries registered per category and every page requesting one loaded the old AND the new
  player JS. Measured: the legacy `html5-player.js` served 6250 bytes next to the new one's
  8598. `brc.bootstrap.v2` is the one category with no replacement; it belonged to the 6.0.x
  admin console, which 7.x replaces outright.
- **Creates the service user and default DAM folder** via repoinit using grammar that the 6.5.0
  parser accepts, and ships the folder's Brightcove metadata schema so the Brightcove tab appears
  in the asset editor.
- ⚠️ **Does NOT remove the 6.0.x root grant.** 6.0.x shipped `jcr_root/_rep_policy.xml`
  (filter `/rep:policy/allowBrc`, `mode="merge"`, `acHandling=merge_preserve`), which grants
  `brightcove_admin` `jcr:read`, `rep:write`, `crx:replicate`, `jcr:versionManagement`,
  `jcr:lockManagement`, `jcr:readAccessControl` and `jcr:modifyAccessControl` on `/` with no
  restriction, i.e. the whole repository. Oak stores it under a generated name (measured:
  `/rep:policy/allow23`), and no 7.x filter owns `/rep:policy`, so it survives the upgrade
  unchanged. The 7.x grants (`/content`; read on `/home/groups` and `/home/users`) are added
  next to it. So the narrowed permission model (least privilege) holds on FRESH installs only;
  an upgraded instance keeps the 6.0.x root grant until an administrator removes that entry by
  hand. `tests/parity/probes/upgrade/onprem-upgrade-probe.js` reports it as a named WARN, not a
  PASS, and records the thumbnail-less import check as NOT MEASURED on such a bed. It also
  masks any 7.x code that would need a wider grant (the thumbnail-placeholder defect fixed in
  `f670f4e` was invisible on upgraded instances for this reason).

| 6.0.x key (`BrcServiceImpl`) | 7.x key (`ConfigurationServiceImpl`) |
|---|---|
| `key` | `accountId` |
| `client_id` / `client_secret` | `clientId` / `clientSecret` |
| `allowed_groups` | `allowedGroups` |
| `playersstore` | `playerStorePath` |
| `defVideoPlayerID` / `defPlaylistPlayerID` | `defaultVideoPlayerId` / `defaultPlaylistPlayerId` |
| `proxy` | `proxyServer` |
| `asset_integration_path` | `damIntegrationPath` |
| `ingest_profile` | `defaultIngestProfile` |

## What you must check yourself

- `allowedGroups` must contain a group the connector users belong to. On a stock 6.5 `admin` is
  only in `everyone`; an account restricted to `administrators` is invisible to admin.
- **WCM Core Components must already be installed on the instance** (AEM 6.5 and 6.5 LTS). The
  connector does not ship them (they would replace your instance's own Core Components bundle; see
  `core-components-not-embedded.md`). Its player and iframe page components inherit from
  `core/wcm/components/page/v3/page` and its `/conf/brightcove` templates use
  `core/wcm/components/container/v1/container`, so make sure your Core Components version
  provides both. Without them those pages and templates do not render or author correctly.
- Workflow model names changed: `brightcove-sync-asset-workflow` → `bc-sync-new-asset`,
  `brightcove-delete-asset-workflow` → `brightcove-delete-asset`. **Your existing launchers
  keep working and nothing needs to change on upgrade day.** Measured on an upgraded instance:
  the 6.0.x models are still present at both `/conf/global/settings/workflow/models/…` and
  `/var/workflow/models/…` (the packages' filters for those trees are `mode="merge"` and only
  own the NEW model names, so nothing deletes the old ones), and the old and new models invoke
  the same process classes, `…workflow.BrightcoveSyncAssetWorkflowStep` and
  `…workflow.BrightcoveDeleteAssetWorkflowStep`, both of which the 7.x bundle registers and
  reports Active. Residual risks, in order: your workflow list shows four Brightcove models
  instead of two; the 6.0.x models are frozen, so step configuration added in 7.x is not in
  them; and if you ever delete them, any launcher still pointing there stops firing silently.
  Re-point launchers at the new names when convenient, then delete the old models.
- Hand-written references to `/etc/designs/cs/brightcove` or `/etc/clientlibs/brightcove`
  paths break, because the upgrade now deletes those trees (see above). Component markup
  includes clientlibs by category, so pages built from the connector's own components are
  unaffected; a customer template that hardcodes a path (for example a
  `/etc/designs/cs/brightcove/shared/img/*.png` thumbnail) must move to
  `/apps/brightcove/clientlibs/*`.
- **Design-mode settings under `/etc/designs/cs/brightcove` are removed.** 7.x `ui.content` owns
  `/etc/designs/cs/brightcove` (that is how the old client libraries are removed), while 6.0.x
  excluded its `jcr:content`. So anything a site stored under
  `/etc/designs/cs/brightcove/jcr:content` (design-mode settings) is deleted on upgrade. No
  connector page or template in 6.0.12 used that design, so this only affects sites that pointed
  their own pages at it (`cq:designPath`); if yours did, back that node up before upgrading. Not
  measured on an upgraded instance: this follows from the package filter, not from a run.

## The upgrade path that was actually verified

2026-09-17 on AEM 6.5.0 GA at pom 7.3.8: install `brightcove_connector.ui.apps` 6.0.12, confirm
it restores the legacy `/etc` clientlibs as registered libraries, then install `brightcove.all`
7.3.8 over it. Result: legacy `/etc/designs/cs/brightcove` gone (404), new `/apps` library still
serving, both legacy workflow models still present and resolving to Active process components,
one `brightcove.core` bundle Active at the new version, zero OSGi components in failed
activation, and the full e2e suite **45 passed / 1 skipped**, identical to the cloud run from the
same commit. The fresh-install path is covered by the same suite on the cloud instance.

## Upgrade measured on AEM 6.5 LTS, 2026-10-07

A fresh 6.5 LTS author (`cq-quickstart-6.6.0`, Java 21), the published `6.0.12-prem` release
asset (`brightcove_connector.ui.apps-6.0.12.zip`) installed through the package manager, an
account configured under the 6.0.x PID with snake_case keys, the 6.0.12 dataload run, and a
customer-style edit made (a marker in the agent's `jcr:description`). Then the unified
`brightcove.all-7.4.0-prem.zip` installed over it the same way. Re-runnable as
`tests/parity/probes/upgrade/onprem-upgrade-probe.js` (`snapshot` before, `check` after).

| Check | 6.0.12 (before) | 7.4.0-prem (after) |
|---|---|---|
| Connector bundles | `brightcove-services 6.0.12` Active | `brightcove.core 7.4.0` Active only; 0 failed components; running jar sha256 equals the jar in the zip |
| Account config | `BrcServiceImpl`, snake_case | unchanged; read by `LegacyConfigurationServiceImpl`: accounts servlet lists it, `search_videos` returns items |
| `/etc/replication/agents.author/brightcove` | present, enabled, `brightcove://` | exactly one, still enabled, marker preserved, transport unchanged (the merge filter left it alone) |
| `brightcove_admin` on `/` | 7 privileges, whole repository | **unchanged** (see the warning above) |
| `brightcove_admin` on `/content`, `/home/groups`, `/home/users` | none | `rep:write jcr:read crx:replicate`; `jcr:read`; `jcr:read`; no repoinit errors in `error.log` |
| `/apps/brightcove/install`, `/apps/brightcove/runmodes` | present (the release asset carries the bundle and runmode configs) | 404 |
| `/etc/designs/cs/brightcove` clientlibs | 4 registered libraries (`brc.bootstrap.v2`, `brc.html5-player`, `brc.smart-player`, `cq.shared`) | 404; each `brc.*` category has one owner; `/brightcove/admin.html` loads 14 scripts, none twice, none from `/etc` |
| DAM | 54 assets, 52 with `brc_id` | same 54 paths, every `brc_id` unchanged; a dataload re-sync created no duplicate |
| Thumbnail-less videos | imported (the root grant lets the service user read `/apps`) | 26 of 26 re-imported after deleting their assets. Not a discriminating test of `f670f4e` here because the root grant is still present; the 7.x branch for those videos no longer reads `/apps` at all |

Also measured on this bed:

- e2e gate `--platform onprem`: 64 passed / 17 skipped, the same as the fresh 6.5 LTS
  install. The first run failed one Sites spec because the fresh instance had no site
  scaffold (`current/scripts/setup-local-dev.sh` not yet run): environment, not upgrade.
- Activation sync after the upgrade, through the upgraded `brightcove://` agent to a 6.5 LTS
  publish: `dam-publish-tier.spec.js` 5 passed / 2 skipped (the two `test.fixme` pins).
- The upgraded instance authorizes `allowed_groups=[everyone]` on the agent path: with
  `everyone` as the only allowed group and `admin` a member of no declared group, row 31
  passed and `brightcove.log` had no `Not authorized`. A FRESH 7.4.0-prem install (read on
  `/home/groups` and `/home/users` only) behaves the same, so the old `dam-sync-on-activation.md`
  claim that `everyone` can never authorize was wrong; see that doc for the mechanism.

