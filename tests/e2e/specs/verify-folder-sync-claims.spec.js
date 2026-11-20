// Folder-sync and DAM-ingest regression coverage on the live publish path: eleven
// claims, each asserted against the observable end state (a fresh CMS GET, never a
// toast or a servlet response). Every case reads the persisted result after a positive
// signal that the activation was processed, so a dead publish path cannot pass a
// negative for the wrong reason.
//
// THE CLAIMS:
//   C1  an activated asset 3 folders below the account root creates a Video Cloud video
//   C2  the video lands in a flat Video Cloud folder named after its IMMEDIATE DAM parent
//   C3  an asset in the account root gets no folder, and no folder named after the account
//   C4  an asset in the integration path root is ingested with NO folder and a WARN
//       naming the path, and no folder create is attempted (Trap 6)
//   C5  two same-named DAM parents in different branches share exactly ONE Video Cloud
//       folder, and both DAM folders are marked with its id (Trap 5, flat namespace)
//   C6  uploading an asset with no activation does not ingest it
//   C7  a folder tree activation ingests every asset in the folder
//   C8  NOT RUN (test.fixme): a sibling tree that only shares the integration path as a
//       string PREFIX is ingested. That is a known DEFECT (substring scoping), not
//       desired behaviour; the body asserts the behaviour we want, so it turns green
//       when the defect is fixed. See the comment above it.
//   C9  the bc-sync-new-asset workflow ingests with no activation
//   C10 the shipped cloud package carries no listener config and the metatype default
//       for isEnabled is false (so a fresh install never syncs on publish)
//   C11 Video Cloud folders are flat, with no parent or hierarchy field
//
// Trigger under test: activating a DAM asset on the AUTHOR fires an OSGi
// ReplicationAction event handled by listeners/BrightcovePublishListener (enabled here
// via OSGi config; the shipped cloud package carries no config and the metatype default
// is false). The listener matches `asset.contains(damIntegrationPath)` (a SUBSTRING
// test, no path boundary: C8), then activateNew -> ServiceUtil.createVideoS3 (CMS
// create + S3 upload + Dynamic Ingest) and FolderSyncUtil.syncFolder, which files the
// video into a Video Cloud folder named after the asset's IMMEDIATE DAM parent, except
// at the account root and at the integration path root (both mean "no folder").
// Context: current/docs/core-folder-sync.md and current/docs/dam-sync-on-activation.md.
//
// Needs (each absent -> NOT MEASURED, never a failure):
//   AEM_BASE, AEM_PUBLISH_URL, BRIGHTCOVE_ACCOUNT_ID/_CLIENT_ID/_CLIENT_SECRET, an
//   enabled author replication agent targeting the publish host, exactly one live
//   Brightcove publish path (the listener here), a readable brightcove.log, and for C10
//   a built cloud package in current/all/target.
//
// Writes only e2e-throwaway-* DAM assets/folders and Video Cloud videos/folders, removed
// in afterAll on failure too. C8 (when run) creates a sibling root
// /content/dam/brightcove_assets_archive because the claim cannot be measured without it;
// it is removed in afterAll.
const { test, markLive, expect, resolveAccountId, brightcoveAssetsRoot, slingPost } = require('../fixtures');
const { AEM_BASE } = require('../target');
const { creds, cmsClient } = require('../cms');
const dam = require('../dam');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

markLive(test);

const LISTENER = 'com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener';
const SYNC_MODEL = '/var/workflow/models/bc-sync-new-asset';
const BRC_LOG = '/logs/brightcove.log';
// The built cloud package, resolved by glob: a standing spec must not pin a release
// number (7.4.3 when this was written, 7.4.4-SNAPSHOT now). The on-prem artifact
// (-prem.zip) is excluded. Absent -> C10 skips as NOT MEASURED.
const TARGET_DIR = path.join(__dirname, '..', '..', '..', 'current', 'all', 'target');
const EVIDENCE = process.env.EVIDENCE_DIR || path.join(__dirname, '..', 'test-results');

function cloudPackage() {
  let names;
  try { names = fs.readdirSync(TARGET_DIR); } catch (e) { return null; }
  const zips = names.filter((n) => /^brightcove\.all-.*\.zip$/.test(n) && !/-prem\.zip$/.test(n)).sort();
  return zips.length ? path.join(TARGET_DIR, zips[zips.length - 1]) : null;
}

test.setTimeout(12 * 60_000);

let cms;
let pub;
let acct;
let root; // <damIntegrationPath>/<accountId>
let integrationPath;
let integrationSegment; // last segment of the integration path
let foldersBefore; // Map id -> folder object, at beforeAll
let mode; // 'listener' | 'agent'
let archiveRoot; // /content/dam/brightcove_assets_archive (C8)
let rootsBefore = {}; // each root's brc_folder_id before this file touched it
const t = dam.tracker();
const RUN = dam.stamp();
const OBS = {}; // observed values, written to the evidence JSON

