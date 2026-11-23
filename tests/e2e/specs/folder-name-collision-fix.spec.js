// Video Cloud folder-name collision regression coverage: Trap 5 and Trap 6 of
// current/docs/core-folder-sync.md, end to end over the live publish path.
//
// Nine cases:
//   1  two same-named DAM folders in different branches -> ONE Video Cloud folder,
//      both videos filed into it, both DAM folders marked with its id
//   2  a DAM folder whose name collides only by CASE with an existing Video Cloud
//      folder reuses it instead of creating a duplicate
//   3  two same-named DAM folders activated CONCURRENTLY -> one folder, both filed
//   4  an asset in the DAM integration path root -> ingested, no folder, a WARN
//   5  a stale brc_folder_id on either root is ignored and repaired; the Video Cloud
//      folder it names is NOT deleted
//   6  a brand-new uniquely named DAM folder still creates a new Video Cloud folder
//   7  an asset in the account root -> no folder, no folder named after the account
//   8  uploading without activating still ingests nothing
//   9  one activation carrying a folder and its three assets ingests all three
//
// NEEDS THE PUBLISH BED. Absent -> the file skips as NOT MEASURED:
//   AEM_BASE (the author), AEM_PUBLISH_URL (its publish, reachable with
//   AEM_PUBLISH_USER/_PASS), BRIGHTCOVE_ACCOUNT_ID/_CLIENT_ID/_CLIENT_SECRET, an
//   enabled author replication agent targeting the publish host, and exactly ONE live
//   Brightcove publish path (BrightcovePublishListener isEnabled=true; the shipped
//   cloud package carries no config and the metatype default is false; no enabled
//   brightcove:// agent).
//
// THE TRIGGER: activating a DAM asset on the author fires an OSGi event that
// listeners/BrightcovePublishListener handles. It ingests through
// ServiceUtil.createVideoS3 and then calls FolderSyncUtil.syncFolder(..., NO_RETRY_DELAY).
//
// METHOD: one test per case, every assertion on the persisted end state read fresh
// (a CMS GET, a DAM node read, a log line). Never a toast, never a servlet response.
// A "the video is unfiled" or "no second folder" assertion is only made AFTER a
// positive signal that the activation was processed (the branch's own log line, or
// brc_id appearing), because after a bare sleep a dead publish path passes it too.
//
// WRITES: only e2e-throwaway-* DAM folders/assets and Video Cloud videos/folders.
// Case 5 writes brc_folder_id onto the two roots because that is the behaviour
// under test; both roots are snapshotted in beforeAll and restored in afterAll.
// Everything else the run creates is removed by dam.tracker() in afterAll, on
// failure too. Never runs the Sync Database dataload.
//
// Run:
//   set -a; . ~/.brightcove/personal.env; set +a
//   AEM_BASE=http://localhost:4502 AEM_PUBLISH_URL=http://localhost:4503 \
//     node_modules/.bin/playwright test specs/folder-name-collision-fix.spec.js --workers=1
const fs = require('fs');
const path = require('path');
const { test, markLive, expect, resolveAccountId, brightcoveAssetsRoot } = require('../fixtures');
markLive(test); // credential-dependent: counts toward the gate's e2e_live verdict
const { creds, cmsClient } = require('../cms');
const dam = require('../dam');
const { AEM_BASE } = require('../target');

const LISTENER = 'com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener';
const BRC_LOG = '/logs/brightcove.log';
const EVIDENCE_DIR = process.env.QA_EVIDENCE_DIR
  || path.join(__dirname, '..', 'test-results');
const OBS_FILE = path.join(EVIDENCE_DIR, 'qa-folder-name-observations.json');

// Ingest of a 2-second file plus the folder hop; the tree case ingests three.
test.setTimeout(10 * 60_000);

let cms;
let pub;
let acct;
let root; // <damIntegrationPath>/<accountId>
let integrationPath; // <damIntegrationPath>
let foldersBefore = new Map(); // Video Cloud folder id -> name, at file start
let rootsBefore = {}; // the two roots' brc_folder_id before this file touched them
let listenerProps = null;
const t = dam.tracker();
const RUN = dam.stamp();
const obs = {};

// Playwright restarts the worker after a failure, so a module-level object written
// only in afterAll would lose the earlier tests' observations. Flush after every test.
function record(key, value) {
  obs[key] = value;
  try {
    fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(OBS_FILE, JSON.stringify(obs, null, 2));
  } catch (e) {
    console.warn(`[qa] could not write ${OBS_FILE}: ${e.message}`);
  }
}

