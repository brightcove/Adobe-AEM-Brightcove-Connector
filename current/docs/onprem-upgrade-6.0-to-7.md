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
- WCM Core Components are **not** embedded in the on-prem package (they would replace your
  instance's own Core Components bundle). The connector's admin page uses
  `core/wcm/components/container/v1` and `page/v3`; make sure your Core Components version
  provides them.
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

## The upgrade path that was actually verified

2026-09-17 on AEM 6.5.0 GA at pom 7.3.8: install `brightcove_connector.ui.apps` 6.0.12, confirm
it restores the legacy `/etc` clientlibs as registered libraries, then install `brightcove.all`
7.3.8 over it. Result: legacy `/etc/designs/cs/brightcove` gone (404), new `/apps` library still
serving, both legacy workflow models still present and resolving to Active process components,
one `brightcove.core` bundle Active at the new version, zero OSGi components in failed
activation, and the full e2e suite **45 passed / 1 skipped**, identical to the cloud run from the
same commit. The fresh-install path is covered by the same suite on the cloud instance.