test.beforeAll(async ({ request }) => {
  test.skip(!creds(), 'NOT MEASURED: BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set');
  test.skip(!dam.AEM_PUBLISH_URL, 'NOT MEASURED: AEM_PUBLISH_URL not set');

  cms = await cmsClient();
  acct = await resolveAccountId(request);
  if (cms.accountId !== acct) {
    throw new Error('the Video Cloud credentials belong to a different account than the one configured in AEM; refusing to write');
  }
  root = await brightcoveAssetsRoot(request);
  if (!root.endsWith(`/${acct}`)) throw new Error(`brightcoveAssetsRoot ${root} does not end with the account id ${acct}`);
  integrationPath = root.slice(0, -(acct.length + 1));
  integrationSegment = path.posix.basename(integrationPath);
  archiveRoot = `${integrationPath}_archive`;

  const modes = await dam.runModes(request);
  if (!modes.includes('author')) throw new Error(`AEM_BASE is not an author (run modes ${modes.join(',')})`);

  const l = await dam.component(request, LISTENER);
  if (!l) throw new Error(`${LISTENER} is not registered: is the connector installed?`);
  const agents = await dam.enabledAgents(request);
  const bcAgents = agents.filter((a) => a.transportUri.toLowerCase().startsWith('brightcove://'));
  const listenerOn = String(l.props.isEnabled) === 'true';
  test.skip(!listenerOn && !bcAgents.length,
    `NOT MEASURED: no Brightcove publish path is live (${LISTENER} isEnabled=${l.props.isEnabled}, brightcove:// agents=${bcAgents.map((a) => a.name).join(',') || 'none'})`);
  if (listenerOn && bcAgents.length) {
    throw new Error(`both the listener and a brightcove:// agent (${bcAgents.map((a) => a.name)}) are enabled: activations would be processed twice`);
  }
  mode = listenerOn ? 'listener' : 'agent';
  test.skip(mode !== 'listener', `NOT MEASURED: this spec measures the listener path and only the brightcove:// agent (${bcAgents.map((a) => a.name).join(',')}) is live here; the agent path is covered by dam-publish-tier`);

  pub = await dam.publishContext();
  const probe = await pub.get('/system/console/bundles.json');
  if (!probe.ok()) throw new Error(`publish ${dam.AEM_PUBLISH_URL} unreachable with the publish credentials (HTTP ${probe.status()})`);

  const intNode = await dam.json(request, integrationPath);
  if (!intNode) throw new Error(`integration path ${integrationPath} does not exist`);
  const rootNode = await dam.json(request, root);
  if (!rootNode) throw new Error(`account root ${root} does not exist`);

  // Snapshot both roots so afterAll can leave the instance exactly as it was found. A
  // stale id here is Trap 4 evidence on this instance: stripping it would be a write
  // the run was not asked to make.
  rootsBefore = {
    [root]: rootNode.brc_folder_id !== undefined ? String(rootNode.brc_folder_id) : null,
    [integrationPath]: intNode.brc_folder_id !== undefined ? String(intNode.brc_folder_id) : null,
  };

  foldersBefore = new Map((await cms.folders()).map((f) => [f.id, f]));
  const bundle = JSON.parse(await (await request.get('/system/console/bundles.json')).text()).data
    .find((b) => b.symbolicName === 'brightcove.core');

  OBS.setup = {
    accountId: acct,
    accountRoot: root,
    integrationPath,
    integrationSegment,
    accountRootBrcFolderId: rootNode.brc_folder_id === undefined ? null : rootNode.brc_folder_id,
    integrationRootBrcFolderId: intNode.brc_folder_id === undefined ? null : intNode.brc_folder_id,
    listenerIsEnabled: l.props.isEnabled,
    listenerTopics: l.props['event.topics'] || null,
    enabledBrightcoveAgents: bcAgents.map((a) => a.name),
    publishPath: mode,
    deployedBundleVersion: bundle ? bundle.version : null,
    foldersBefore: [...foldersBefore.values()],
    folderNamedAfterAccountBefore: [...foldersBefore.values()].filter((f) => f.name === acct).map((f) => f.id),
    folderNamedAfterIntegrationSegmentBefore: [...foldersBefore.values()].filter((f) => f.name === integrationSegment).map((f) => f.id),
  };
});

test.afterAll(async ({ request }) => {
  const notes = [];
  const rootsAfter = [];
  // No case here writes brc_folder_id to a root any more: C4 asserts the fixed "the
  // integration path root means no folder" rule, which writes nothing. Restore the
  // snapshot anyway so a run can never leave a root changed, and so a root that
  // already carried a stale id (Trap 4 evidence) keeps it.
  for (const p of [integrationPath, root]) {
    const before = rootsBefore[p] ?? null;
    try {
      const n = await dam.json(request, p);
      const now = n && n.brc_folder_id !== undefined ? String(n.brc_folder_id) : null;
      if (now !== before) {
        if (before === null) await dam.setProps(request, p, { 'brc_folder_id@Delete': '' });
        else await dam.setProps(request, p, { brc_folder_id: before });
        notes.push(`${p} brc_folder_id ${now} -> restored to ${before}`);
      }
      const after = await dam.json(request, p);
      rootsAfter.push({ path: p, before, after: after && after.brc_folder_id !== undefined ? String(after.brc_folder_id) : null });
    } catch (e) {
      notes.push(`could not restore ${p}: ${e.message}`);
      rootsAfter.push({ path: p, before, after: 'unreadable' });
    }
  }

  const errors = await t.cleanup({ cms, request, pub });

  // C8's sibling root, created only for this measurement.
  if (OBS.C8 && OBS.C8.archiveRootCreated) {
    await rawDelete(request, archiveRoot);
  }

  // Leftovers this run created: every tracked video re-read directly, every
  // tracked folder name re-listed.
  const leftoverVideos = [];
  for (const id of t.videos) {
    if (await cms.tryGet(id)) leftoverVideos.push(String(id));
  }
  const names = [...t.bcFolderNames];
  const leftoverFolders = (await cms.folders()).filter((f) => names.includes(f.name)).map((f) => ({ id: f.id, name: f.name }));
  const leftoverDam = [];
  for (const p of [...t.assets, ...t.folders]) {
    if (await dam.json(request, p)) leftoverDam.push(p);
  }

  OBS.cleanup = { errors, notes, rootsAfter, leftoverVideos, leftoverFolders, leftoverDam };
  try {
    fs.mkdirSync(EVIDENCE, { recursive: true });
    fs.writeFileSync(path.join(EVIDENCE, 'folder-sync-claims-evidence.json'), JSON.stringify(OBS, null, 2));
  } catch (e) {
    console.warn(`could not write evidence: ${e.message}`);
  }
  // Per-worker cleanup record, so a worker restart does not hide an earlier
  // worker's leftovers.
  try {
    const all = readObs();
    all.cleanupByWorker = all.cleanupByWorker || {};
    all.cleanupByWorker[test.info().workerIndex] = OBS.cleanup;
    writeObs(all);
  } catch (e) {
    console.warn(`could not record cleanup: ${e.message}`);
  }
  if (pub) await pub.dispose();

  expect(errors, 'cleanup reported problems (see the warning above)').toEqual([]);
  expect({ leftoverVideos, leftoverFolders, leftoverDam }, 'throwaway objects left behind').toEqual({ leftoverVideos: [], leftoverFolders: [], leftoverDam: [] });
  expect(rootsAfter.filter((r) => r.before !== r.after), 'a DAM root was not left in its pre-run state').toEqual([]);
});

