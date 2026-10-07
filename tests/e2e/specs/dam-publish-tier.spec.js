// Publish-tier coverage: parity-matrix rows 31, 32, 33, DAM ingest and DAM move.
// None of these had ever been measured: they need a publish instance to activate to.
//
// THE TRIGGER (read from the code, not assumed):
//   Activating a DAM asset on the AUTHOR fires an OSGi event that
//   listeners/BrightcovePublishListener handles. It is registered for BOTH topics
//   (b5ac373): `com/day/cq/replication` (classic Replicator, paths in "paths",
//   ACTIVATE/DEACTIVATE) and `org/apache/sling/distribution/agent/package/distributed`
//   (Sling Content Distribution, "distribution.paths", ADD/DELETE). It only acts when
//     - its OSGi config `isEnabled` is true (metatype default FALSE, and ui.config
//       ships no config, so a fresh install never syncs on publish), and
//     - the instance run modes contain "author".
//   For every activated path containing a configured account's damIntegrationPath it
//   reads jcr:content/metadata/brc_lastsync:
//     - absent  -> activateNew: ServiceUtil.createVideoS3 = CMS create + S3 upload of
//                  the ORIGINAL rendition + Dynamic Ingest (this is the "DAM ingest"
//                  path), reference_id = the asset's jcr:uuid (BGS-1705), then brc_id /
//                  dc:title / brc_lastsync written and committed
//     - present -> activateModified: ServiceUtil.updateVideo (name <- dc:title,
//                  description <- brc_description, tags, reference_id, ...) on brc_id
//   and in both cases FolderSyncUtil.syncFolder: an asset whose parent DAM folder has
//   brc_folder_id is moved into that Video Cloud folder; an unsynced parent gets a
//   Video Cloud folder named after the DAM folder NODE NAME; the account root
//   (<damIntegrationPath>/<accountId>) never gets one (BGS-1600).
//   The listener does nothing on publish; the publish instance is only the target.
//
//   The other publish path, webservices/BrcReplicationHandler, is a TransportHandler
//   for a replication agent whose transport URI starts with `brightcove://`. The
//   package ships no such agent. If one is enabled on the author it ALSO processes
//   every activation; the preflight records which paths are live.
//
//   "Move": listeners/BrightcoveMoveListener is a leftover "Lab2020" template whose
//   body is commented out. It has no Brightcove behaviour at all. A DAM move is only
//   reflected in Video Cloud when the asset is activated again (activateModified ->
//   syncFolder). Both are below: the re-activation is asserted, the move-alone
//   expectation is pinned as test.fixme.
//
// SDK vs 6.5: the local AEMaaCS SDK author has NO Sling Content Distribution agents
// (`/libs/sling/distribution/services/agents` is empty) and replicates through classic
// agents under /etc/replication/agents.author, so locally only the
// `com/day/cq/replication` branch of the listener can fire, on BOTH lines. The
// distribution-event branch (the one b5ac373 repaired) runs only on real AEMaaCS and
// is NOT exercised here.
//
// Needs (each absent -> the file skips as NOT MEASURED):
//   AEM_BASE          the author (as for every spec)
//   AEM_PUBLISH_URL   its publish instance, reachable with AEM_PUBLISH_USER/_PASS
//                     (default: the author's credentials), and an enabled author
//                     replication agent pointing at it
//   BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID / _CLIENT_SECRET  the Video Cloud account the
//                     author is configured with (a personal test account)
//   BrightcovePublishListener isEnabled=true on the author
// Writes only throwaway objects named e2e-throwaway-*: DAM assets/folders under the
// account folder, Video Cloud videos and folders. afterAll removes them on failure
// too. Every Video Cloud assertion is a fresh CMS GET (tests/e2e/cms.js), never a
// toast or a servlet response.
const { test, expect, resolveAccountId } = require('../fixtures');
const { creds, cmsClient } = require('../cms');
const dam = require('../dam');