const unique = (label) => `${dam.PREFIX}${label}-${RUN}`;

// ---- preconditions ---------------------------------------------------------------

test.beforeAll(async ({ request }) => {
  test.skip(!creds(), 'NOT MEASURED: BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set');
  test.skip(!dam.AEM_PUBLISH_URL, 'NOT MEASURED: AEM_PUBLISH_URL not set, so there is no publish tier to activate to');

  cms = await cmsClient();
  acct = await resolveAccountId(request);
  if (cms.accountId !== acct) {
    throw new Error(`the Video Cloud credentials in the environment belong to account ${cms.accountId}, but AEM is configured with ${acct}; refusing to write`);
  }
  root = await brightcoveAssetsRoot(request);
  integrationPath = path.posix.dirname(root);

  const modes = await dam.runModes(request);
  if (!modes.includes('author')) throw new Error(`AEM_BASE is not an author (run modes ${modes.join(',')}); the listener only acts on author`);

  const l = await dam.component(request, LISTENER);
  if (!l) throw new Error(`${LISTENER} is not registered: is the connector installed?`);
  listenerProps = l.props;
  if (String(l.props.isEnabled) !== 'true') {
    throw new Error(`${LISTENER} isEnabled=${l.props.isEnabled}: no publish path would ingest anything, so every case would fail for the wrong reason`);
  }
  const brightcoveAgents = (await dam.enabledAgents(request)).filter((a) => a.transportUri.toLowerCase().startsWith('brightcove://'));
  if (brightcoveAgents.length) {
    throw new Error(`an enabled brightcove:// agent (${brightcoveAgents.map((a) => a.name)}) would push every activation a second time through BrcReplicationHandler; the results would not be attributable`);
  }
  pub = await dam.publishContext();
  const probe = await pub.get('/system/console/bundles.json');
  if (!probe.ok()) throw new Error(`publish ${dam.AEM_PUBLISH_URL} not reachable with the publish credentials (HTTP ${probe.status()})`);
  const deployed = (await (await request.get('/system/console/bundles.json')).json()).data
    .find((b) => b.symbolicName === 'brightcove.core');
  if (!deployed) throw new Error('brightcove.core is not installed on the author');

  foldersBefore = new Map((await cms.folders()).map((f) => [f.id, f.name]));
  const acctRootNode = await dam.json(request, root);
  const intRootNode = await dam.json(request, integrationPath);
  rootsBefore = {
    [root]: acctRootNode && acctRootNode.brc_folder_id !== undefined ? String(acctRootNode.brc_folder_id) : null,
    [integrationPath]: intRootNode && intRootNode.brc_folder_id !== undefined ? String(intRootNode.brc_folder_id) : null,
  };

  record('preconditions', {
    account: acct,
    author: AEM_BASE,
    publish: dam.AEM_PUBLISH_URL,
    deployedCoreVersion: deployed.version,
    deployedCoreBundleId: deployed.id,
    listenerEnabled: l.props.isEnabled,
    listenerTopics: l.props['event.topics'],
    damIntegrationPath: integrationPath,
    accountRoot: root,
    foldersBefore: [...foldersBefore.entries()].map(([id, name]) => ({ id, name })),
    rootsBefore,
    run: RUN,
  });
});

test.afterAll(async ({ request }) => {
  // Restore the two roots to the state beforeAll saw. The fix is expected to have
  // removed the stale ids case 5 wrote; this makes the bed clean even when it did not.
  const restore = async (p) => {
    const before = rootsBefore[p] ?? null;
    const now = await dam.json(request, p);
    const has = now && now.brc_folder_id !== undefined;
    if (before === null && has) await dam.setProps(request, p, { 'brc_folder_id@Delete': '' });
    if (before !== null && !has) await dam.setProps(request, p, { brc_folder_id: before });
    return { path: p, before, after: ((await dam.json(request, p)) || {}).brc_folder_id ?? null };
  };
  const rootsAfter = [];
  for (const p of [root, integrationPath]) {
    if (p) rootsAfter.push(await restore(p));
  }

  const errors = await t.cleanup({ cms, request, pub });
  if (pub) await pub.dispose();

  // Leftovers this run created. Folders and videos are matched on the run stamp, and
  // every video hit is re-read directly (the search index keeps listing deleted videos).
  const foldersLeft = cms ? (await cms.folders()).filter((f) => String(f.name).includes(RUN)) : [];
  const videosLeft = [];
  if (cms) {
    for (const v of await cms.searchAll(dam.PREFIX.replace(/-$/, ''))) {
      if (String(v.name).includes(RUN) && await cms.tryGet(v.id)) videosLeft.push({ id: v.id, name: v.name });
    }
  }
  const damLeft = [];
  for (const p of [...t.assets, ...t.folders]) {
    if ((await request.get(`${p}.json`)).status() !== 404) damLeft.push(p);
  }

  record('cleanup', {
    roots: rootsAfter,
    foldersLeft: foldersLeft.map((f) => ({ id: f.id, name: f.name })),
    videosLeft,
    damLeft,
    trackerErrors: errors,
  });

  expect(errors, 'cleanup left throwaway objects behind (see the warning above)').toEqual([]);
  expect(damLeft, 'DAM nodes this run created are still there').toEqual([]);
  expect(foldersLeft.map((f) => `${f.name} (${f.id})`), 'throwaway Video Cloud folders left behind').toEqual([]);
  expect(videosLeft, 'throwaway Video Cloud videos left behind').toEqual([]);
  expect(rootsAfter.filter((r) => r.before !== r.after), 'a root was not restored to its pre-run state').toEqual([]);
});