// ---- helpers ---------------------------------------------------------------------

function unique(label) {
  return `${dam.PREFIX}${label}-${RUN}`;
}

async function rawDelete(request, nodePath) {
  const tok = await request.get('/libs/granite/csrf/token.json');
  const token = tok.ok() ? (await tok.json()).token : '';
  const r = await request.post(nodePath, {
    form: { ':operation': 'delete' },
    headers: { Referer: `${AEM_BASE}/`, 'CSRF-Token': token },
  });
  if (r.status() >= 300 && r.status() !== 404) console.warn(`[verify] raw delete ${nodePath}: HTTP ${r.status()}`);
}

async function mkfolder(request, folderPath) {
  await dam.createDamFolder(request, folderPath);
  t.folders.add(folderPath);
  // The connector files the video into a Video Cloud folder named after the
  // immediate DAM parent, so register that name for the guarded folder delete.
  t.bcFolderNames.add(path.posix.basename(folderPath));
  return folderPath;
}

// Upload a throwaway asset, title it with its own prefixed name (so the video the
// connector creates carries the prefix and the guarded delete can remove it), and
// return its jcr:uuid (the reference_id the connector uses).
async function upload(request, folderPath, label) {
  const name = unique(label);
  const { assetPath, assetState } = await dam.uploadAsset(request, folderPath, `${name}.mp4`);
  t.assets.add(assetPath);
  await dam.setMetadata(request, assetPath, { 'dc:title': name });
  const node = await dam.assetNode(request, assetPath);
  const uuid = node && node['jcr:uuid'];
  expect(uuid, `${assetPath} has no jcr:uuid: the reference_id guard would be off`).toBeTruthy();
  return { name, assetPath, uuid, assetState };
}

async function brcIdOf(request, assetPath) {
  const m = await dam.metadata(request, assetPath);
  return m ? m.brc_id : undefined;
}

async function waitBrcId(request, assetPath, timeout = 180_000) {
  return dam.pollUntil(async () => (await brcIdOf(request, assetPath)) || null, { timeout, interval: 3_000 });
}

async function waitFolderOf(videoId, timeout = 90_000) {
  return dam.pollUntil(async () => {
    const v = await cms.get(videoId);
    return v.folder_id || null;
  }, { timeout, interval: 3_000 });
}

async function foldersNamed(name) {
  return (await cms.folders()).filter((f) => f.name === name);
}

async function logFor(request, needle) {
  return dam.logLines(request, BRC_LOG, needle, 20_000);
}

// The log lines containing `needle`, or a NOT MEASURED skip when the tailer could not
// be read at all. ⚠️ null is NOT zero: a caller that read it as zero would pass a
// "this line never appeared" assertion for the wrong reason, so the callers skip.
async function needLog(request, needle, what) {
  const lines = await logFor(request, needle);
  test.skip(!lines, `NOT MEASURED: brightcove.log could not be read through the log tailer, so "${what}" cannot be attributed to this run`);
  return lines;
}

const countWith = (lines, ...needles) => lines.filter((l) => needles.every((n) => l.includes(n))).length;

// A DAM node's brc_folder_id as a string, or null when the property is absent.
async function damFolderProperty(request, folderPath) {
  const n = await dam.json(request, folderPath);
  return n && n.brc_folder_id !== undefined ? String(n.brc_folder_id) : null;
}

// The replication markers the classic Replicator writes on jcr:content (measured:
// cq:lastReplicationAction sits there, not on the asset node). Read both so the
// "no activation" negative cannot pass because it looked in the wrong place.
async function replicationMarkers(request, assetPath) {
  const node = (await dam.json(request, assetPath)) || {};
  const content = (await dam.json(request, `${assetPath}/jcr:content`)) || {};
  return {
    onAssetNode: node['cq:lastReplicationAction'],
    onContent: content['cq:lastReplicationAction'],
    replicatedOnContent: content['cq:lastReplicated'],
  };
}

// Sling log lines look like "20.11.2026 10:08:54.654 *ERROR* ...". Parse the local
// timestamp so a line can be attributed to one activation rather than to an
// earlier run in the same tail window.
function logTimeMs(line) {
  const m = /^(\d{2})\.(\d{2})\.(\d{4}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})/.exec(line);
  if (!m) return null;
  return new Date(Number(m[3]), Number(m[2]) - 1, Number(m[1]), Number(m[4]), Number(m[5]), Number(m[6]), Number(m[7])).getTime();
}

async function logSince(request, needle, sinceMs, slackMs = 3_000) {
  const lines = (await dam.logLines(request, BRC_LOG, needle, 20_000)) || [];
  return lines.filter((l) => {
    const t = logTimeMs(l);
    return t !== null && t >= sinceMs - slackMs;
  });
}

// A ReplicationAction carrying several paths, which is the event shape a tree
// activation produces (the classic Replicator accepts repeated `path` params).
async function activatePaths(request, paths) {
  const tok = await request.get('/libs/granite/csrf/token.json');
  const { token } = await tok.json();
  const body = ['cmd=Activate', ...paths.map((p) => `path=${encodeURIComponent(p)}`)].join('&');
  const res = await request.post('/bin/replicate.json', {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-Token': token, Referer: `${AEM_BASE}/` },
    data: body,
  });
  expect(res.status(), `activate [${paths.join(', ')}]`).toBeLessThan(300);
}

// ---- evidence ----------------------------------------------------------------------
// Playwright restarts the worker after a failed test, so a module-level object
// written only in afterAll loses the earlier tests' observations. Flush after every
// test, keyed by claim, so the evidence file is complete whatever the failures do.
const OBS_FILE = path.join(EVIDENCE, 'folder-sync-claims-observations.json');

