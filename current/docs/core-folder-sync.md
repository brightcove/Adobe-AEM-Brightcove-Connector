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
already-synced-subfolder branch (`brc_folder_id` present) runs **first**. Moving a video
into a folder that already exists cannot create a spurious one, so on that branch an
UNKNOWN answer still moves. But the assumption that "an account root never carries
`brc_folder_id`" is false on instances the pre-guard bug hit: see Trap 4.

## Trap 2: `refresh(true)`, not `refresh(false)`

When `createFolder` comes back empty, the helper re-reads the parent in case a concurrent
publish created the folder. In Oak `refresh()` is **session-wide**, so `refresh(false)`
would discard pending changes made earlier in the same session, including the
`brc_id`/`brc_lastsync` marker BGS-1705 added. Losing half that marker persists
`brc_lastsync` with no `brc_id`, which routes the next publish to `updateVideo` with a
null id. Keep `keepChanges = true`.

## Trap 3: the account id is not always the parent's name

`BrcReplicationHandler.replicateAssets` derives the account from the asset's parent. With
subfolder sync the parent can be a subfolder at any depth, synced or not, and its name is
then a folder name where an account id is expected: the account lookup misses and
replication bails out with "Account not existing" before any folder can be created
(matrix row 32, measured live on the agent path 2026-10-07 for a never-synced subfolder).

`FolderSyncUtil.resolveAccountId` now resolves by PATH, as the listener and the workflow
step do: the configured account whose `<integrationPath>/<accountId>` is the folder or an
ancestor. That covers unsynced, nested and poisoned (Trap 4) folders. Only when no
configuration can be read does it fall back to walking up while the folder carries
`brc_folder_id`.

The walk also tests `isAccountRoot` as a stop condition, but that stop can never fire: the walk
only runs when `accountIdByPath` returned null, which means either the configuration could not
be read (then `isAccountRoot` is null too, not TRUE) or the folder is outside every
`<integrationPath>/<accountId>` (then none of its ancestors is an account root either, or the
path lookup would have matched). A poisoned root (Trap 4) is handled by the path lookup, not by
this stop. The condition is left in place as harmless; nothing pins it. The seam
`BrcReplicationHandler.accountIdFor(Resource)` exists so the behaviour is testable without
standing up a replication agent.

## Trap 4: a poisoned account root (fixed, self-repairing)

Before the guard existed, the bug in Trap 1 ran to completion on some instances: it
created the Video Cloud folder named after the account AND wrote its id to the account
root's `brc_folder_id`. The guard only stops NEW folders. With the brc_folder_id branch
first, such a root looked like a synced subfolder, and every root-level activation filed
its video into that folder, indefinitely. On the agent path it did double damage:
`resolveAccountId` walked up past the root and the asset was skipped as "Account not
existing".

The rule: **the account root means "no folder"**. When `brc_folder_id` is present and
`isAccountRoot` says TRUE:

- the stored id is ignored (no move, no create);
- the property is removed from the root and saved, with a WARN naming the path and the
  stale id;
- ⚠️ the Video Cloud folder is **not** deleted or emptied: a customer may have filed
  videos in it on purpose;
- `resolveAccountId` returns the root's own name.

⚠️ If the save fails (e.g. the service user cannot write the root), the property is put
back in the session before returning: the caller commits the BGS-1705 marker on the same
session, and a pending remove it may not persist would fail that commit. The id stays
ignored either way. Do not use `refresh(false)` to drop it (Trap 2).

Three states still hold: only TRUE repairs. UNKNOWN (no OSGi) keeps the id and moves,
which can never create a folder; FALSE is a real subfolder.

## How it is proved

`core/src/test/java/.../webservices/BrcReplicationHandlerFolderSyncTest.java` (AEM mocks,
JCR_MOCK for real JCR semantics, mocked `ServiceUtil` as the Brightcove boundary):

1. a new subfoldered video is moved into its Brightcove folder and no folder is created
2. the same on the modified/update path
3. `accountIdFor` walks up past a synced subfolder, and returns the folder's own name at
   the account root (no configuration: the fallback walk); with configuration, unsynced
   and nested subfolders resolve to the account by path
   (`unsyncedAndNestedSubfoldersResolveToTheAccountByPath`), and
   `FolderSyncUtilNestedSubfolderTest` (was a disabled red pin) covers two synced levels on
   the fallback. Reverting the path resolution turns both red
   (2026-10-07, matrix row 32).
4. an unsynced parent creates nothing while the account-root question is unanswerable

Measured 2026-09-17: reverting all three port points turns 1, 2 and 3 red and leaves 4
green, so the first three discriminate the port and the fourth is the standing guard
against the three-state collapse. The pre-existing BGS-1705 test still passes, which is
what covers the `syncFolder` extraction as a refactor rather than a rewrite.

Trap 4 (`utils/FolderSyncUtilPoisonedRootTest` plus one poisoned-root test per caller:
`BrcReplicationHandlerFolderSyncTest`, `workflow/BrightcoveSyncAssetWorkflowStepFolderSyncTest`,
`listeners/BrightcovePublishListenerFolderSyncTest`). The account-root answer comes from
the test seam `FolderSyncUtil.configurationSource`, set through `FolderSyncUtilSeam` in
test sources; tests must reset it. Reverting the two Trap 4 checks turns the poisoned-root
test of every caller and of the helper red, plus the `resolveAccountId` one; the
real-subfolder and UNKNOWN controls stay green. The UNKNOWN control also asserts the move to
the stored id still happens. `failedRepairSaveRestoresThePropertyWithItsOriginalType` drives
the save-fail branch (a session whose `save()` is denied) with a LONG-typed id: the property
is back in the session with its type; it fails if `restore` is skipped or writes a String.
Live, cloud author, 2026-10-07: a
root-level activation on the poisoned root left the video in no folder and removed the
property (`tests/parity/matrix.md` row 31).

Live through the real replication agent, 2026-10-07, 6.5 LTS author with the on-prem
`/home` read grant: an asset in a never-synced DAM subfolder got a Video Cloud folder
named after the DAM folder, the id written back, and the video moved in; a DAM move plus
re-activation moved it to the new folder (`tests/e2e/specs/dam-publish-tier.spec.js`
rows 32 and move, `tests/parity/matrix.md` rows 32 and 44). Still not measured live:
nested subfolders (two synced levels) through the agent.