const LISTENER = 'com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener';
const TOPIC_REPLICATION = 'com/day/cq/replication';
const TOPIC_DISTRIBUTION = 'org/apache/sling/distribution/agent/package/distributed';
const BRC_LOG = '/logs/brightcove.log';

test.describe.configure({ mode: 'serial' });
// Dynamic Ingest of even a 2-second file takes minutes; activation hops are seconds.
test.setTimeout(8 * 60_000);

let cms;
let pub;
let acct;
let root; // <damIntegrationPath>/<accountId>
let foldersBefore; // Video Cloud folder ids that existed before this file ran
const t = dam.tracker();
const RUN = dam.stamp();

test.beforeAll(async ({ request }) => {
  test.skip(!creds(), 'NOT MEASURED: BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set');
  test.skip(!dam.AEM_PUBLISH_URL, 'NOT MEASURED: AEM_PUBLISH_URL not set, so there is no publish tier to activate to');

  cms = await cmsClient();
  acct = await resolveAccountId(request);
  if (cms.accountId !== acct) {
    throw new Error('the Video Cloud credentials in the environment belong to a different account than the one configured in AEM; refusing to write');
  }
  root = `/content/dam/brightcove_assets/${acct}`;

  const modes = await dam.runModes(request);
  if (!modes.includes('author')) throw new Error(`AEM_BASE is not an author (run modes ${modes.join(',')}); the listener only acts on author`);

  const l = await dam.component(request, LISTENER);
  if (!l) throw new Error(`${LISTENER} is not registered: is the connector installed?`);
  test.skip(String(l.props.isEnabled) !== 'true',
    `NOT MEASURED: ${LISTENER} isEnabled=${l.props.isEnabled} on the author (metatype default false, no config shipped). Enable it in configMgr to measure the publish path.`);

  pub = await dam.publishContext();
  const probe = await pub.get('/system/console/bundles.json');
  if (!probe.ok()) throw new Error(`publish ${dam.AEM_PUBLISH_URL} not reachable with the publish credentials (HTTP ${probe.status()})`);

  const agents = await dam.enabledAgents(request);
  const pubHost = new URL(dam.AEM_PUBLISH_URL).host;
  const toPublish = agents.filter((a) => a.transportUri.includes(pubHost));
  if (!toPublish.length) throw new Error(`no enabled author replication agent targets ${pubHost}: activation would never reach publish`);
  const brightcoveAgents = agents.filter((a) => a.transportUri.toLowerCase().startsWith('brightcove://'));
  test.info().annotations.push(
    { type: 'publish-paths', description: `listener=enabled; brightcove:// agents=${brightcoveAgents.map((a) => a.name).join(',') || 'none'}; agents to publish=${toPublish.map((a) => a.name).join(',')}` },
    { type: 'listener.event.topics', description: l.props['event.topics'] || '(none)' });

  foldersBefore = new Set((await cms.folders()).map((f) => f.id));
});

test.afterAll(async ({ request }) => {
  const errors = await t.cleanup({ cms, request, pub });
  if (pub) await pub.dispose();
  expect(errors, 'cleanup left throwaway objects behind (see the warning above)').toEqual([]);
});

// ---- helpers local to this file --------------------------------------------------

// A DAM asset that the connector treats as already synced to `videoId`: the exact
// marker ServiceUtil.updateAsset writes on import (brc_id + brc_lastsync). Written
// AFTER the upload settles so the original rendition is older than brc_lastsync and
// updateRenditions does not re-ingest it as a replacement master.
async function syncedAsset(request, folder, name, videoId) {
  const { assetPath, assetState } = await dam.uploadAsset(request, folder, `${name}.mp4`);
  t.assets.add(assetPath);
  await dam.setMetadata(request, assetPath, { brc_id: videoId, brc_lastsync: { date: new Date() } });
  const m = await dam.metadata(request, assetPath);
  expect(m.brc_id).toBe(videoId);
  expect(Number.isNaN(Date.parse(m.brc_lastsync)), `brc_lastsync was not stored as a date: ${m.brc_lastsync}`).toBe(false);
  test.info().annotations.push({ type: 'upload', description: `${assetPath} dam:assetState=${assetState}` });
  return assetPath;
}