function readObs() {
  try { return JSON.parse(fs.readFileSync(OBS_FILE, 'utf8')); } catch (e) { return {}; }
}

function writeObs(all) {
  try {
    fs.mkdirSync(EVIDENCE, { recursive: true });
    fs.writeFileSync(OBS_FILE, JSON.stringify(all, null, 2));
  } catch (e) {
    console.warn(`[verify] could not write evidence: ${e.message}`);
  }
}

test.afterEach(async ({}, testInfo) => {
  const all = readObs();
  if (!all.setup && OBS.setup) all.setup = OBS.setup; // the first worker's is the clean baseline
  const m = /^(C\d+)/.exec(testInfo.title);
  const key = m ? m[1] : testInfo.title;
  all[key] = {
    title: testInfo.title,
    status: testInfo.status,
    workerIndex: testInfo.workerIndex,
    observations: OBS[key] || null,
  };
  writeObs(all);
});

// ---- C1 ----------------------------------------------------------------------------

test('C1: an activated asset 3 folders deep below the account root creates a Video Cloud video', async ({ request }) => {
  const a = unique('c1-a');
  const b = unique('c1-b');
  const c = unique('c1-c');
  await mkfolder(request, `${root}/${a}`);
  await mkfolder(request, `${root}/${a}/${b}`);
  await mkfolder(request, `${root}/${a}/${b}/${c}`);
  const folderPath = `${root}/${a}/${b}/${c}`;
  const depth = folderPath.split('/').length - root.split('/').length;
  expect(depth, 'the asset sits 3 folders below the account root').toBe(3);

  const asset = await upload(request, folderPath, 'c1-asset');
  const startedAt = Date.now();
  await dam.activate(request, asset.assetPath);

  const brcId = await waitBrcId(request, asset.assetPath);
  expect(brcId, 'the listener never wrote brc_id back to the asset, so no video was created').toBeTruthy();
  t.videos.add(String(brcId));

  const v = await cms.getByRef(asset.uuid);
  expect(v, `no CMS video with reference_id ${asset.uuid} (fresh CMS GET)`).not.toBeNull();
  expect(String(v.id), 'the asset points at a different video than the one found by reference_id').toBe(String(brcId));
  expect(v.name).toBe(asset.name);

  const lines = await needLog(request, asset.assetPath, 'the listener lines naming this asset');
  const fired = lines.filter((l) => l.includes('Activating New Brightcove Asset'));
  expect(fired.length, `no "Activating New Brightcove Asset" line for ${asset.assetPath}`).toBeGreaterThanOrEqual(1);

  // The binary really arrived, not just a CMS record.
  const sources = await dam.pollUntil(async () => {
    const s = await cms.sources(v.id);
    return s.length ? s : null;
  }, { timeout: 6 * 60_000, interval: 10_000 });
  expect(sources, `video ${v.id} has no sources 6 min after activation: the S3 upload or ingest failed`).not.toBeNull();

  OBS.C1 = {
    assetPath: asset.assetPath,
    depth,
    videoId: v.id,
    videoName: v.name,
    sources: sources.map((s) => ({ src: s.src ? s.src.split('?')[0] : null, duration: s.duration })),
    elapsedMsToBrcId: Date.now() - startedAt,
    log: fired,
  };
});

// ---- C2 ----------------------------------------------------------------------------

test('C2: the video lands in a flat Video Cloud folder named exactly after its immediate AEM parent', async ({ request }) => {
  // Three levels, all different names, so "immediate parent" cannot be confused
  // with an ancestor.
  const a = unique('c2-a');
  const b = unique('c2-b');
  const c = unique('c2-c');
  await mkfolder(request, `${root}/${a}`);
  await mkfolder(request, `${root}/${a}/${b}`);
  await mkfolder(request, `${root}/${a}/${b}/${c}`);

  const asset = await upload(request, `${root}/${a}/${b}/${c}`, 'c2-asset');
  await dam.activate(request, asset.assetPath);
  const brcId = await waitBrcId(request, asset.assetPath);
  expect(brcId, 'no brc_id: no video was created').toBeTruthy();
  t.videos.add(String(brcId));

  const v = await cms.getByRef(asset.uuid);
  expect(v).not.toBeNull();
  const folderId = await waitFolderOf(v.id);
  expect(folderId, `video ${v.id} has no folder_id after activation`).toBeTruthy();

  const folder = (await cms.folders()).find((f) => f.id === folderId);
  expect(folder, `Video Cloud has no folder ${folderId}`).toBeTruthy();
  expect(folder.name, 'folder must be named after the immediate parent').toBe(c);
  expect(folder.name, 'folder was named after the grandparent').not.toBe(b);
  expect(folder.name, 'folder was named after the great-grandparent').not.toBe(a);
  expect(folder.name, 'folder was named after the account').not.toBe(acct);
  expect(folder.name, 'folder was named after the integration path').not.toBe(integrationSegment);

  // Flat: the folder is a top-level object in the account's folder list, created by
  // this test (unique name), and carries no parent/hierarchy field.
  expect(foldersBefore.has(folderId), 'the folder already existed; not attributable to this activation').toBe(false);
  expect(Object.keys(folder).filter((k) => /parent|child|depth|hierarchy|ancestor/i.test(k))).toEqual([]);

  OBS.C2 = {
    assetPath: asset.assetPath,
    videoId: v.id,
    folderId,
    folderName: folder.name,
    immediateParent: c,
    ancestors: [b, a],
    folderObject: folder,
    log: (await logFor(request, asset.assetPath)) || [],
  };
});

// ---- C3 ----------------------------------------------------------------------------