// ---- helpers local to this file --------------------------------------------------

// Lines of the connector log containing `needle`, or null when the tailer could not
// be read. ⚠️ null is NOT zero: a caller that read it as zero would pass a "this
// line never appeared" assertion for the wrong reason, so the callers below skip.
async function logLines(request, needle, tail = 20_000) {
  return dam.logLines(request, BRC_LOG, needle, tail);
}

async function needLog(request, needle, what) {
  const lines = await logLines(request, needle);
  test.skip(!lines, `NOT MEASURED: the connector log tailer could not be read, so "${what}" cannot be attributed to this run`);
  return lines;
}

const countWith = (lines, ...needles) => lines.filter((l) => needles.every((n) => l.includes(n))).length;

// Upload a throwaway asset into `folderPath`. Returns { assetPath, uuid, name }.
// The uuid is the reference_id the connector defaults to (BGS-1705), so a CMS
// lookup by reference_id is a direct read, not the search index.
async function upload(request, folderPath, label) {
  const name = `${unique(label)}.mp4`;
  const { assetPath, assetState } = await dam.uploadAsset(request, folderPath, name);
  t.assets.add(assetPath);
  const node = await dam.assetNode(request, assetPath);
  expect(node && node['jcr:uuid'], `${assetPath} has no jcr:uuid, so the CMS reference_id would be empty`).toBeTruthy();
  return { assetPath, uuid: node['jcr:uuid'], name: name.replace(/\.mp4$/, ''), assetState };
}

async function mkfolder(request, folderPath) {
  await dam.createDamFolder(request, folderPath);
  t.folders.add(folderPath);
  return folderPath;
}

// Activate, prove the replication hop happened, then wait for activateNew's brc_id.
// brc_id is the positive signal that the activation was PROCESSED: without it, any
// "no folder" assertion below would pass vacuously.
async function activateAndIngest(request, assetPath) {
  await dam.activate(request, assetPath);
  const onPub = await dam.waitOnPublish(pub, `${assetPath}/jcr:content/metadata`);
  expect(onPub, `${assetPath} never reached ${dam.AEM_PUBLISH_URL}: the replication hop itself failed, so the connector may never have seen it`).not.toBeNull();
  const brcId = await dam.pollUntil(async () => {
    const m = await dam.metadata(request, assetPath);
    return m && m.brc_id ? String(m.brc_id) : null;
  }, { timeout: 150_000, interval: 3_000 });
  expect(brcId, 'the listener never wrote brc_id back to the asset, so no video was created').toBeTruthy();
  t.videos.add(brcId);
  const byRef = await cms.getByRef((await dam.assetNode(request, assetPath))['jcr:uuid']);
  expect(byRef, `no CMS video with the asset's reference_id, so the ingested video cannot be found`).not.toBeNull();
  expect(String(byRef.id), 'the video found by reference_id is not the one brc_id names').toBe(brcId);
  return brcId;
}

const folderOf = async (videoId) => {
  const v = await cms.get(videoId);
  return v.folder_id === undefined ? null : v.folder_id;
};

async function waitFiled(videoId, folderId, timeout = 120_000) {
  return dam.pollUntil(async () => ((await folderOf(videoId)) === folderId ? true : null), { timeout, interval: 3_000 });
}

// Every Video Cloud folder whose name equals `name` case-insensitively. Video Cloud
// refuses a second folder differing only by case, so more than one here is a defect
// in its own right.
const foldersNamed = async (name) => (await cms.folders()).filter((f) => String(f.name).toLowerCase() === String(name).toLowerCase());