async function unsyncedAsset(request, folder, name, title) {
  const { assetPath, assetState } = await dam.uploadAsset(request, folder, `${name}.mp4`);
  t.assets.add(assetPath);
  await dam.setMetadata(request, assetPath, { 'dc:title': title });
  const m = await dam.metadata(request, assetPath);
  expect(m.brc_lastsync, 'a fresh upload must not carry a sync marker').toBeUndefined();
  expect(m['dc:title']).toBe(title);
  test.info().annotations.push({ type: 'upload', description: `${assetPath} dam:assetState=${assetState}` });
  return assetPath;
}

async function uuidOf(request, assetPath) {
  const n = await dam.assetNode(request, assetPath);
  const uuid = n && n['jcr:uuid'];
  // Absent is not "no guard": without a jcr:uuid the BGS-1705 reference_id default is
  // empty and the duplicate guard is off. Say so instead of carrying on.
  expect(uuid, `${assetPath} has no jcr:uuid, so the connector would send an empty reference_id`).toBeTruthy();
  return uuid;
}

async function activateAndReachPublish(request, assetPath) {
  await dam.activate(request, assetPath);
  const onPub = await dam.waitOnPublish(pub, `${assetPath}/jcr:content/metadata`);
  expect(onPub, `${assetPath} never arrived on ${dam.AEM_PUBLISH_URL}: the replication hop itself failed`).not.toBeNull();
}

async function newThrowawayVideo(label) {
  const name = `${dam.PREFIX}${label}-${RUN}`;
  const id = await cms.create(name);
  t.videos.add(id);
  return { id, name };
}

async function newFolder(label) {
  const name = `${dam.PREFIX}folder-${label}-${RUN}`;
  return { name, path: `${root}/${name}` };
}

// Video Cloud folders created since this file started that are named after the
// account. BGS-1600's guard exists to keep this empty.
async function spuriousAccountFolders() {
  return (await cms.folders()).filter((f) => !foldersBefore.has(f.id) && f.name === acct);
}

// ---- row 31 ------------------------------------------------------------------------

test('row 31: activating a synced DAM asset pushes its metadata to the existing video', async ({ request }) => {
  const v = await newThrowawayVideo('r31');
  const assetPath = await syncedAsset(request, root, `${dam.PREFIX}r31-${RUN}`, v.id);
  const uuid = await uuidOf(request, assetPath);
  const title = `${v.name}-renamed`;
  const description = `row 31 description ${RUN}`;
  await dam.setMetadata(request, assetPath, { 'dc:title': title, brc_description: description });

  const before = await cms.get(v.id);
  expect(before.name).toBe(v.name); // nothing to pass vacuously
  expect(before.description || null).not.toBe(description);
  const lastsyncBefore = (await dam.metadata(request, assetPath)).brc_lastsync;

  await activateAndReachPublish(request, assetPath);

  // Independent re-read of the persisted video.
  const after = await dam.pollUntil(async () => {
    const x = await cms.get(v.id);
    return x.name === title && x.description === description ? x : null;
  }, { timeout: 90_000, interval: 3_000 });
  const seen = after || (await cms.get(v.id));
  expect(seen.name, 'CMS name after activation (dc:title)').toBe(title);
  expect(seen.description, 'CMS description after activation (brc_description)').toBe(description);
  expect(seen.reference_id, 'the update carries the asset jcr:uuid as reference_id').toBe(uuid);
  // It was an update of THIS video, not a second create.
  expect(String((await cms.getByRef(uuid)).id)).toBe(v.id);
  // Account-root asset: no folder move, and no folder named after the account.
  expect(seen.folder_id || null).toBeNull();
  expect(await spuriousAccountFolders()).toEqual([]);

  const m = await dam.metadata(request, assetPath);
  expect(m.brc_state).toBe('ACTIVE');
  expect(m.brc_lastsync, 'brc_lastsync advances on a successful update').not.toBe(lastsyncBefore);
});

// ---- row 32 ------------------------------------------------------------------------