test('C3: an asset directly in the account root produces a video with no folder and no folder named after the account', async ({ request }) => {
  const beforeAcct = (await foldersNamed(acct)).map((f) => f.id).sort();
  const asset = await upload(request, root, 'c3-asset');
  const rootSkipBefore = ((await logFor(request, root)) || []).filter((l) => l.includes('Asset is at account root level')).length;
  await dam.activate(request, asset.assetPath);

  const brcId = await waitBrcId(request, asset.assetPath);
  expect(brcId, 'no brc_id: the activation was never processed, so "no folder" would pass vacuously').toBeTruthy();
  t.videos.add(String(brcId));

  const v = await cms.getByRef(asset.uuid);
  expect(v).not.toBeNull();
  const folderId = v.folder_id === undefined ? null : v.folder_id;
  expect(folderId, `video ${v.id} was filed into a folder (${folderId}) instead of staying at the account root`).toBeNull();

  const afterAcct = (await foldersNamed(acct)).map((f) => f.id).sort();
  expect(afterAcct, 'a folder named after the account id was created').toEqual(beforeAcct);

  // The account-root branch must be the one that ran, for THIS activation.
  const lines = await needLog(request, root, 'the account-root guard lines');
  const rootSkipAfter = lines.filter((l) => l.includes('Asset is at account root level')).length;
  expect(rootSkipAfter, 'no new "Asset is at account root level" line appeared during this activation: the guard did not run').toBeGreaterThan(rootSkipBefore);
  const fired = (await logFor(request, asset.assetPath)) || [];
  expect(fired.filter((l) => l.includes('Activating New Brightcove Asset')).length,
    `the listener did not process ${asset.assetPath}`).toBeGreaterThanOrEqual(1);

  OBS.C3 = {
    assetPath: asset.assetPath,
    videoId: v.id,
    folderId,
    accountFoldersBefore: beforeAcct,
    accountFoldersAfter: afterAcct,
    rootSkipLinesBefore: rootSkipBefore,
    rootSkipLinesAfter: rootSkipAfter,
    log: lines.filter((l) => l.includes('Asset is at account root level')).slice(-3),
  };
});

// ---- C4 ----------------------------------------------------------------------------

test('C4: an asset in the integration-path root is ingested with NO folder and the WARN names the path', async ({ request }) => {
  // Trap 6: the integration path root means "no folder", like the account root. The old
  // behaviour gave the asset a Video Cloud folder named after the path's last segment
  // (or, on an account that already had one, left it unfiled after a 409). Both are gone:
  // no create is attempted at all.
  const beforeSame = (await foldersNamed(integrationSegment)).map((f) => f.id);
  const warnBefore = countWith(await needLog(request, 'DAM integration path root', 'the integration-root WARN'), 'DAM integration path root');
  const asset = await upload(request, integrationPath, 'c4-asset');
  const startedAt = Date.now();
  await dam.activate(request, asset.assetPath);

  const brcId = await waitBrcId(request, asset.assetPath);
  expect(brcId, 'no brc_id: the asset in the integration-path root was never ingested').toBeTruthy();
  t.videos.add(String(brcId));

  const v = await cms.getByRef(asset.uuid);
  expect(v, `no CMS video with reference_id ${asset.uuid}`).not.toBeNull();

  // Positive signal that the ROOT branch ran, not merely "no folder yet": the WARN
  // naming this exact path is new since the test started.
  const warn = await dam.pollUntil(async () => {
    const l = await logFor(request, 'DAM integration path root');
    if (!l) return null;
    return countWith(l, 'DAM integration path root', integrationPath) > warnBefore ? l : null;
  }, { timeout: 60_000, interval: 3_000 });

  const afterSame = (await foldersNamed(integrationSegment)).map((f) => f.id);
  const newIds = afterSame.filter((id) => !beforeSame.includes(id));
  const conflicts = await logSince(request, 'FOLDER_NAME_IN_USE', startedAt);
  const gaveUp = await logSince(request, 'No folder created', startedAt);
  const rootProperty = await damFolderProperty(request, integrationPath);
  const warnLines = ((await logFor(request, 'DAM integration path root')) || []).filter((l) => l.includes(integrationPath));
  // Re-read the video AFTER the WARN: the guard has returned by then, so no move can
  // still be in flight. Reading it earlier would let a slow (buggy) move look unfiled.
  const finalV = await cms.get(v.id);
  const folderId = finalV.folder_id === undefined ? null : finalV.folder_id;

  // Record the end state BEFORE asserting, so a failure still carries the
  // observed values into the evidence file.
  OBS.C4 = {
    assetPath: asset.assetPath,
    ingested: Boolean(brcId),
    videoId: v.id,
    videoFolderId: folderId,
    integrationSegment,
    foldersNamedBefore: beforeSame,
    foldersNamedAfter: afterSame,
    newFolderIds: newIds,
    integrationRootBrcFolderIdAfter: rootProperty,
    folderNameInUseConflicts: conflicts.map((l) => l.replace(/^.*HttpServices /, '')),
    noFolderCreated: gaveUp.map((l) => l.replace(/^.*FolderSyncUtil /, '')),
    warnLines: warnLines.slice(-3).map((l) => l.slice(0, 260)),
    log: (await logFor(request, asset.assetPath)) || [],
  };

  expect(warn, `no WARN naming ${integrationPath} appeared for this activation, so the root branch may not have run`).not.toBeNull();
  expect(folderId,
    `video ${v.id} was filed into folder ${folderId}: an asset in the integration path root has no DAM folder of its own`).toBeNull();
  // No create was ATTEMPTED: neither a refused create (409) nor the give-up ERROR.
  expect(conflicts.length, 'a folder create was attempted and refused (FOLDER_NAME_IN_USE)').toBe(0);
  expect(gaveUp.length, 'the folder-sync code gave up on a create it should never have attempted').toBe(0);
  // Nothing was created or removed under the integration path's own name.
  expect(afterSame, 'a folder named after the integration path segment was created or removed').toEqual(beforeSame);
  expect(rootProperty, 'the integration path root must not be marked as synced').toBeNull();
  expect(warnLines.some((l) => l.includes('*WARN*')), 'the integration-root line is not a WARN').toBe(true);
});

// ---- C5 ----------------------------------------------------------------------------