async function damFolderProperty(request, folderPath) {
  const n = await dam.json(request, folderPath);
  return n && n.brc_folder_id !== undefined ? String(n.brc_folder_id) : null;
}

// A single ReplicationAction carrying several paths: the event shape a folder Publish
// in the Assets UI produces (the classic Replicator accepts repeated `path` params).
// dam.activate sends one path, so case 9 needs this.
async function activateTree(request, paths) {
  const tok = await (await request.get('/libs/granite/csrf/token.json')).json();
  const body = ['cmd=Activate', ...paths.map((p) => `path=${encodeURIComponent(p)}`)].join('&');
  const res = await request.post('/bin/replicate.json', {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-Token': tok.token, Referer: `${AEM_BASE}/` },
    data: body,
  });
  expect(res.status(), `activate ${paths.length} paths in one action`).toBeLessThan(300);
  return (await res.text()).trim();
}

// ---- 1: same-named DAM folders, sequential ----------------------------------------

test('1: two same-named DAM folders in different branches file both videos into exactly ONE Video Cloud folder', async ({ request }) => {
  // This is the case that was silent-unfiled on 7.4.3: the second create was refused
  // and nothing looked the name up, so video B stayed at the account root.
  const same = unique('s1-same');
  t.bcFolderNames.add(same);
  const branchA = await mkfolder(request, `${root}/${unique('s1-a')}`);
  const branchB = await mkfolder(request, `${root}/${unique('s1-b')}`);
  const folderA = await mkfolder(request, `${branchA}/${same}`);
  const folderB = await mkfolder(request, `${branchB}/${same}`);
  expect((await foldersNamed(same)).map((f) => f.id), 'no Video Cloud folder of that name may exist before the run').toEqual([]);

  const a = await upload(request, folderA, 's1-asset-a');
  const b = await upload(request, folderB, 's1-asset-b');
  const warnsBefore = countWith(await needLog(request, same, 'the folder-sync log lines'), same);

  const videoA = await activateAndIngest(request, a.assetPath);
  const videoB = await activateAndIngest(request, b.assetPath);

  const named = await foldersNamed(same);
  expect(named.map((f) => f.id), `exactly one Video Cloud folder may carry the name '${same}'`).toHaveLength(1);
  const folderId = named[0].id;
  expect(foldersBefore.has(folderId), 'the folder must be new, not a pre-existing one that happens to share the name').toBe(false);

  // Both videos in the ONE folder. A null folder_id on either is the 7.4.3 defect.
  expect(await waitFiled(videoA, folderId), `video A (${videoA}) was not filed into ${folderId}: folder_id=${await folderOf(videoA)}`).toBe(true);
  expect(await waitFiled(videoB, folderId), `video B (${videoB}) was not filed into ${folderId}: folder_id=${await folderOf(videoB)}`).toBe(true);
  expect((await cms.folderVideoIds(folderId)).sort(), 'the folder must hold exactly the two videos').toEqual([videoA, videoB].sort());

  // Both DAM folders marked, so a later publish skips the create. The flat-namespace
  // consequence is that they share ONE id (documented as out of scope, not a defect).
  expect(await damFolderProperty(request, folderA), 'DAM folder A never got a brc_folder_id').toBe(folderId);
  expect(await damFolderProperty(request, folderB), 'DAM folder B never got a brc_folder_id').toBe(folderId);

  // Which branch ran: the second activation must have logged the reuse, not a failure.
  const lines = await needLog(request, same, 'the folder-sync log lines');
  const reuse = countWith(lines, 'already exists');
  const gaveUp = countWith(lines, 'No folder created');
  const warnsAfter = countWith(lines, same);
  record('case1', { same, folderA, folderB, videoA, videoB, folderId, folderVideoIds: await cms.folderVideoIds(folderId), reuseLogLines: reuse, gaveUpLogLines: gaveUp, folderSyncLogLinesAdded: warnsAfter - warnsBefore });
  expect(gaveUp, `the connector gave up on the folder (${gaveUp} "No folder created" line(s)) instead of reusing it`).toBe(0);
  expect(reuse, 'the reuse branch never logged "a folder with that name already exists", so a different path filed video B').toBeGreaterThan(0);
});

// ---- 2: case-insensitive collision -------------------------------------------------

