# Brightcove folder sync on publish

Why an asset's DAM folder becomes a Brightcove folder, which three code paths do it,
and the traps in the middle.

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
for every configured account; `syncFolder` asks the wider question,
`isRootThatMeansNoFolder`, which also covers the integration path root (Trap 6).

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

## Trap 4: a poisoned root (fixed, self-repairing)

Before the guards existed, the bugs in Trap 1 and Trap 6 ran to completion on some
instances: they created the Video Cloud folder named after the account (or after the
integration path's last segment) AND wrote its id to that root's `brc_folder_id`. The
guards only stop NEW folders. With the brc_folder_id branch first, such a root looked like
a synced subfolder, and every root-level activation filed its video into that folder,
indefinitely. On the agent path it did double damage: `resolveAccountId` walked up past the
root and the asset was skipped as "Account not existing".

The rule: **a root means "no folder"**. When `brc_folder_id` is present and
`isRootThatMeansNoFolder` says TRUE:

- the stored id is ignored (no move, no create);
- the property is removed from the root and saved, with a WARN naming the path and the
  stale id;
- ⚠️ the Video Cloud folder is **not** deleted or emptied: a customer may have filed
  videos in it on purpose;
- `resolveAccountId` returns the root's own name.

The repair covers both kinds of root: the account root (`Trap 1`) and the integration path
root (`Trap 6`). The second one matters because the old Trap 6 behaviour wrote a
`brc_folder_id` there whenever it managed to create the folder; without the repair the new
"integration path root means no folder" rule would be bypassed on exactly those instances.

⚠️ If the save fails (e.g. the service user cannot write the root), the property is put
back in the session before returning: the caller commits the BGS-1705 marker on the same
session, and a pending remove it may not persist would fail that commit. The id stays
ignored either way. Do not use `refresh(false)` to drop it (Trap 2).

Three states still hold: only TRUE repairs. UNKNOWN (no OSGi) keeps the id and moves,
which can never create a folder; FALSE is a real subfolder.

## Trap 5: the Video Cloud folder name is already taken

Video Cloud folder names are unique per account, **case-insensitively**: `POST /folders`
with a name that exists answers `409` and `{"error_code":"FOLDER_NAME_IN_USE","message":
"Folder names must be unique (case-insensitive.)"}` (measured 2026-11-20). Video Cloud
folders are also flat, with no parent field (measured: the folder object carries only
`id, account_id, name, created_at, updated_at, video_count`). The DAM is a tree. So two DAM
folders called `clips` under different parents can only ever be ONE Video Cloud folder.

**Before the fix** this was a silent data-loss-shaped bug. `ServiceUtil.createFolder` read
only `id`, so every non-201 arrived as `""`, indistinguishable from a network failure. The
helper retried the same create, got the same 409, logged `No folder created` and returned.
The video stayed at the account root while the activation still logged
`BC: ACTIVATION SUCCESSFUL`. Measured on 7.4.3-cloud: two same-named DAM parents produced
two videos, one folder, and the second video's `folder_id` was `null`.

**Now** a refused create means the folder probably EXISTS:

1. `ServiceUtil.createFolderResponse` keeps the CMS response, so the reason is available
   (`FOLDER_NAME_IN_USE: Folder names must be unique (case-insensitive.)`). It replaces the
   helper's use of `createFolder(String)`, which reads only `id`; that method stays because
   `com.coresecure.*` is an exported package, so it is public API even with no in-bundle
   caller left.
2. `ServiceUtil.findFolderIdByName` looks the name up **case-insensitively** over the
   account's folder list. Case matters: asking for `Clips` when `clips` exists is the same
   collision that made the create fail, so a case-sensitive lookup would miss the folder and
   leave the video unfiled.
3. A match files the video there and writes its id to the DAM folder, so later publishes
   skip the create entirely. ⚠️ Two same-named DAM folders therefore store the **same**
   `brc_folder_id`: that is the flat-namespace consequence, and it is the product question
   below, not something to invent a fix for.
4. No match: ERROR naming the DAM folder path, the requested name and the create's error.
   The video stays at the account root and the DAM folder is left **unmarked**, because a
   stored id would file later videos into a folder that does not exist.
5. The retry is unchanged in shape (wait only where the thread is ours, `refresh(true)`,
   re-read the parent) and now repeats create → lookup, so a folder created concurrently is
   found either way.

The lookup costs one GET of the account's folder list, on the failure path only. ⚠️ The
folders endpoint **ignores `limit` and `offset`** and returns the whole list (measured: 110
folders returned with `limit=2` and with `offset=100`), so a single GET is complete and
there is no page loop to write; `CmsAPI.getFolders` still sends `offset=0` only. A folder
list that cannot be read is reported as "no such folder", because the CMS client swallows
read errors: that degrades to the loud ERROR above, never to a false success.

## Trap 6: the integration path root means "no folder"

`/content/dam/brightcove_assets` (the configured `damIntegrationPath`) holds one folder per
account, not folders a customer made for videos.

The old behaviour gave an asset sitting directly in it a Video Cloud folder named after the
path's last segment (`brightcove_assets`). On an account that already has such a folder the
create was refused, which is Trap 5, and the video was left unfiled (measured 2026-11-20,
claim C4: `folder_id` null, two 409s). On a clean account it instead created a folder that
has nothing to do with the asset, and wrote its id onto the integration path root.

**Decision: the integration path root means "no folder", like the account root.** No create,
no move, no `brc_folder_id`, and a WARN naming the path. Filing the video into a Video Cloud
folder that merely shares the path segment's name would attach it to an unrelated folder.

`isRootThatMeansNoFolder` answers for both kinds of root in one configuration pass, with the
same three states as Trap 1: TRUE for either root, FALSE for a customer folder, `null` when
the configuration cannot be read (warn, skip, create nothing). Because the stored-id branch
is checked first, a root that already carries `brc_folder_id` takes the Trap 4 self-repair,
which is what stops this rule being bypassed on instances the old behaviour touched.

⚠️ Not covered here: the listener's path scoping is a substring match
(`asset.contains(damIntegrationPath)`), so a sibling tree such as
`/content/dam/brightcove_assets_archive` is treated as managed. That is a separate defect
(measured 2026-11-20, claim C8) and changing it is out of scope for folder sync.

## Out of scope: same-named DAM folders share one Video Cloud folder

The flat Video Cloud namespace cannot represent two DAM folders with the same name, so today
they share one Video Cloud folder (Trap 5). The alternative is disambiguating the name with a
path prefix (e.g. `branchB/same`, or `branchB-same`). That changes every folder name a
customer sees in Studio, needs a product decision, and needs a migration story for folders
that already exist. Deliberately **not** done here: do not "fix" Trap 5 by inventing a naming
scheme without that decision. Recorded 2026-11-20.

## Default folder properties

The default DAM integration folder's `jcr:content` (`/content/dam/brightcove_assets/jcr:content`)
ships three properties from `ui.content`: `jcr:title` "Brightcove Connector", `cq:conf`
`/conf/brightcove`, and `metadataSchema` (the Brightcove schema, which is what puts the
"Brightcove" tab on the asset metadata editor in that folder).

⚠️ Its filter mode differs per platform. `ui.content`'s filter is a template,
`ui.content/src/main/vault-filter/filter.xml`, carrying `mode="${brightcove.damFolderFilterMode}"`;
`ui.content/pom.xml` substitutes it into `target/vault-filter/filter.xml` and uses that copy as the
`filterSource`. The value comes from the root pom: `merge_properties` by default (cloud), `merge` in
the `onprem` profile. ⚠️ The template must not go back under `src/main/content/META-INF/vault/`:
filevault plugin 1.3.6 fails the build when that file exists and a different `filterSource` is set.

⚠️ The node is serialized in its own file, `brightcove_assets/_jcr_content/.content.xml`, not inline
in the folder's `.content.xml`. FileVault honours `merge` only on an aggregate root node
(JCRVLT-255); inline, the package validator fails the on-prem build ("Non-empty Node ... is
supposed to be imported with mode 'merge' but it is not the aggregator's root node"). The folder's
own `.content.xml` only declares `sling:OrderedFolder` and is outside the filter. The onprem build
still packages the same `ui.content` module (the `onprem` profile only ADDS `ui.content.onprem` and
`ui.config.onprem`), which is why the switch has to live in that one module's filter rather than in
an on-prem-only module.

⚠️ On-prem must not use `merge_properties`: FileVault 3.2.8, which classic AEM 6.5 service packs
ship, rejects it at install (`No enum constant org.apache.jackrabbit.vault.fs.api.ImportMode.MERGE_PROPERTIES`,
HTTP 500 from the package manager), so `brightcove.ui.content` fails to install. The mode needs
runtime FileVault 3.5.0 or later (JCRVLT-255). 6.5 LTS ships a newer FileVault and installs it,
which is how the 7.4.0 change (PR #139) passed the 6.5 LTS gate and still broke a 6.5 SP install.

On AEMaaCS the package is installed on every pipeline deploy, so the mode decides what happens on
every deploy, not just at upgrade.

| Situation | Cloud (`merge_properties`) | On-prem (`merge`) |
|---|---|---|
| Fresh install, or the `jcr:content` node is missing | All three written | All three written |
| Existing folder without a schema (upgraded from 6.0.x / 7.2.x) | Missing `metadataSchema` and `cq:conf` added: the Brightcove tab appears | 🔴 Nothing added: no Brightcove tab until the schema is assigned (gap below) |
| Customer renamed the folder or chose their own schema | Kept | Kept |
| Other properties on the node (e.g. `processingProfile`) | Untouched | Untouched |
| A customer's own `damIntegrationPath` folder | Not covered by this filter | Not covered by this filter |

🔴 On-prem upgrade gap: an existing folder `jcr:content` that lacks `metadataSchema` keeps lacking
it, because `merge` never touches an existing node. Workaround: assign the Brightcove schema to the
folder in the Assets UI (folder properties, Metadata Schema), or delete the folder's `jcr:content`
before upgrading if nothing on it is worth keeping. A repoinit `set properties on ... default ...`
statement would set it only when absent, but it is not usable here: AEM 6.5.0 GA ships
`org.apache.sling.repoinit.parser 1.2.2` (and `org.apache.sling.jcr.repoinit 1.1.8`), which has no
`set properties` operation at all and rejects it with `RepoInitParsingException: Encountered "set"`
(parsed offline against the 1.2.2 jar, 2026-10-19), and a parse error aborts the whole script
(`repoinit.md`). Closing the gap would need code (e.g. a startup check in `brightcove.core`), not
packaging.

Alternatives that were shipped or considered:
- `update` (7.4.0 pre-release): guaranteed the tab but reset a customer's title, `cq:conf` or
  schema on every install.
- `merge` everywhere: keeps customer values, but on cloud an existing folder would never get the
  schema. Kept on-prem only, where `merge_properties` is not installable.
- `merge_properties` everywhere (7.4.0, 7.4.1): broke the on-prem install on 6.5 SPs.
- Moving the filter entry into an on-prem-only and a cloud-only module: there is no cloud-only
  content module, the node's content is in `ui.content`'s `.content.xml`, and two packages owning
  the same root depend on install order. Rejected for the property substitution above.

Build requirement: FileVault package plugin 1.1.6 (FileVault 3.4.8) rejects `merge_properties`
at build time; the plugin is 1.3.6 (the AEM archetype's version). The cloud instances apply the
filter with their own FileVault (4.1.x on the AEMaaCS SDK, 3.8.0 on 6.5 LTS); both support it.

Pinned by:
- `scripts/check-dam-sync-packaging.py <cloud zip> <prem zip>`: the packaged filter of the
  embedded `brightcove.ui.content` must say `merge_properties` in the cloud zip and `merge` in the
  `-prem` zip, and no package in the `-prem` zip may use `merge_properties` anywhere.
- `tests/parity/probes/dam-folder-filter-mode.sh <aem> <ui.content zip> <update|merge|merge_properties>`,
  which sets a customer value, installs, and asserts the result for the situations above
  (`merge` asserts the upgrade-gap row as it is: schema stays absent). It discriminates: the
  `update` build passes as `update` and fails as `merge_properties`. Run on the AEMaaCS SDK author
  and 6.5 LTS author for `merge_properties`, and on the 6.5.0 GA author for `merge`.

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

Trap 5 and Trap 6 (`utils/FolderSyncUtilCollisionTest`, `sling/ServiceUtilFolderNameTest`,
and one collision test per caller: `listeners/BrightcovePublishListenerFolderSyncTest`,
`workflow/BrightcoveSyncAssetWorkflowStepFolderSyncTest` and
`webservices/BrcReplicationHandlerFolderSyncTest`, each driving its own entry point rather
than the helper). Red on the pre-fix code: the collision test asserted a `moveVideoToFolder`
that never happened (the helper retried the refused create and gave up), and the
integration-root test asserted no create while the old code created a folder named after the
path segment. Reverting `FolderSyncUtil` to `origin/cloud-master` turns all three callers'
collision tests red (measured 2026-11-20), so each caller's wiring is pinned, not just the
helper's. `ServiceUtilFolderNameTest` pins the matching itself
(case-insensitive, a prefix is not a match, an unreadable list resolves to nothing) because a
mocked `ServiceUtil` would leave that logic untested. `theRetryFindsAFolderCreatedWhileTheWorkflowWaited`
proxies the node to make `refresh(boolean)` a no-op: ⚠️ JCR_MOCK cannot do it at all
(`AbstractItem.refresh` throws whatever the flag), so the retry branch is otherwise unreachable
in a unit test even though a real Oak session runs it.

Live, 2026-11-20, on the 7.4.3 artifact before the fix (`validation-2026-11-20/`): two
same-named DAM parents produced one folder and the second video's `folder_id` was `null`, and
an asset in the integration path root was ingested with `folder_id` null after two 409s.

Live again on the fixed bundle, same day, with the 7.4.3 jar swapped back as the red control
(`validation-2026-11-20/qa-folder-name-fix.md`, listener path only): the two same-named
parents now share one Video Cloud folder and both videos carry its id; a case-only collision
resolves to the existing folder; two concurrent activations produced one folder with both
videos filed; an integration-path-root asset is ingested unfiled with the WARN and no create
attempt; a stale `brc_folder_id` on either root is ignored and removed. The red control failed
both of the first and fourth cases on 7.4.3, so the cases discriminate. Not measured live:
the workflow-step and replication-agent callers (unit-tested only), and a collision against a
Video Cloud folder list large enough to hit an undocumented cap.

Live through the real replication agent, 2026-10-07, 6.5 LTS author with the on-prem
`/home` read grant: an asset in a never-synced DAM subfolder got a Video Cloud folder
named after the DAM folder, the id written back, and the video moved in; a DAM move plus
re-activation moved it to the new folder (`tests/e2e/specs/dam-publish-tier.spec.js`
rows 32 and move, `tests/parity/matrix.md` rows 32 and 44). Still not measured live:
nested subfolders (two synced levels) through the agent.