test('C5: two same-named DAM parents in different branches share exactly ONE Video Cloud folder', async ({ request }) => {
  // Trap 5: Video Cloud folder names are unique per account, case-insensitively, and
  // folders are flat, so two DAM folders called `same` under different parents can only
  // ever be ONE Video Cloud folder. The fix looks the name up when the create is refused
  // and files the second video into the folder that already exists. Before it, the second
  // video stayed at the account root with folder_id null.
  const same = unique('c5-same');
  const branchA = unique('c5-branchA');
  const branchB = unique('c5-branchB');
  const folderA = `${root}/${branchA}/${same}`;
  const folderB = `${root}/${branchB}/${same}`;
  await mkfolder(request, `${root}/${branchA}`);
  await mkfolder(request, folderA);
  await mkfolder(request, `${root}/${branchB}`);
  await mkfolder(request, folderB);

  const beforeSame = (await foldersNamed(same)).map((f) => f.id);
  expect(beforeSame, 'no folder with this run-unique name exists yet').toEqual([]);

  const assetA = await upload(request, folderA, 'c5-assetA');
  const assetB = await upload(request, folderB, 'c5-assetB');
  await dam.activate(request, assetA.assetPath);
  const brcA = await waitBrcId(request, assetA.assetPath);
  await dam.activate(request, assetB.assetPath);
  const brcB = await waitBrcId(request, assetB.assetPath);
  if (brcA) t.videos.add(String(brcA));
  if (brcB) t.videos.add(String(brcB));

  const vA = brcA ? await cms.getByRef(assetA.uuid) : null;
  const vB = brcB ? await cms.getByRef(assetB.uuid) : null;
  const afterSame = await foldersNamed(same);
  const folderId = afterSame.length === 1 ? afterSame[0].id : null;
  const fA = vA && folderId ? await waitFolderOf(vA.id) : null;
  const fB = vB && folderId ? await waitFolderOf(vB.id) : null;
  const markedA = await damFolderProperty(request, folderA);
  const markedB = await damFolderProperty(request, folderB);
  const members = folderId ? await cms.folderVideoIds(folderId) : [];
  const gaveUp = (await logFor(request, same)) || [];

  // Record the end state BEFORE asserting (see C4).
  OBS.C5 = {
    sharedName: same,
    branchA: folderA,
    branchB: folderB,
    ingestedA: Boolean(brcA),
    ingestedB: Boolean(brcB),
    videoA: vA ? vA.id : null,
    videoB: vB ? vB.id : null,
    videoAFolderId: vA && vA.folder_id !== undefined ? vA.folder_id : null,
    videoBFolderId: vB && vB.folder_id !== undefined ? vB.folder_id : null,
    folderA: fA || null,
    folderB: fB || null,
    foldersNamedAfter: afterSame.map((f) => f.id),
    damFolderMarkedA: markedA,
    damFolderMarkedB: markedB,
    folderMembers: members,
    noFolderCreated: gaveUp.filter((l) => l.includes('No folder created')).length,
    reuseLogLines: gaveUp.filter((l) => l.includes('already exists')).length,
  };

  expect(brcA, 'asset A was never ingested').toBeTruthy();
  expect(brcB, 'asset B was never ingested').toBeTruthy();
  expect(vA).not.toBeNull();
  expect(vB).not.toBeNull();

  // Exactly ONE Video Cloud folder of that name: Video Cloud refuses a second one.
  expect(afterSame.map((f) => f.id), `exactly one Video Cloud folder may carry the name '${same}', saw ${afterSame.length}`).toHaveLength(1);
  expect(foldersBefore.has(folderId), 'the folder must be new, not a pre-existing one that happens to share the name').toBe(false);

  // Both videos in it. A null folder_id on either is the pre-fix defect.
  expect(fA, `video A (${vA.id}) was not filed into ${folderId}: folder_id=${vA.folder_id}`).toBe(folderId);
  expect(fB, `video B (${vB.id}) was not filed into ${folderId}: folder_id=${vB.folder_id}`).toBe(folderId);
  expect(members.sort(), 'the folder must hold exactly the two videos').toEqual([vA.id, vB.id].sort());

  // Both DAM folders marked with the same id: the flat-namespace consequence, documented
  // as out of scope rather than a defect (current/docs/core-folder-sync.md, Trap 5).
  expect(markedA, 'DAM folder A never got a brc_folder_id').toBe(folderId);
  expect(markedB, 'DAM folder B never got a brc_folder_id').toBe(folderId);
  expect(OBS.C5.noFolderCreated, 'the connector gave up on the second create instead of reusing the folder').toBe(0);
});

// ---- C6 ----------------------------------------------------------------------------

test('C6: uploading an asset with no activation does not ingest it', async ({ request }) => {
  const folder = unique('c6-folder');
  await mkfolder(request, `${root}/${folder}`);
  const asset = await upload(request, `${root}/${folder}`, 'c6-asset');

  // The upload helper already waited for the asset to settle. Give any
  // activation-independent path (a launcher, a scheduler) a generous window.
  await dam.sleep(60_000);

  const m = await dam.metadata(request, asset.assetPath);
  expect(m, 'the asset has no metadata: the upload itself failed, so absence of a video proves nothing').not.toBeNull();
  expect(m.brc_id, 'brc_id was written without any activation').toBeUndefined();
  expect(await cms.getByRef(asset.uuid), `a CMS video with reference_id ${asset.uuid} exists without any activation`).toBeNull();

  const markers = await replicationMarkers(request, asset.assetPath);
  expect(markers.onContent, 'the asset was replicated, so "no activation" is false').toBeUndefined();
  expect(markers.onAssetNode, 'the asset node carries a replication marker, so "no activation" is false').toBeUndefined();

  const lines = await needLog(request, asset.assetPath, 'the listener lines naming this asset');
  expect(lines.filter((l) => l.includes('Activating New Brightcove Asset')).length, 'the listener processed this asset without an activation').toBe(0);
  expect(lines.filter((l) => l.includes(`Found Brightcove Account ID #${acct} for ${asset.assetPath}`)).length, 'the listener matched this path without an activation').toBe(0);

  OBS.C6 = {
    assetPath: asset.assetPath,
    assetState: asset.assetState,
    brcId: m.brc_id === undefined ? null : m.brc_id,
    cqLastReplicationAction: markers,
    waitedMs: 60_000,
    log: lines,
  };
});