test('row 32 (BGS-1600): activating in a DAM subfolder creates and uses the matching Video Cloud folder', async ({ request }) => {
  const f = await newFolder('r32');
  await dam.createDamFolder(request, f.path);
  t.folders.add(f.path);
  t.bcFolderNames.add(f.name);
  expect((await cms.folders()).some((x) => x.name === f.name), 'no Video Cloud folder of that name exists yet').toBe(false);

  const v = await newThrowawayVideo('r32');
  const assetPath = await syncedAsset(request, f.path, `${dam.PREFIX}r32-${RUN}`, v.id);
  expect((await cms.get(v.id)).folder_id || null).toBeNull();

  await activateAndReachPublish(request, assetPath);

  const folderId = await dam.pollUntil(async () => {
    const n = await dam.json(request, f.path);
    return n && n.brc_folder_id;
  }, { timeout: 90_000, interval: 3_000 });
  expect(folderId, 'the DAM subfolder never got a brc_folder_id').toBeTruthy();

  const created = (await cms.folders()).find((x) => x.id === folderId);
  expect(created, `Video Cloud has no folder ${folderId}`).toBeTruthy();
  expect(created.name, 'folder named after the DAM folder node name').toBe(f.name);
  expect(foldersBefore.has(folderId)).toBe(false);

  const moved = await dam.pollUntil(async () => {
    const x = await cms.get(v.id);
    return x.folder_id === folderId ? x : null;
  }, { timeout: 60_000, interval: 3_000 });
  expect(moved, `video ${v.id} was not moved into folder ${folderId}`).not.toBeNull();
  expect(await spuriousAccountFolders(), 'a folder named after the account root was created').toEqual([]);

  // Second activation reuses the folder rather than creating another one.
  await dam.activate(request, assetPath);
  await dam.sleep(15_000);
  expect((await cms.folders()).filter((x) => x.name === f.name).map((x) => x.id)).toEqual([folderId]);
  expect((await cms.get(v.id)).folder_id).toBe(folderId);
});

// ---- DAM ingest ----------------------------------------------------------------------

test('DAM ingest: activating an unsynced source video creates ONE new Video Cloud video via Dynamic Ingest', async ({ request }) => {
  const name = `${dam.PREFIX}ingest-${RUN}`;
  const assetPath = await unsyncedAsset(request, root, name, name);
  const uuid = await uuidOf(request, assetPath);
  expect(await cms.getByRef(uuid), 'a video with this reference_id already exists').toBeNull();
  const startedAt = Date.now();

  await activateAndReachPublish(request, assetPath);

  const brcId = await dam.pollUntil(async () => (await dam.metadata(request, assetPath)).brc_id, { timeout: 120_000, interval: 3_000 });
  expect(brcId, 'activateNew never wrote brc_id back to the asset').toBeTruthy();
  t.videos.add(String(brcId));

  const v = await cms.getByRef(uuid);
  expect(v, `no video with reference_id ${uuid}`).not.toBeNull();
  expect(String(v.id)).toBe(String(brcId));
  expect(v.name).toBe(name);
  expect(Date.parse(v.created_at)).toBeGreaterThanOrEqual(startedAt - 60_000);

  // The binary really arrived: Dynamic Ingest produced playable sources.
  const sources = await dam.pollUntil(async () => {
    const s = await cms.sources(v.id);
    return s.length ? s : null;
  }, { timeout: 6 * 60_000, interval: 10_000 });
  expect(sources, `video ${v.id} has no sources 6 min after ingest: the S3 upload or ingest request failed`).not.toBeNull();
  const final = await cms.get(v.id);
  expect(final.duration, 'duration of the 2s fixture, in ms').toBeGreaterThan(1000);
  expect(final.duration).toBeLessThan(4000);

  const m = await dam.metadata(request, assetPath);
  expect(m.brc_lastsync).toBeTruthy();
  expect(m.brc_state).toBe('ACTIVE');
});

// ---- row 33 ------------------------------------------------------------------------