test('2: a DAM folder whose name collides only by CASE with an existing Video Cloud folder reuses it', async ({ request }) => {
  const lower = unique('s2case');
  // Both names must start with the throwaway prefix (the CMS client refuses anything
  // else), and must differ only by case after it.
  const mixed = `${dam.PREFIX}S2CASE-${RUN}`;
  expect(mixed.toLowerCase(), 'the two names must differ').toBe(lower.toLowerCase());
  expect(mixed, 'the two names must not be identical').not.toBe(lower);

  // Measure the premise on this bed, now: a create that differs only by case must be
  // refused. If it is accepted, the case cannot be measured as a collision and the
  // test skips rather than reporting a pass for the wrong path.
  const existing = await cms.createFolder(lower);
  t.bcFolderNames.add(lower);
  let premise;
  try {
    const dup = await cms.createFolder(mixed);
    premise = `a second folder differing only by case was ACCEPTED (${dup.id})`;
    t.bcFolderNames.add(mixed);
    await cms.delFolderIfThrowaway(dup);
  } catch (e) {
    premise = /HTTP 409/.test(e.message) ? 'case-insensitive uniqueness confirmed (HTTP 409)' : `unexpected: ${e.message}`;
  }
  record('case2.premise', { lower, mixed, premise, existingFolderId: existing.id });
  test.skip(!/409/.test(premise), `NOT MEASURED: the case-insensitive collision premise does not hold on this account (${premise})`);

  // The DAM folder's name differs from the Video Cloud folder only by case.
  const damName = mixed;
  t.bcFolderNames.add(damName);
  const damFolder = await mkfolder(request, `${root}/${damName}`);
  const a = await upload(request, damFolder, 's2-asset');
  const videoId = await activateAndIngest(request, a.assetPath);

  expect(await waitFiled(videoId, existing.id),
    `video ${videoId} was not filed into the pre-existing folder ${existing.id} ('${lower}'): folder_id=${await folderOf(videoId)}`).toBe(true);
  const named = await foldersNamed(lower);
  expect(named.map((f) => f.id), `a duplicate folder was created instead of reusing '${lower}'`).toEqual([existing.id]);
  expect(await damFolderProperty(request, damFolder), `the DAM folder must record ${existing.id}`).toBe(existing.id);

  const lines = await needLog(request, mixed, 'the create payload for the mixed-case name');
  record('case2', { damFolder, videoId, existingFolderId: existing.id, foldersNamed: named, createPayloadLines: countWith(lines, mixed), reuseLogLines: countWith(lines, 'already exists'), gaveUpLogLines: countWith(lines, 'No folder created') });
  expect(countWith(lines, 'No folder created'), 'the connector gave up instead of reusing the mixed-case match').toBe(0);
});

// ---- 3: race -----------------------------------------------------------------------

test('3: two same-named DAM folders activated CONCURRENTLY produce one folder and file both videos', async ({ request }) => {
  // The most likely way the fix breaks: both activations race to POST /folders, one is
  // refused, and the refused one must find the winner's folder. If the lookup runs
  // before the winner's folder is readable, the loser is left unfiled.
  const same = unique('s3-race');
  t.bcFolderNames.add(same);
  const branchA = await mkfolder(request, `${root}/${unique('s3-a')}`);
  const branchB = await mkfolder(request, `${root}/${unique('s3-b')}`);
  const folderA = await mkfolder(request, `${branchA}/${same}`);
  const folderB = await mkfolder(request, `${branchB}/${same}`);
  expect((await foldersNamed(same)).map((f) => f.id), 'no Video Cloud folder of that name may exist before the race').toEqual([]);

  const a = await upload(request, folderA, 's3-asset-a');
  const b = await upload(request, folderB, 's3-asset-b');

  const startedAt = Date.now();
  await Promise.all([dam.activate(request, a.assetPath), dam.activate(request, b.assetPath)]);
  const activationSpreadMs = Date.now() - startedAt;

  const [videoA, videoB] = await Promise.all([activateAndIngest(request, a.assetPath), activateAndIngest(request, b.assetPath)]);

  const named = await foldersNamed(same);
  expect(named.map((f) => f.id), `exactly one Video Cloud folder may exist after the race`).toHaveLength(1);
  const folderId = named[0].id;

  const [filedA, filedB] = await Promise.all([waitFiled(videoA, folderId), waitFiled(videoB, folderId)]);
  const seenA = await folderOf(videoA);
  const seenB = await folderOf(videoB);
  const lines = await needLog(request, same, 'the folder-sync log lines for the race');
  record('case3', {
    same, folderA, folderB, videoA, videoB, folderId, filedA, filedB, folderA_seen: seenA, folderB_seen: seenB,
    activationSpreadMs, folders: named,
    createPayloadLines: countWith(lines, same), reuseLogLines: countWith(lines, 'already exists'),
    gaveUpLogLines: countWith(lines, 'No folder created'), errorLines: countWith(lines, '*ERROR*'),
    log: lines.filter((l) => l.includes(same)).map((l) => l.slice(0, 240)),
  });

  // Neither unfiled: that is the defect, and the race is how it would come back.
  expect(seenA, `video A (${videoA}) was left unfiled after the race`).toBe(folderId);
  expect(seenB, `video B (${videoB}) was left unfiled after the race`).toBe(folderId);
  expect(filedA && filedB).toBe(true);
  expect((await cms.folderVideoIds(folderId)).sort(), 'the folder must hold both videos').toEqual([videoA, videoB].sort());
  expect(countWith(lines, 'No folder created'), 'the connector gave up on a colliding create during the race').toBe(0);
});