// ---- C7 ----------------------------------------------------------------------------

test('C7: a folder tree activation ingests every asset in the folder', async ({ request }) => {
  const folder = unique('c7-folder');
  await mkfolder(request, `${root}/${folder}`);
  const assets = [];
  for (const label of ['c7-a1', 'c7-a2', 'c7-a3']) assets.push(await upload(request, `${root}/${folder}`, label));

  // Tree activation: one ReplicationAction carrying the folder and every descendant
  // path (the event shape the classic tree-activation servlet produces).
  await activatePaths(request, [`${root}/${folder}`, ...assets.map((a) => a.assetPath)]);

  const ids = [];
  for (const a of assets) {
    const id = await waitBrcId(request, a.assetPath, 240_000);
    ids.push(id);
    if (id) t.videos.add(String(id));
  }
  const missing = assets.filter((a, i) => !ids[i]).map((a) => a.assetPath);
  expect(missing, `assets in the activated folder that were not ingested: ${missing.join(', ')}`).toEqual([]);
  for (const a of assets) {
    expect(await cms.getByRef(a.uuid), `no CMS video for ${a.assetPath}`).not.toBeNull();
  }

  // Observation only (not the claim): what a bare folder-path activation does.
  const solo = unique('c7-solo');
  await mkfolder(request, `${root}/${solo}`);
  const soloAsset = await upload(request, `${root}/${solo}`, 'c7-solo-asset');
  await dam.activate(request, `${root}/${solo}`); // the folder path alone
  const soloId = await dam.pollUntil(async () => (await brcIdOf(request, soloAsset.assetPath)) || null, { timeout: 45_000, interval: 3_000 });
  if (soloId) t.videos.add(String(soloId));

  OBS.C7 = {
    folder: `${root}/${folder}`,
    assets: assets.map((a, i) => ({ path: a.assetPath, videoId: ids[i] })),
    folderPathOnlyActivation: {
      folder: `${root}/${solo}`,
      asset: soloAsset.assetPath,
      ingested: Boolean(soloId),
      note: 'a bare folder-path activation is the folder node only; the listener cannot adapt a folder to an Asset',
    },
    log: (await logFor(request, `${root}/${folder}`)) || [],
  };
});

// ---- C8 ----------------------------------------------------------------------------

// 🔴 DEFECT, not a pending fix: the listener scopes by SUBSTRING, not by path boundary.
// `listeners/BrightcovePublishListener` matches `asset.contains(damIntegrationPath)`, so a
// sibling tree such as /content/dam/brightcove_assets_archive is treated as managed and its
// assets are ingested. Measured live on 7.4.3 and still true on 7.4.4-SNAPSHOT (2026-11-20).
// Context: current/docs/core-folder-sync.md "Trap 6", which records it as a separate defect
// out of scope for folder sync.
// DEFECT: the listener's path scoping is a substring test, so a shared-prefix sibling is ingested.
// The body asserts the behaviour we WANT (not ingested), so it does not pin the bug; promote
// it to `test` once the scoping has a path boundary and it should pass as written.
test.fixme('C8: an asset in a sibling tree that only shares the integration path as a string prefix is NOT ingested', async ({ request }) => {
  // The claim cannot be measured unless the sibling root exists. Create it if it
  // does not, and remove it in afterAll.
  const existing = await dam.json(request, archiveRoot);
  OBS.C8 = { archiveRoot, archiveRootCreated: !existing };
  if (!existing) {
    const res = await slingPost(request, archiveRoot, {
      'jcr:primaryType': 'sling:OrderedFolder',
      'jcr:content/jcr:primaryType': 'nt:unstructured',
      'jcr:content/jcr:title': path.posix.basename(archiveRoot),
    });
    expect(res.status(), `create ${archiveRoot}`).toBeLessThan(300);
  }
  const folder = unique('c8-folder');
  await mkfolder(request, `${archiveRoot}/${folder}`);
  const asset = await upload(request, `${archiveRoot}/${folder}`, 'c8-asset');

  // Precondition: the path does NOT sit under the configured integration path.
  expect(asset.assetPath.startsWith(`${integrationPath}/`), 'the asset is under the integration path, so this is not the substring case').toBe(false);
  expect(asset.assetPath.includes(integrationPath), 'the asset path does not contain the integration path').toBe(true);

  await dam.activate(request, asset.assetPath);

  // Positive signal that the activation itself was delivered, so "not ingested" cannot pass
  // because the publish path was dead.
  const markers = await dam.pollUntil(async () => {
    const m = await replicationMarkers(request, asset.assetPath);
    return m.onContent || m.onAssetNode ? m : null;
  }, { timeout: 60_000, interval: 3_000 });
  expect(markers, `the activation of ${asset.assetPath} was never replicated, so "not ingested" would pass for the wrong reason`).not.toBeNull();

  // The window an ingest of this fixture actually needs, before concluding nothing happened.
  const brcId = await dam.pollUntil(async () => (await brcIdOf(request, asset.assetPath)) || null, { timeout: 120_000, interval: 5_000 });
  if (brcId) t.videos.add(String(brcId)); // tracked even though the assertion below fails
  const v = await cms.getByRef(asset.uuid);
  const lines = await logFor(request, asset.assetPath);

  OBS.C8 = {
    ...OBS.C8,
    assetPath: asset.assetPath,
    ingested: Boolean(brcId),
    videoId: v ? v.id : null,
    replicationMarkers: markers,
    log: lines || null,
  };

  expect(brcId, `the asset under ${archiveRoot} was ingested: path scoping is a substring test (the defect)`).toBeNull();
  expect(v, `no CMS video may exist with reference_id ${asset.uuid}`).toBeNull();
  if (lines) {
    expect(lines.filter((l) => l.includes(`Found Brightcove Account ID #${acct} for ${asset.assetPath}`)).length,
      'the listener matched the shared-prefix sibling path').toBe(0);
  }
});