test('row 33 (BGS-1705): a redelivered publish of a new asset does not create a duplicate video', async ({ request }) => {
  const name = `${dam.PREFIX}r33-${RUN}`;
  const assetPath = await unsyncedAsset(request, root, name, name);
  const uuid = await uuidOf(request, assetPath);
  expect(await cms.getByRef(uuid)).toBeNull();

  // Two activations back to back: two replication events for the same path while the
  // first create is still in flight, then a third after the marker has landed.
  await Promise.all([dam.activate(request, assetPath), dam.activate(request, assetPath)]);
  const brcId = await dam.pollUntil(async () => (await dam.metadata(request, assetPath)).brc_id, { timeout: 120_000, interval: 3_000 });
  expect(brcId, 'no brc_id after two activations').toBeTruthy();
  t.videos.add(String(brcId));
  await dam.activate(request, assetPath);
  await dam.sleep(20_000);

  // Persisted state: exactly one video for this asset, and it is the one the asset
  // points at. reference_id makes a second create a 409, so ALSO count by the unique
  // name, which is what would catch a duplicate created without a reference_id.
  const byRef = await cms.getByRef(uuid);
  expect(byRef).not.toBeNull();
  expect(String(byRef.id)).toBe(String(brcId));
  expect((await dam.metadata(request, assetPath)).brc_id, 'brc_id stable across redelivery').toBe(brcId);
  const q = `+name:"${name}"`;
  const indexed = await dam.pollUntil(async () => ((await cms.count(q)) >= 1 ? true : null), { timeout: 120_000, interval: 5_000 });
  expect(indexed, 'the video never appeared in CMS search, so the duplicate count is NOT MEASURED').toBe(true);
  expect(await cms.count(q), `videos named ${name}`).toBe(1);

  // Which branch each event took, from the connector's own log. null = the log could
  // not be read, which is NOT MEASURED, not zero.
  // The asset path is unique to this run, so every matching line is from this test.
  const fresh = await dam.logLines(request, BRC_LOG, assetPath, 20_000);
  if (fresh) {
    const creates = fresh.filter((l) => l.includes('Activating New Brightcove Asset')).length;
    const updates = fresh.filter((l) => l.includes('Activating Modified Brightcove Asset')).length;
    test.info().annotations.push({ type: 'listener branches', description: `new=${creates} modified=${updates}` });
    expect(creates + updates, 'the listener saw fewer than three events').toBeGreaterThanOrEqual(3);
  } else {
    test.info().annotations.push({ type: 'listener branches', description: 'NOT MEASURED: brightcove.log not readable through the log tailer' });
  }
});

// ---- move ---------------------------------------------------------------------------

test('move: moving an asset between DAM folders then re-activating moves the video between Video Cloud folders', async ({ request }) => {
  const a = await newFolder('mvA');
  const b = await newFolder('mvB');
  for (const f of [a, b]) {
    await dam.createDamFolder(request, f.path);
    t.folders.add(f.path);
    t.bcFolderNames.add(f.name);
  }
  const v = await newThrowawayVideo('mv');
  const assetPath = await syncedAsset(request, a.path, `${dam.PREFIX}mv-${RUN}`, v.id);
  await activateAndReachPublish(request, assetPath);
  const folderA = await dam.pollUntil(async () => ((await dam.json(request, a.path)) || {}).brc_folder_id, { timeout: 90_000, interval: 3_000 });
  expect(folderA).toBeTruthy();
  expect(await dam.pollUntil(async () => ((await cms.get(v.id)).folder_id === folderA ? true : null), { timeout: 60_000 })).toBe(true);

  const moved = await dam.moveNode(request, assetPath, b.path);
  t.assets.add(moved);
  // Observed, not asserted: what Video Cloud shows after the move alone (see the
  // test.fixme below for the expectation).
  await dam.sleep(20_000);
  test.info().annotations.push({ type: 'folder after move alone', description: String((await cms.get(v.id)).folder_id) });

  await activateAndReachPublish(request, moved);
  const folderB = await dam.pollUntil(async () => ((await dam.json(request, b.path)) || {}).brc_folder_id, { timeout: 90_000, interval: 3_000 });
  expect(folderB, 'the destination DAM folder never got a brc_folder_id').toBeTruthy();
  expect(folderB).not.toBe(folderA);
  expect(((await cms.folders()).find((x) => x.id === folderB) || {}).name).toBe(b.name);
  const inB = await dam.pollUntil(async () => ((await cms.get(v.id)).folder_id === folderB ? true : null), { timeout: 60_000 });
  expect(inB, `video ${v.id} not in folder ${folderB} after the re-activation`).toBe(true);
  expect((await dam.metadata(request, moved)).brc_id, 'the sync marker travelled with the move').toBe(v.id);
});

