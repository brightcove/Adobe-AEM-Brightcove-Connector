# On-prem-only commit ledger (Phase 3)

Every `onprem-master` commit with no patch-equivalent on `cloud-master` (`git cherry origin/cloud-master origin/onprem-master`, 2026-09-17).
Status is one of: `present` (cloud already has the behaviour) · `ported <sha>` · `obsolete <reason>` · `todo`.
Version bumps and whitespace commits are pre-classified `obsolete`.

| Date | SHA | Subject | Files | Status |
|---|---|---|---|---|
| 2021-06-02 | `1f8da141d790aed62dbb7d98e8e31bb16455cede` | updates to how the ConfigurationService configurations bind to fix a bug with multiple accounts | 2 | present — `ConfigurationGrabberImpl.bindConfigurationService(ConfigurationService, Map)` already keys the map by `config.getAccountID()` instead of a `ServiceReference` custom key |
| 2021-06-02 | `91c6323e4e2018227131f1b244375c91618291dc` | whitespace updates to POM files | 2 | obsolete (version/housekeeping) |
| 2021-07-13 | `7d9abf33da165e5c4f0c24f766a876fe672676f1` | fixed AEM_NO_DAM tag default issue | 1 | present — `CmsAPI.getVideosCount(String q)` already delegates with `dam_only=true` |
| 2021-07-13 | `f47186d76ce561206b5bed1688b71cde58e1af78` | updated other instance of AEM_NO_DAM logic issue | 1 | present — `CmsAPI.getVideos(q,limit,offset,sort)` already delegates with `dam_only=true` |
| 2021-07-26 | `46045d3462e7c1785acb87fa3bcea8058c91fcba` | updated dialog dropdown logic to support proxy components | 5 | present — part of dialog-dropdown feature; `clientlib-dialogs/js/dynamic_dropdown.js` already uses the `.brightcove-dialog-*-dropdown` granite:class selectors and account-scoped autocomplete this commit introduced |
| 2021-07-26 | `132871d2b781f0b0f58d73633a5d7367af4c4b76` | removed SNAPSHOT from packages | 3 | obsolete (version/housekeeping) |
| 2021-08-24 | `d9b3a820c6678e2a1bffa4299e9dd677b6ef6486` | Updates to ensure latest connector is recognizable | 3 | present — part of dialog-dropdown feature; same `dynamic_dropdown.js` autocomplete-video wiring is on HEAD; Java 1.8 baseline matches cloud's `core/pom.xml` |
| 2021-08-26 | `9d8f23a82211e6bc476d284b14ef7d3a910fd219` | updated to fix the dropdown issue with multiple accounts BC-33 | 2 | present — part of dialog-dropdown feature; `account_id` param on the video/playlist fetch is on HEAD's `dynamic_dropdown.js` |
| 2021-10-05 | `645887ee5db6af14a044f21a3f378658d722b422` | Added field length limitations to Brightcove metadata tab; Swapped Coral Select for Coral Autocomplete in Brightcove Video Player component | 3 | present — `autoDialog.jsp` has the exact `maxlength` values (250/5000/250/255) on the description/link fields; player dialog already uses `autocompletevideo` |
| 2021-10-07 | `a2d76c6220c5cffdf4ad50de4115a7684f1e4223` | AutoDialog update for non-BC assets | 2 | present — `autoDialog.jsp` still renders the "This resource is not managed by Brightcove…" notice block verbatim, and the search limit is already 20 |
| 2021-10-07 | `68bd1c0ea5411e237d6efc49b6f20cadc12271c9` | version bump to release | 3 | obsolete (version/housekeeping) |
| 2021-10-14 | `befcebf8d68be328165f48e03a2dddef81f1750e` | BC-55: fix to dynamic player dropdown for multiple player accounts | 1 | present — part of dialog-dropdown feature; `dynamic_dropdown.js`'s player-list fetch already sends `account_id` |
| 2021-10-26 | `8d2534930bd3a4cffb367f3944142e6b157296d0` | In-Page Experience Fixes | 3 | present — `clientlib-dialogs/js/experiences.js` already has the `isBrightcoveDialog()`/`updateQueryStringParameter()` pattern this commit introduced |
| 2021-10-31 | `fd4f9c92bdc249b0b3878ab2b3ba4993c3c38cd3` | BC-57 issue with playlist component | 2 | present — part of dialog-dropdown feature; playlist-dropdown `items.clear()`/`account_id` logic matches `dynamic_dropdown.js` on HEAD exactly |
| 2022-04-07 | `5f9629d1ddb1c8e965b9768d13cdf2fbd5d6f63b` | BC-58 - removed legacy package path | 4 | present — cloud's `ui.apps` filter.xml has no `/libs/cq/gui/content/common/links/brightcove` entry; the classic-UI Tools link this commit deleted was never re-added |
| 2022-04-07 | `ad56a3213f2552bcb49b70527b458580e3bdd34d` | Updated Brightcove Loading Animation and Icon | 2 | obsolete — superseded by the BCON-era admin-tool header/spinner redesign; current `brightcoveadmin.html` has no `.loading`/`.loader` block or PNG loader image at all, replaced by the `.brc-spinner`/`.brc-table-spinner`/`.brc-sync-spinner-*` system in `clientlib-tools/css/style.css` and `brcUI.js` |
| 2022-04-07 | `85e847f41fd7adbf0a432be710a344cbbceb50ac` | BC-57 - Updated Video Dropdown to Include Name and ID in Values | 3 | present — `VideoPlayer.java` has the identical `Pattern.compile("\\[(.*?)\\]")` bracket-ID extraction; `BrcApi.java`'s dropdown-list builder emits the identical `"name [id]"` format; `filter.xml` no longer carries the `/apps/brightcove/install` exclude or the `/libs` filter this commit touched |
| 2022-04-07 | `c501c9565dba77dc93395f7079813877645d4d53` | version update | 3 | obsolete (version/housekeeping) |
| 2022-04-11 | `4f4b6672b238f8c23e620c0a5405ae0f3e3f9864` | BC-59 :: Fixed an issue with aggressive encoding of query parameters like the colon | 1 | present — `CmsAPI.java`'s video-search URL builder still has `.replace("%253A", ":")` |
| 2022-06-22 | `447df943c4fde57621873b43441a240c518e5480` | labels dropdown and API methods; initial clips UI support | 7 | present — `list_labels`/`get_videos_with_label` actions, `CmsAPI.getLabels()`/`getVideosWithLabel()`, the `#filter_clips` checkbox and `.state-clip` badge are all on HEAD verbatim, including a `BCON-121` comment anchored to the same selectors |
| 2022-06-22 | `144153119f107caffac090bbe0556418f8084319` | fixed video count in console when searching by folder or label | 2 | present — `Constants.TOTALS` is set on the videos/folders/labels result objects in `ServiceUtil.java` exactly as this commit added |
| 2022-06-23 | `bf634185590a50ec7036c82258a4b652c3728916` | Updated BC logo on header | 4 | obsolete — the whole admin-tool header was rewritten (`.brc-header`, BCON-19x Figma pass); there is no navbar or logo `<img>` left to theme, and the `#080886` navbar colors this commit introduced are gone with it |
| 2022-07-28 | `1ee4eb9ea1c3f6e419aef22399145b28a9334c36` | Updated logging and testing library dependencies | 2 | obsolete (version/housekeeping) — pure slf4j/jacoco version bumps, no behavior change |
| 2022-07-28 | `b3ed2570dea4b9717cf5cea1494bc7cadd4be82d` | updated release version to 6.0.0 | 3 | obsolete (version/housekeeping) |
| 2022-07-28 | `eb27f28a0f54609d0f6f690ff9116cfcd36d9c29` | Removed dependency on older Bootstrap; updated styles | 9 | obsolete — the `navbar`/`nav-pills` markup and `$.browser` IE-compat hacks this commit touched don't exist on HEAD at all; the whole admin-tool shell was rebuilt without Bootstrap's navbar or IE support |
| 2022-07-28 | `c97283308c0cdb5f18c9680bc2c1cf0192b8f0ad` | updated jQuery to 3.5.1 | 1 | obsolete (version/housekeeping) — vendored-jQuery bump under the same rewritten, Bootstrap-free admin tool |
| 2022-07-31 | `63a2ce62b7180272bdcf2f35e8b033ec778bb7f5` | updated license files | 2 | obsolete (version/housekeeping) |
| 2022-08-04 | `8090292b76e7e202766c1430124df7ab511c1432` | fixed metadata preview and playlist header styles | 3 | obsolete — target markup (`ul.thumbnails`, inline `<pre>` short-description) no longer exists; HEAD's video-detail panel sets `divMeta.shortDescription` via `.textContent` on a styled table cell instead |
| 2022-08-04 | `80ca1a53df596af83a6c5ab09fe5b9ce52cfce15` | updated playlist creation logic and styles | 9 | present — `CmsAPI.createBlankPlaylist()`, `ServiceUtil.createPlaylist(String)`, and the `create_blank_playlist` API action all match verbatim |
| 2022-08-05 | `dd6b81380d5a48bfc9b130ef4912d4e4826b700c` | labels schema support; junit dependency version bump | 5 | obsolete (version/housekeeping) |
| 2022-08-11 | `a3010dc7cc78371a94b2161b2888d2c0c9d5f5b4` | initial UI for labels | 6 | present — part of labels feature (a3010dc/b3b92cf/a09d242); `Video.java` carries the `labels` field, `ServiceUtil.java` (~line 1338) still reads `brc_labels` off the DAM asset for sync-back, and the labels pills UI in `brightcoveadmin.html`/`brcUI.js` is the direct descendant of this commit's label-link rendering |
| 2022-08-11 | `b3b92cf29b63a4bb646796fb3d1870a765a278ae` | better label support; creating labels support | 6 | present — part of labels feature; `CmsAPI.createLabel()` / `BrcApi.createLabel()` / the `create_label` action are structurally identical to this commit's code (same `Constants.LABEL`/`Constants.ID`/409-on-duplicate logic) |
| 2022-08-12 | `a09d24278bd96a68d7dc66cb266f7617c259ddbd` | label saving support | 6 | present — part of labels feature; `CmsAPI.updateLabels()` / `BrcApi.updateLabels()` / the `update_labels` action match verbatim |
| 2022-08-13 | `58b584ab48d45c8348b3b4ad0f772df501b1e782` | multilingual variant display in admin | 5 | present — `Video.java` carries the `variants` field and `Constants.VARIANTS`; `brcUI.js` still has `showVariants()` and the `.variant` click handler this commit added |
| 2022-08-16 | `144f9b53a556f65e1bd7906f0c63daf17f6a89c0` | todo cleanup; variant polish; folder sync started | 7 | present — part of subfolder-sync feature (144f9b5/8ee7087/d923bdd/ec5819d/1911f57); the `folderId`/`Constants.FOLDER_ID` field this commit added to `Video.java` is on HEAD; the "folder sync started" TODO comments were only comments, no code |
| 2022-08-16 | `8ee708780a8262cc100fd0be99c41b7ab8c565a1` | initial subfolder sync | 5 | present — part of subfolder-sync feature; `AssetPropertyIntegratorRunnable.java`/`AssetPropertyIntegrator.java` on HEAD still run the identical `brc_folder_id` JCR-SQL2 lookup, rename-detection, and `sling:OrderedFolder` creation (ported to Jackson `ArrayNode`/`ObjectNode`, same logic) |
| 2022-08-16 | `d923bdd6258bad7227fbb88f02b8e1d7c0206918` | subfolder replication support; autoDialog updated for subfolders | 6 | port S — part of subfolder-sync feature. `VideoImportCallable.cleanPath(confPath,filename,folderId)` and `autoDialog.jsp`'s `parentNode.hasProperty("brc_folder_id")` walk-up are present. Gap: `BrcReplicationHandler.java` (`webservices/BrcReplicationHandler.java` ~line 187, `getReplicationResult`) still does plain `account_id = parent.getName()` with no subfolder-aware walk-up, so an asset synced via the classic replication `TransportHandler` (still an active `@Component(service = TransportHandler.class)`) in a subfolder resolves the wrong account. |
| 2022-08-17 | `ec5819d31eee7cc4b1e3c5b93b1ada7485330668` | fixed replication issue | 1 | present — `ServiceUtil.java` (~line 1045) sets `Constants.BRC_LASTSYNC` inside the per-field loop before `resourceResolver.commit()`, matching this commit's reordering fix |
| 2022-09-02 | `792f3a21203abdecbe8310b202414aec57131bbb` | fixed issue with null width and/or height for video player component | 1 | present — `player-embed.html` still uses `data-sly-attribute.data-width="${player.width ? player.width : ''}"` (and `data-height`) |
| 2022-09-28 | `1911f571aed0278025032781062174adcd997fc9` | fixed issue with subfolder video activations | 1 | port S — part of subfolder-sync feature. `BrcReplicationHandler.java`'s `activateVideo`/`deactivateVideo` (after both `serviceUtil.updateRenditions(_asset, video)` calls, ~lines 379 and 407) have no `brc_folder_id`/`serviceUtil.moveVideoToFolder(...)` block, so a video activated/republished in a subfolder via the legacy replication agent never gets moved to its Brightcove folder. `ServiceUtil.moveVideoToFolder(String,String)` already exists on HEAD with the same signature, so this is a same-file, two-call-site port. |
| 2022-09-29 | `4009329fbb7350f7dfbf6f3017cc55cbcf7c8c01` | bumped version number | 3 | obsolete (version/housekeeping) |
| 2023-04-04 | `f90d3b0f64a347d73db65e6e17b2cb540dbbaed2` | update to lodash dependency to support 6.5.10+ | 1 | obsolete — the clientlib this commit patched (`page/brightcoveplayer/clientlib`) has no `dependencies` attribute at all on HEAD, and `listener.js` doesn't reference `_`/underscore/lodash anywhere; the dependency was dropped rather than needing the `lodash.underscore` alias |
| 2023-04-04 | `9cc8b91187738acb8aab765afc4492fb69c77fa4` | version updates to 6.0.2 | 3 | obsolete (version/housekeeping) |
| 2023-06-11 | `afa58e79146d39f3ac9869be8335a5893c9474fd` | fixes for playlist modal height and incorrect target logic on adding items to playlist | 2 | port S — `.pml-dialog` on HEAD still lacks `align-items:center`/`justify-content:center`, `.pml-dialog_container` is still `position:absolute` (not the flex-centered layout this commit introduced), `.pml-dialog .playlist-listing`/`.label-listing` still lack `max-height`/`overflow-y:auto`, and `showPopup()` still calls `$popup.show()` instead of `.css('display','flex')`. Separately, `brcUI.js` still has `$item.localName == 'img'` at two call sites (label-add and playlist-add autocomplete click handlers) — `.localName` doesn't exist on a jQuery object, so the check is always false; the on-prem fix (`$item.get(0).nodeName == 'IMG'`) was never carried forward. |
| 2023-06-14 | `0f1217091f952983e97d30fe28a04b7b63d7d860` | version bump to 6.0.3 | 3 | obsolete (version/housekeeping) |
| 2023-12-04 | `ccdaeb2fde4c2dcf19af42436e07ce97df8ef7cd` | fixed issue with full scroll import of assets not respecting AEM_NO_DAM tag filter | 2 | port S — `ServiceUtil.getList(Boolean exportCSV, int offset, int limit, boolean full_scroll, String query, String sort)` (the 6-arg overload, ~line 200) still hardcodes `dam_only=false`, unlike this commit's fix (`true`) and unlike the sibling `CmsAPI.getVideos`/`getVideosCount` overloads which already default `true` (see rows 3-4 above). One-line default flip. |
| 2024-01-14 | `b4b122eb8c50547d83f94741bd82c825810c8c49` | BC-90 experience player fix | 4 | present — `ExperiencePlayer.java` has the identical `Pattern.compile("\\[(.*?)\\]")` bracket-ID extraction for `experienceID`, and `brightcoveexperiences.html`'s iframe `src` already uses `${player.experienceID}` |
| 2024-01-14 | `308f42caed2e74ab7b3ce41ad193ce50ca875885` | added change event to switch account ID in experience player | 1 | present — `clientlib-dialogs/js/experiences.js`'s account-`change` listener that updates the autocomplete `data-granite-autocomplete-src` matches verbatim |
| 2024-01-14 | `c7d573c6ad38e37badbe82ebba0466555c928c37` | bumped version in pom files | 3 | obsolete (version/housekeeping) |
| 2024-02-14 | `c100a16f1587f622be6825b1af291d038aad492d` | ContextPath Prefixed - Multiple Occurrences | 9 | port L — part of context-path feature (c100a16/cf674b7), needs design per plan §1.4/§3. Confirmed zero `contextPath`/`getContextPath` references anywhere in cloud's `ui.apps` JS. Live surfaces still needing the fix: `clientlib-tools/js/brcUI.js`, `brcAdmin.js`, `clientlib-dialogs/js/dynamic_dropdown.js` and `experiences.js`, `page/brightcoveplayer/clientlib/listener.js`, and — importantly, since it's the modern Touch UI surface, not a removed one — `clientlib-assetfinder/source/js/asset_content_finder.js` (categories `cq.authoring.editor.hook.assetfinder`), which makes unprefixed `$.getJSON("/bin/brightcove/…")`/`$.ajax` calls today. The `extensions/contentfinder/{brightcovePlayers,brightcovePlaylist,brightcoveVideo}.js` portion of this commit is obsolete (ExtJS sidekick, confirmed absent from cloud's `extensions/` tree entirely; only `extensions/console/*` classic-UI config survives). |
| 2024-03-01 | `cf674b75ade8c509687deeb5187d6b14164effd4` | Falsy check added for all contextpath calls | 4 | port L — same context-path feature and same file set as c100a16; the `(CQ.shared.HTTP.getContextPath() \|\| '')` falsy-guard refinement is needed wherever the new helper is added. The `extensions/contentfinder/brightcovePlayers.js` portion is obsolete for the same reason (ExtJS sidekick, removed). |
| 2024-03-13 | `748bc76fbd172550f75344f2695f22be8566e847` | Adding in new way to sync from AEM to brightcove | 25 | present — `BrightcoveSyncAssetWorkflowStep.java` and `BrightcoveDeleteAssetWorkflowStep.java` exist on HEAD (further evolved past this commit's version, see cdf5d26 below). Cloud registers the equivalent workflow models under different names (`bc-sync-new-asset`/`brightcove-delete-asset` vs. this commit's `brightcove-sync-asset-workflow`/`brightcove-delete-asset-workflow`, per plan §1.2); reconciling the model names is scoped to Phase 4's legacy-name overlay, not a Phase 3 port. |
| 2024-10-03 | `cdf5d26f1efd43cdbb674c09aa462bcafad37c33` | Fixing syncing issue with multiple files | 5 | present — `BrightcoveSyncAssetWorkflowStep.java` on HEAD already threads `brightcoveAssetId` as a return value/local parameter through `activateAsset`/`activateNew`/`activateModified`/`syncBrightcoveData` rather than a shared instance field, matching this commit's concurrency fix |
| 2024-10-04 | `8ff0b48a50ca78beece748807b11efd7016db270` | updating version number | 3 | obsolete (version/housekeeping) |
| 2024-11-19 | `3b27f2f119a93b4d1d96928e667c5a85390ff9d0` | Adding ability to see all playlists | 5 | present — part of playlists feature (3b27f2f/48b94cc); `BrcApi.java` and `GetLocalAssetList.java` both call `serviceUtil.getPlaylists(..., full_scroll=true)` |
| 2024-11-20 | `48b94cc746414c9c1819b6ba0a1f3b1347297baf` | removing pagination on playlists | 5 | present — part of playlists feature; `#pagination` div id and `doPageList(total, type)`'s `if (type !== "Playlists")` guard hiding the page selector for playlist listings both match verbatim |
| 2024-11-21 | `bb0462da74913ddcc8e9dc780070083f41fb839d` | Updating Brightcove Metadata and logging | 5 | present — `ServiceUtil.setLink()`'s `objObject.has(Constants.URL)`/`has(Constants.TEXT)` null-guards and `autoDialog.jsp`'s account-resolution guard (including the pre-existing `StringUtils.isNotBlank("brc_id")` literal-string quirk this commit introduced) both match HEAD verbatim |
| 2025-02-13 | `d3743eac836e3c79791a6df4b8d575704e647bde` | Fixing proxy error on rendition creation | 4 | present — the per-call proxy plumbing this commit added to `ServiceUtil.setImages()` is now centralized: `getRenditionInputStream(src)` calls `HttpServices.getSSLConnection(...)`, which honors a shared static `PROXY` (`HttpServices.setProxy`/`getProxy`) used by every outbound connection, a cleaner superset of the original fix |
| 2025-08-04 | `04a2e70ea69c21c5f7a23b28bfa67229f78d2d2c` | Update BrightcoveExperiences.js | 1 | obsolete — the `r(f)`/`setTimeout` retry-timer this commit renamed to `tryPlayers` doesn't exist on HEAD at all; `clientlib-base/players/html5-player/js/BrightcoveExperiences.js` was rewritten to call `createPlayers()` off a `document.addEventListener("DOMContentLoaded", ...)` listener instead, which structurally can't collide with another Adobe tool's global `r` function |
| 2026-02-13 | `86afcf2515f407840b48e7dc2c0bc6a6ded85481` | update versions to 6.0.12 | 3 | obsolete (version/housekeeping) |

## Port plan

Ordered by customer value: context-path and proxy handling first (on-prem customers behind dispatchers/proxies hit these), then daily-use admin-tool/import behavior. Proxy handling (§1.4 `d3743ea`) turned out to already be present (see row above) via the shared `HttpServices` proxy plumbing, so it drops out of this plan.

### 1. Context-path handling — `c100a16f1587f622be6825b1af291d038aad492d`, `cf674b75ade8c509687deeb5187d6b14164effd4` — effort **L**

**STATUS: ported 2026-09-17 (see the Phase 3 log in `ONPREM-PARITY-PLAN.md` §3).** One shared
helper `brc.url()` (`clientlibs/clientlib-url/js/brcUrl.js`, category `brc.url`) declared as a
`dependencies` entry by all six consumer clientlibs; every connector URL in shipped JS routed
through it; the admin page publishes `${request.contextPath}` because it has no Granite runtime.
Two departures from the on-prem original, both deliberate:
- the on-prem fix called `Granite.HTTP.externalize()` directly at each site, which assumes a
  Granite runtime on the page. The admin tool is a plain HTML page, so the helper asks three
  sources in order (page-published value, `Granite.HTTP`, `CQ.shared.HTTP`) and distinguishes
  "a source said the root" from "no source could answer" (one `console.warn`, never a silent
  default).
- the `extensions/contentfinder/*` ExtJS files that `cf674b7` patched do not exist on HEAD, so
  only its `syncDB()` falsy-guard equivalent carries over. Four live cloud-era files the on-prem
  commit never saw (`variant-dialog.js`, `custom-assets-menu-options-visibility.js`) had the same
  defect and are included.
Verified by `tests/e2e/specs/bcon-context-path.spec.js` (4 tests, in the gate on both platforms,
including a negative control that bypasses the helper and requires the check to go red). Matrix
row 38. Residual `not measured`: dialog XML `storePath`/`options`/`src` attributes.

Needs a design, per plan §1.4/§3: one shared helper that reads the request's context path, used everywhere the admin tool, dialogs, and the Touch UI asset finder build a `/bin/brightcove/...` URL. Highest customer value because AEMaaCS never has a context root but every on-prem customer behind a dispatcher does; this is the one gap that silently breaks the connector under a real on-prem deployment topology today.

- Add the helper (client-side JS equivalent of "read context path or fall back to empty string") and route every relative API call through it:
  - `current/ui.apps/.../clientlibs/clientlib-tools/js/brcUI.js`
  - `current/ui.apps/.../clientlibs/clientlib-tools/js/brcAdmin.js`
  - `current/ui.apps/.../clientlibs/clientlib-dialogs/js/dynamic_dropdown.js`
  - `current/ui.apps/.../clientlibs/clientlib-dialogs/js/experiences.js`
  - `current/ui.apps/.../components/page/brightcoveplayer/clientlib/listener.js`
  - `current/ui.apps/.../clientlibs/clientlib-assetfinder/source/js/asset_content_finder.js` (Touch UI asset finder — the one live surface among the on-prem commit's touched files that isn't dead ExtJS code)
- Verify by fronting the on-prem 6.5.0 instance (:4602) with a reverse proxy on a sub-path (plan's suggested approach, no dispatcher needed) and confirming the admin tool and asset finder still resolve API calls.

### 2. Subfolder-sync / subfoldered-video replication — `d923bdd6258bad7227fbb88f02b8e1d7c0206918`, `1911f571aed0278025032781062174adcd997fc9` — effort **S + S**

**STATUS: ported 2026-09-17.** Both gaps closed in `BrcReplicationHandler`, but not by
pasting the on-prem snippets: the mainline's own folder-sync logic (in
`BrightcovePublishListener` and `BrightcoveSyncAssetWorkflowStep`) is a superset of the
on-prem version, and it existed in TWO copy-pasted private methods. Pasting a third copy
was the wrong shape, so the logic moved to `utils/FolderSyncUtil` and all three publish
paths now call it; the only per-caller difference, the 15s retry wait that only the
workflow step's own thread can afford, is a parameter.
- `d923bdd`: account-id resolution now walks up past a synced subfolder, behind the
  testable seam `BrcReplicationHandler.accountIdFor(Resource)`.
- `1911f57`: both activation paths (`activateNew`, `activateModified`) sync the folder
  after `updateRenditions`.
Two things found while extracting, both kept: the account-root guard needed OSGi and
would NPE outside it (caught by the outer catch, i.e. the sync was silently skipped), so
it now reports three states and the caller refuses to create a folder on an unanswered
question; and the already-synced-subfolder branch moved ahead of that guard, since an
account root never carries `brc_folder_id`.
Verified by `core/.../BrcReplicationHandlerFolderSyncTest` (4 tests) plus
`FolderSyncUtilTest` (the three-state pin). Measured control: reverting all three port
points turns 3 of the 4 red. The BGS-1705 test still passes, which is what covers the
extraction as a refactor. Doc: `current/docs/core-folder-sync.md`.
Residual `not measured`: a live subfoldered activation through a real replication agent.

Both gaps are in the same file, `current/core/.../webservices/BrcReplicationHandler.java`, which is still an active `@Component(service = TransportHandler.class)` wired to `/etc/replication/agents.author/brightcove`. Customers with subfolder-per-Brightcove-folder DAM organization (the mainline subfolder-sync feature is otherwise fully ported, see rows 144f9b5/8ee7087/ec5819d above) hit this daily if their publish path still goes through the classic replication agent rather than `BrightcovePublishListener`/`BrightcoveSyncAssetWorkflowStep` (which already have the fix).

- `d923bdd`: fix `account_id` resolution in `getReplicationResult` (~line 187) to walk up past a `brc_folder_id` subfolder before reading the account name, mirroring the identical guard already in `BrightcovePublishListener.java` and `BrightcoveSyncAssetWorkflowStep.java`.
- `1911f571`: add the `brc_folder_id`/`serviceUtil.moveVideoToFolder(...)` block after both `updateRenditions(_asset, video)` calls (~lines 379, 407) in `activateVideo`/`deactivateVideo`.
- Verify with a video asset inside a synced Brightcove subfolder, activated through the classic replication agent (not the workflow step), and confirm it lands in the correct Brightcove folder.

### 3. AEM_NO_DAM tag filter on full-scroll import — `ccdaeb2fde4c2dcf19af42436e07ce97df8ef7cd` — effort **S**

**STATUS: ported 2026-09-17.** The one-line default flip, as classified. Pinned by
`core/.../ServiceUtilDamOnlyDefaultTest`, which asserts the delegation (`dam_only = true`)
rather than the outgoing query string, because the `q=%20-tags:AEM_NO_DAM` parameter is
built two layers down in `CmsAPI` behind an authenticated Brightcove call and the defect
was entirely in the value this overload passed on. A second test pins that the explicit
overload still honours a caller-supplied `false`, so the fix cannot be mistaken for
"always filter". Measured control: reverting the one line turns the first test red.
🔴 The deeper defect this exposes is NOT fixed and is filed as plan §3b item 10: the
8-arg `getList` ignores BOTH flags on its first page (`CmsAPI.getVideos(q, limit, offset,
sort)` hardcodes `dam_only=true, clips_only=false`), and `getVideosCount(query)` always
filters, so `totals` does not describe an unfiltered page 1. The port makes the default
consistent; it does not make the parameter work.
Matrix row 35 stays `not measured`: the import itself was never fired against the account.

- One-line default flip: `ServiceUtil.getList(Boolean, int, int, boolean, String, String)` (the 6-arg overload, ~line 200) should default `dam_only=true`, matching the already-fixed sibling overloads and `CmsAPI.getVideos`/`getVideosCount`.
- Verify: tag a video `AEM_NO_DAM` in the target account, run a full-scroll import via this specific overload's call path, confirm the tagged video is excluded.

### 4. Playlist/label modal centering and add-icon click bug — `afa58e79146d39f3ac9869be8335a5893c9474fd` — effort **S**

**STATUS: split 2026-09-17. Layout half ported; add-icon half obsolete.** The classification
above assumed both halves had a live surface here. Measured on both instances and across all of
`ui.apps`, they do not:
- **Ported (live).** `.pml-dialog` is the admin tool's confirmation dialog, used by switch
  account, bulk-delete playlists and create-new-label. `align-items`/`justify-content` on the
  overlay, `.pml-dialog_container` de-positioned (the flex parent centers it), and
  `overflow-y:auto; max-height:50vh` moved to `.pml-dialog_content`, which is the live
  scrolling region on this line (the on-prem fix bounded `.playlist-listing`/`.label-listing`,
  see below). `showPopup()` now sets `display:flex` instead of jQuery `.show()`, which would
  set `display:block` and silently drop the centering: that is the trap that makes the CSS
  change look ineffective.
- **Obsolete (dead code).** `.label-add-input`, `.playlist-add-input`, `.label-listing` and
  `.playlist-listing` exist NOWHERE: not in `brightcoveadmin.html`, not anywhere else in
  `ui.apps`, and not in the served DOM of either instance (measured 2026-09-17). The cloud line
  replaced those listings with `#editPlaylistModal` and the label pills, so
  `suggestLabelsForVideo` / `suggestVideosForPlaylist` and their two keyup binders are
  unreachable. The `$item.localName == 'img'` bug is real but sits in that dead code, so the
  fix is deliberately NOT ported: a fix to unreachable code cannot be verified and would claim
  behaviour nothing exercises. The block carries a 🔴 comment naming the defect, the on-prem
  commit and the two ways out (delete it, or restore the markup and then port).
  No capability is lost: matrix rows 13 and 18 already record label apply/remove and playlist
  rename as `works` on 7.3.x through the newer surfaces.
Verified by `tests/e2e/specs/bcon-pml-dialog-layout.spec.js`: 6 tests (centered-from-the-real-UI,
tall-dialog-stays-in-viewport-and-scrolls, and a negative control that re-applies the pre-fix CSS
and requires the checks to go red) at two viewports, all geometry as numbers. Doc:
`current/docs/pml-dialog-layout.md`.

Daily-use bug in the playlist/label management dialogs (`.pml-dialog`) that on-prem customers using 6.0.x fixed years ago.

- CSS: add `align-items:center;justify-content:center` to `.pml-dialog`, drop `position:absolute` from `.pml-dialog_container`, add `overflow-y:auto;max-height:50vh` to `.pml-dialog .playlist-listing`/`.label-listing` (`clientlib-tools/css/style.css`).
- JS: change `showPopup()`'s `$popup.show()` to `$popup.css('display', 'flex')`, and fix both `$item.localName == 'img'` checks (label-add and playlist-add autocomplete click handlers) to `$item.get(0).nodeName == 'IMG'` (`clientlib-tools/js/brcUI.js`).
- Verify: open the "add label"/"add video to playlist" dialog on a small viewport, click directly on the add-icon (not the text), confirm the item is added and the dialog is vertically centered.

**Effort total: 1 L + 4 S.**
