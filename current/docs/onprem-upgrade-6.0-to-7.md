# Upgrading an on-prem AEM 6.5 install from connector 6.0.x to 7.x

Companion to `../ONPREM-PARITY-PLAN.md` (Phase 4). Facts here were measured on AEM 6.5.0 GA on
2026-09-17 while upgrading a 6.0.12 install in place to the unified 7.3.0 build.

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
  `brightcove-delete-asset-workflow` → `brightcove-delete-asset`. Update launchers that
  reference the old names.
- Paths under `/etc/designs/cs/brightcove` and `/etc/clientlibs/brightcove` are no longer
  shipped; component markup includes clientlibs by category, so pages built with the
  connector's components are unaffected. Hand-written references to those paths must move to
  the `/apps/brightcove/clientlibs/*` categories.