// ---- 4: integration path root -------------------------------------------------------

test('4: an asset directly in the DAM integration path root is ingested, gets NO folder, and logs the WARN', async ({ request }) => {
  // Pre-fix this was claim C4: ingested, folder_id null, but only because two 409s were
  // swallowed. The new rule is deliberate: no create at all, a WARN, no brc_folder_id.
  const asset = await upload(request, integrationPath, 's4-iroot');
  const segment = path.posix.basename(integrationPath);
  const segmentBefore = await foldersNamed(segment);
  const warnBefore = countWith(await needLog(request, 'DAM integration path root', 'the integration-root WARN'), 'DAM integration path root');

  const videoId = await activateAndIngest(request, asset.assetPath);

  // Positive signal that the ROOT branch ran (not merely "no folder yet").
  const warn = await dam.pollUntil(async () => {
    const l = await logLines(request, 'DAM integration path root');
    if (!l) return null;
    return countWith(l, 'DAM integration path root', integrationPath) > warnBefore ? l : null;
  }, { timeout: 60_000, interval: 3_000 });
  expect(warn, `no WARN naming ${integrationPath} appeared for this activation, so the root branch may not have run`).not.toBeNull();

  expect(await folderOf(videoId), `video ${videoId} must stay unfiled: an asset in the integration path root has no DAM folder of its own`).toBeNull();
  expect((await foldersNamed(segment)).map((f) => f.id), 'no folder named after the integration path segment may be created or removed').toEqual(segmentBefore.map((f) => f.id));
  expect(await damFolderProperty(request, integrationPath), 'the integration path root must not be marked as synced').toBeNull();

  const lines = (await needLog(request, 'DAM integration path root', 'the integration-root WARN')).filter((l) => l.includes(integrationPath));
  record('case4', { assetPath: asset.assetPath, videoId, folderId: await folderOf(videoId), segment, segmentFolders: segmentBefore.map((f) => ({ id: f.id, name: f.name })), warnLines: lines.slice(-3).map((l) => l.slice(0, 240)) });
  expect(lines.some((l) => l.includes('*WARN*')), 'the integration-root line is not a WARN').toBe(true);
});

// ---- 5: stale brc_folder_id on both roots ------------------------------------------