// ---- C9 ----------------------------------------------------------------------------

test('C9: the bc-sync-new-asset workflow ingests with no activation', async ({ request }) => {
  const folder = unique('c9-folder');
  await mkfolder(request, `${root}/${folder}`);
  const asset = await upload(request, `${root}/${folder}`, 'c9-asset');

  const beforeMarkers = await replicationMarkers(request, asset.assetPath);
  expect(beforeMarkers.onContent, 'the asset was already activated; the workflow is not being measured on an unactivated asset').toBeUndefined();
  expect(beforeMarkers.onAssetNode, 'the asset was already activated; the workflow is not being measured on an unactivated asset').toBeUndefined();
  expect(await cms.getByRef(asset.uuid), 'a video already exists for this asset').toBeNull();

  const startedAt = Date.now();
  const wf = await dam.runWorkflow(request, SYNC_MODEL, asset.assetPath, { timeout: 300_000 });
  const elapsedMs = Date.now() - startedAt;
  expect(wf.status, `workflow ${wf.instance}`).toBe('COMPLETED');

  const brcId = await waitBrcId(request, asset.assetPath, 60_000);
  expect(brcId, 'the workflow step never wrote brc_id: no video was created').toBeTruthy();
  t.videos.add(String(brcId));
  const v = await cms.getByRef(asset.uuid);
  expect(v, `no CMS video with reference_id ${asset.uuid} after the workflow`).not.toBeNull();

  const afterMarkers = await replicationMarkers(request, asset.assetPath);
  expect(afterMarkers.onContent, 'the workflow activated the asset, so "no activation" is false').toBeUndefined();
  expect(afterMarkers.onAssetNode, 'the workflow activated the asset, so "no activation" is false').toBeUndefined();

  // elapsedMs is RECORDED, not asserted. The old assertion was a >= 15s lower bound,
  // which pins wall-clock time rather than behaviour and can only ever fail when the
  // system gets faster. The 15s figure is FolderSyncUtil.WORKFLOW_RETRY_DELAY_SECONDS,
  // and that delay only applies on a REFUSED folder create, which a run-unique folder
  // name never triggers; the behaviour itself is pinned by the unit test
  // theRetryFindsAFolderCreatedWhileTheWorkflowWaited.

  OBS.C9 = {
    assetPath: asset.assetPath,
    workflow: wf,
    elapsedMs,
    videoId: v.id,
    cqLastReplicationAction: afterMarkers,
  };
});

// ---- C10 ---------------------------------------------------------------------------

test('C10: the shipped cloud package carries no listener config and the metatype default is false', async () => {
  const ZIP = cloudPackage();
  test.skip(!ZIP, `NOT MEASURED: no cloud package in ${TARGET_DIR} (run "mvn clean package -DskipTests" in current/ first), so the shipped-config claim cannot be measured`);
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'bc-c10-'));
  try {
    const outer = execFileSync('unzip', ['-Z1', ZIP], { encoding: 'utf8' }).split('\n');
    const uicfg = outer.find((n) => /brightcove\.ui\.config-.*\.zip$/.test(n));
    const coreJar = outer.find((n) => /brightcove\.core-.*\.jar$/.test(n));
    expect(uicfg, 'no ui.config package in the shipped zip').toBeTruthy();
    expect(coreJar, 'no core bundle in the shipped zip').toBeTruthy();

    execFileSync('unzip', ['-o', '-j', ZIP, uicfg, coreJar, '-d', tmp]);
    const uiList = execFileSync('unzip', ['-Z1', path.join(tmp, path.basename(uicfg))], { encoding: 'utf8' });
    expect(uiList, `the shipped ui.config package carries a BrightcovePublishListener config:\n${uiList}`).not.toMatch(/BrightcovePublishListener/);
    // And no shipped config file's CONTENT names the pid either.
    for (const entry of uiList.split('\n').filter((n) => /\.(cfg\.json|config|cfg)$/.test(n))) {
      const body = execFileSync('unzip', ['-p', path.join(tmp, path.basename(uicfg)), entry], { encoding: 'utf8' });
      expect(body, `${entry} names the listener pid`).not.toMatch(/BrightcovePublishListener/);
    }

    const metatype = execFileSync('unzip', ['-p', path.join(tmp, path.basename(coreJar)),
      'OSGI-INF/metatype/*BrightcovePublishListener*'], { encoding: 'utf8' });
    expect(metatype, 'the listener metatype was not found in the core bundle').toMatch(/id="isEnabled"/);
    expect(metatype, 'the isEnabled metatype default is not false').toMatch(/id="isEnabled"[^>]*default="false"/);

    OBS.C10 = {
      zip: ZIP,
      uiConfigEntries: uiList.split('\n').filter(Boolean),
      metatypeLine: (metatype.match(/<AD id="isEnabled"[^>]*>/) || [null])[0],
    };
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
});

// ---- C11 ---------------------------------------------------------------------------

test('C11: Video Cloud folders are flat, with no parent or hierarchy field', async () => {
  const folders = await cms.folders();
  expect(Array.isArray(folders), 'the folder list is not a flat array').toBe(true);
  expect(folders.length, 'no folders to inspect').toBeGreaterThan(0);

  const keys = [...new Set(folders.flatMap((f) => Object.keys(f)))].sort();
  const hierarchyKeys = keys.filter((k) => /parent|child|depth|hierarchy|ancestor|tree/i.test(k));
  expect(hierarchyKeys, `folder objects expose hierarchy fields: ${hierarchyKeys.join(', ')}`).toEqual([]);

  // No folder carries a nested array/object that could encode children.
  const nested = folders.filter((f) => Object.values(f).some((v) => v !== null && typeof v === 'object'));
  expect(nested.map((f) => f.id), 'a folder object carries a nested value').toEqual([]);

  OBS.C11 = { folderCount: folders.length, keys, sample: folders[0] };
});