// 🔴 Expected to fail on the current code: BrightcoveMoveListener has no Brightcove
// behaviour (template body commented out), so nothing reacts to a DAM move until the
// asset is activated again. Not yet run red: run once as `test` when the authors are
// free, then keep as fixme until a move handler exists.
test.fixme('move alone (no re-activation) moves the video to the destination folder', async ({ request }) => {
  const a = await newFolder('mvaloneA');
  const b = await newFolder('mvaloneB');
  for (const f of [a, b]) {
    await dam.createDamFolder(request, f.path);
    t.folders.add(f.path);
    t.bcFolderNames.add(f.name);
  }
  // Seed the destination so it is already a synced folder: a move handler would then
  // only need moveVideoToFolder, the simplest behaviour to expect.
  const seed = await newThrowawayVideo('mvalone-seed');
  await activateAndReachPublish(request, await syncedAsset(request, b.path, `${dam.PREFIX}mvalone-seed-${RUN}`, seed.id));
  const folderB = await dam.pollUntil(async () => ((await dam.json(request, b.path)) || {}).brc_folder_id, { timeout: 90_000 });
  expect(folderB).toBeTruthy();

  const v = await newThrowawayVideo('mvalone');
  const assetPath = await syncedAsset(request, a.path, `${dam.PREFIX}mvalone-${RUN}`, v.id);
  await activateAndReachPublish(request, assetPath);
  expect((await cms.get(v.id)).folder_id).not.toBe(folderB);

  const moved = await dam.moveNode(request, assetPath, b.path);
  t.assets.add(moved);
  const inB = await dam.pollUntil(async () => ((await cms.get(v.id)).folder_id === folderB ? true : null), { timeout: 90_000 });
  expect(inB, 'Video Cloud folder unchanged after a DAM move').toBe(true);
});

// 🔴 Expected to fail on the current code: FolderSyncUtil.syncFolder returns early at
// the account root (the BGS-1600 guard) without calling removeVideoFromFolder, so a
// video whose asset is moved from a synced subfolder back to the account root keeps
// its old Video Cloud folder even after re-activation. Not yet run red.
test.fixme('moving an asset back to the account root and re-activating takes the video out of its folder', async ({ request }) => {
  const a = await newFolder('mvrootA');
  await dam.createDamFolder(request, a.path);
  t.folders.add(a.path);
  t.bcFolderNames.add(a.name);
  const v = await newThrowawayVideo('mvroot');
  const assetPath = await syncedAsset(request, a.path, `${dam.PREFIX}mvroot-${RUN}`, v.id);
  await activateAndReachPublish(request, assetPath);
  const folderA = await dam.pollUntil(async () => ((await dam.json(request, a.path)) || {}).brc_folder_id, { timeout: 90_000 });
  expect(folderA).toBeTruthy();
  expect(await dam.pollUntil(async () => ((await cms.get(v.id)).folder_id === folderA ? true : null), { timeout: 60_000 })).toBe(true);

  const moved = await dam.moveNode(request, assetPath, root);
  t.assets.add(moved);
  await activateAndReachPublish(request, moved);
  const out = await dam.pollUntil(async () => ((await cms.get(v.id)).folder_id ? null : true), { timeout: 60_000 });
  expect(out, `video ${v.id} still in folder ${folderA} after moving to the account root`).toBe(true);
  expect(await spuriousAccountFolders()).toEqual([]);
});