test('5: stale brc_folder_id on the account root and on the integration path root is ignored and repaired, and the folder survives', async ({ request }) => {
  // The old Trap 6 behaviour wrote the integration path root's brc_folder_id whenever it
  // managed to create the folder; without the repair, the new "root means no folder"
  // rule would be bypassed on exactly those instances.
  const staleName = unique('s5-stale');
  const staleFolder = await cms.createFolder(staleName);
  t.bcFolderNames.add(staleName);

  for (const p of [root, integrationPath]) {
    await dam.setProps(request, p, { brc_folder_id: staleFolder.id });
    expect(await damFolderProperty(request, p), `could not pre-set brc_folder_id on ${p}`).toBe(staleFolder.id);
  }

  const acctAsset = await upload(request, root, 's5-acct');
  const intAsset = await upload(request, integrationPath, 's5-int');

  const repairedBefore = countWith(await needLog(request, 'carries a stale brc_folder_id', 'the stale-root repair WARN'), 'carries a stale brc_folder_id');
  const acctVideo = await activateAndIngest(request, acctAsset.assetPath);
  const intVideo = await activateAndIngest(request, intAsset.assetPath);

  const repaired = await dam.pollUntil(async () => {
    const a = await damFolderProperty(request, root);
    const i = await damFolderProperty(request, integrationPath);
    return a === null && i === null ? { a, i } : null;
  }, { timeout: 60_000, interval: 3_000 });
  const seenRoot = await damFolderProperty(request, root);
  const seenInt = await damFolderProperty(request, integrationPath);

  const lines = await needLog(request, 'carries a stale brc_folder_id', 'the stale-root repair WARN');
  record('case5', {
    staleFolder: { id: staleFolder.id, name: staleName },
    acctAsset: acctAsset.assetPath, intAsset: intAsset.assetPath, acctVideo, intVideo,
    acctVideoFolder: await folderOf(acctVideo), intVideoFolder: await folderOf(intVideo),
    accountRootAfter: seenRoot, integrationRootAfter: seenInt,
    repairLogLinesAdded: countWith(lines, 'carries a stale brc_folder_id') - repairedBefore,
    repairLog: lines.filter((l) => l.includes(root) || l.includes(integrationPath)).slice(-4).map((l) => l.slice(0, 260)),
    staleFolderStillExists: (await cms.folders()).some((f) => f.id === staleFolder.id),
    staleFolderVideos: await cms.folderVideoIds(staleFolder.id),
  });

  // Both ids ignored: neither video is filed, and nothing was created from them.
  expect(await folderOf(acctVideo), `video ${acctVideo} was filed from the account root's stale id`).toBeNull();
  expect(await folderOf(intVideo), `video ${intVideo} was filed from the integration root's stale id`).toBeNull();
  // Both properties removed (repaired), and the repair is the one that did it.
  expect(repaired, `a stale brc_folder_id survived: account root=${seenRoot}, integration root=${seenInt}`).not.toBeNull();
  expect(await cms.folderVideoIds(staleFolder.id), 'the stale folder must hold no video from this run').toEqual([]);
  // The Video Cloud folder is NOT deleted or emptied: a customer may have filed videos there.
  expect((await cms.folders()).some((f) => f.id === staleFolder.id), 'the fix deleted the Video Cloud folder the stale id named').toBe(true);
  expect(countWith(lines, 'carries a stale brc_folder_id'), 'no repair WARN was logged').toBeGreaterThan(repairedBefore);
});

// ---- 6: unique name still creates a folder -----------------------------------------

test('6: a brand-new uniquely named DAM folder still creates a NEW Video Cloud folder and files the video into it', async ({ request }) => {
  const name = unique('s6-unique');
  t.bcFolderNames.add(name);
  const folder = await mkfolder(request, `${root}/${name}`);
  expect((await foldersNamed(name)).map((f) => f.id), 'no Video Cloud folder of that name may exist before the run').toEqual([]);

  const a = await upload(request, folder, 's6-asset');
  const videoId = await activateAndIngest(request, a.assetPath);

  const named = await foldersNamed(name);
  expect(named.map((f) => f.id), `exactly one folder named '${name}' must be created`).toHaveLength(1);
  const folderId = named[0].id;
  expect(foldersBefore.has(folderId), 'the folder must be NEW: the unique-name path must still create one').toBe(false);
  expect(await waitFiled(videoId, folderId), `video ${videoId} was not filed into the new folder ${folderId}`).toBe(true);
  expect(await damFolderProperty(request, folder), `the DAM folder must record ${folderId}`).toBe(folderId);
  expect(await cms.folderVideoIds(folderId)).toEqual([videoId]);

  const lines = await needLog(request, name, 'the create payload for the unique name');
  record('case6', { folder, videoId, folderId, folderVideoIds: await cms.folderVideoIds(folderId), createPayloadLines: countWith(lines, name), reuseLogLines: countWith(lines, 'already exists'), gaveUpLogLines: countWith(lines, 'No folder created') });
  expect(countWith(lines, 'No folder created'), 'the unique-name path reported no folder').toBe(0);
});

// ---- 7: account root unchanged ------------------------------------------------------

test('7: an asset directly in the account root is ingested with no folder, and no folder named after the account is created', async ({ request }) => {
  const acctNamedBefore = (await foldersNamed(acct)).map((f) => f.id).sort();
  const a = await upload(request, root, 's7-acctroot');
  const skipBefore = countWith(await needLog(request, 'Asset is at account root level', 'the account-root guard line'), 'Asset is at account root level');

  const videoId = await activateAndIngest(request, a.assetPath);

  // Positive signal that the account-root guard ran for this activation.
  const guard = await dam.pollUntil(async () => {
    const l = await logLines(request, 'Asset is at account root level');
    if (!l) return null;
    return countWith(l, 'Asset is at account root level') > skipBefore ? l : null;
  }, { timeout: 60_000, interval: 3_000 });
  expect(guard, 'the account-root guard never logged for this activation, so "no folder" may be vacuous').not.toBeNull();

  expect(await folderOf(videoId), `video ${videoId} was filed into a folder instead of staying at the account root`).toBeNull();
  expect((await foldersNamed(acct)).map((f) => f.id).sort(), 'a folder named after the account id was created or removed').toEqual(acctNamedBefore);
  record('case7', { assetPath: a.assetPath, videoId, folderId: await folderOf(videoId), accountNamedFolders: acctNamedBefore });
});

