# Brightcove folder sync on publish

Why an asset's DAM folder becomes a Brightcove folder, which three code paths do it,
and the two traps in the middle.

Read this before touching `FolderSyncUtil` or any of its three callers. If you change
what it describes, update it in the same PR.

## What it does

A customer can organise `/content/dam/brightcove_assets/<accountId>/` into subfolders.
Each synced DAM subfolder carries a `brc_folder_id` property naming the Brightcove
folder it corresponds to (`AssetPropertyIntegrator` writes it when it creates the
folder). When an asset is pushed to Brightcove, the video has to end up in that folder,
and if the DAM subfolder has never been synced, the Brightcove folder has to be created
first.

## The three publish paths

All three push an asset to Brightcove, so all three have to do this:

| Path | Class | Thread it runs on | Retry wait |
|---|---|---|---|
| DAM publish event | `listeners/BrightcovePublishListener` | OSGi EventAdmin delivery | `NO_RETRY_DELAY` |
| Workflow step | `workflow/BrightcoveSyncAssetWorkflowStep` | workflow | `WORKFLOW_RETRY_DELAY_SECONDS` (15s) |
| Classic replication agent | `webservices/BrcReplicationHandler` | replication agent queue | `NO_RETRY_DELAY` |

⚠️ The retry wait is not a tuning knob. The publish listener and the replication handler
run on **shared infrastructure threads**, and sleeping there stalls unrelated work. Only
the workflow step has a thread it owns.

The logic used to be two copy-pasted private methods in the first two classes, and
**missing entirely from the third**: a customer whose publish path still goes through
`/etc/replication/agents.author/brightcove` rather than the listener or the workflow step
got every subfoldered video left at the account root. That is
`ONPREM-PARITY-PLAN.md` §3 Phase 3 item 2: the on-prem 6.0.x line fixed the replication
path in `d923bdd` and `1911f57` and neither fix ever reached this line. The shared helper
is `utils/FolderSyncUtil`; the three classes keep a three-line private delegate so their
own call sites and log wording are unchanged.

## Trap 1: the account root is indistinguishable from an unsynced subfolder

Both lack `brc_folder_id`. Without a guard, an asset sitting directly in
`<damIntegrationPath>/<accountId>` makes the helper "sync" that folder, i.e. create a
Brightcove folder **named after the account id** and move every root-level video into
it. `isAccountRoot` compares the parent's path against `<assetIntegrationPath>/<accountId>`
for every configured account.

**The guard needs OSGi.** `ServiceUtil.getConfigurationGrabber()` goes through
`FrameworkUtil.getBundle()`, which yields nothing outside a container, so the question
genuinely has three answers: yes, no, and **cannot tell**. `isAccountRoot` returns
`Boolean`, `null` for the third, and the helper then skips folder creation rather than
acting on an unanswered question. ⚠️ Do not make it return `boolean`: `false` is a claim
that this is not an account root, and acting on that claim when nothing verified it is
exactly how the spurious folder gets created. `FolderSyncUtilTest` pins the `null`.

The check order also matters, and it was changed when the helper was extracted: the
already-synced-subfolder branch (`brc_folder_id` present) now runs **first**, before the
account-root question is asked. An account root never carries `brc_folder_id`, and moving
a video into a folder that already exists cannot create a spurious one, so that branch
does not need the guard and no longer depends on OSGi being reachable.

## Trap 2: `refresh(true)`, not `refresh(false)`

When `createFolder` comes back empty, the helper re-reads the parent in case a concurrent
publish created the folder. In Oak `refresh()` is **session-wide**, so `refresh(false)`
would discard pending changes made earlier in the same session, including the
`brc_id`/`brc_lastsync` marker BGS-1705 added. Losing half that marker persists
`brc_lastsync` with no `brc_id`, which routes the next publish to `updateVideo` with a
null id. Keep `keepChanges = true`.

## Trap 3: the account id is not always the parent's name

`BrcReplicationHandler.replicateAssets` derives the account from the asset's parent. With
subfolder sync the parent can be a Brightcove subfolder, so the parent's name is a
Brightcove folder id, the account lookup misses, and replication bails out with
"Account not existing". `FolderSyncUtil.resolveAccountId` walks up one level when the
parent carries `brc_folder_id`. The seam `BrcReplicationHandler.accountIdFor(Resource)`
exists so the behaviour is testable without standing up a replication agent.

## How it is proved

`core/src/test/java/.../webservices/BrcReplicationHandlerFolderSyncTest.java` (AEM mocks,
JCR_MOCK for real JCR semantics, mocked `ServiceUtil` as the Brightcove boundary):

1. a new subfoldered video is moved into its Brightcove folder and no folder is created
2. the same on the modified/update path
3. `accountIdFor` walks up past a synced subfolder, and returns the folder's own name at
   the account root
4. an unsynced parent creates nothing while the account-root question is unanswerable

Measured 2026-09-17: reverting all three port points turns 1, 2 and 3 red and leaves 4
green, so the first three discriminate the port and the fourth is the standing guard
against the three-state collapse. The pre-existing BGS-1705 test still passes, which is
what covers the `syncFolder` extraction as a refactor rather than a rewrite.

**Not measured:** a live subfoldered activation through a real replication agent against
the Brightcove account. That needs a DAM subfolder synced to a real Brightcove folder and
an asset activated through `/etc/replication/agents.author/brightcove`, which mutates the
live account. Status: `not measured`, not "works".