// ---- 8: upload alone does not ingest -------------------------------------------------

test('8: uploading an asset with no activation still does not ingest it', async ({ request }) => {
  const name = unique('s8-noingest');
  t.bcFolderNames.add(name);
  const folder = await mkfolder(request, `${root}/${name}`);
  const a = await upload(request, folder, 's8-asset');

  // The asset settles first (uploadAsset waits for metadata); then give any
  // unexpected trigger the same window the pre-fix measurement used.
  await dam.sleep(60_000);

  const meta = await dam.metadata(request, a.assetPath);
  const node = await dam.assetNode(request, a.assetPath);
  const content = await dam.json(request, `${a.assetPath}/jcr:content`);
  const byRef = await cms.getByRef(a.uuid);
  const lines = await needLog(request, a.assetPath, 'listener lines naming this asset');

  record('case8', {
    assetPath: a.assetPath, assetState: a.assetState, brc_id: meta && meta.brc_id, cmsVideoByRef: byRef && byRef.id,
    replicationOnAsset: node && node['cq:lastReplicationAction'],
    replicationOnContent: content && content['cq:lastReplicationAction'],
    listenerLines: lines.length,
  });
  expect(meta && meta.brc_id, 'brc_id was written without an activation').toBeUndefined();
  expect(byRef, 'a CMS video exists for an asset that was never activated').toBeNull();
  expect(node && node['cq:lastReplicationAction'], 'the asset node carries a replication marker').toBeUndefined();
  expect(content && content['cq:lastReplicationAction'], 'jcr:content carries a replication marker').toBeUndefined();
  expect(lines, `the listener logged ${lines.length} line(s) for an asset that was never activated`).toEqual([]);
});

// ---- 9: tree activation --------------------------------------------------------------

test('9: one activation carrying a folder and its three assets still ingests every asset', async ({ request }) => {
  const name = unique('s9-tree');
  t.bcFolderNames.add(name);
  const folder = await mkfolder(request, `${root}/${name}`);
  const assets = [];
  for (const label of ['s9-a1', 's9-a2', 's9-a3']) assets.push(await upload(request, folder, label));

  const response = await activateTree(request, [folder, ...assets.map((x) => x.assetPath)]);
  const onPub = await dam.waitOnPublish(pub, `${assets[0].assetPath}/jcr:content/metadata`);
  expect(onPub, 'the tree activation never reached the publish instance').not.toBeNull();

  const ids = [];
  for (const a of assets) {
    const id = await dam.pollUntil(async () => {
      const m = await dam.metadata(request, a.assetPath);
      return m && m.brc_id ? String(m.brc_id) : null;
    }, { timeout: 240_000, interval: 3_000 });
    if (id) t.videos.add(id);
    ids.push(id);
  }

  const named = await foldersNamed(name);
  const folderId = named.length === 1 ? named[0].id : null;
  const filed = [];
  if (folderId) {
    for (const id of ids) if (id) filed.push(await waitFiled(id, folderId, 60_000));
  }
  record('case9', {
    folder, assets: assets.map((a, i) => ({ path: a.assetPath, videoId: ids[i] })), replicateResponse: response,
    folderId, filed,
  });

  expect(ids.filter(Boolean), `only ${ids.filter(Boolean).length} of 3 assets were ingested by one tree activation`).toHaveLength(3);
  expect(new Set(ids).size, 'the same video id came back for two different assets').toBe(3);
  for (const [i, a] of assets.entries()) {
    const v = await cms.getByRef(a.uuid);
    expect(v, `no CMS video with the reference_id of asset ${i + 1}`).not.toBeNull();
    expect(String(v.id)).toBe(ids[i]);
  }
  // Secondary: one shared DAM parent means one Video Cloud folder for all three.
  expect(named.map((f) => f.id), `the three assets share one DAM folder, so exactly one Video Cloud folder '${name}' must exist`).toHaveLength(1);
  expect(filed, 'every ingested video must be filed into that folder').toEqual([true, true, true]);
});
