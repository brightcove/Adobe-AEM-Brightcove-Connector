// Parity row 35: the AEM_NO_DAM tag keeps a video out of the DAM on import.
//
// Two throwaway videos on the account AEM is configured with, both ingested so
// they are state ACTIVE (the importer skips non-ACTIVE videos, so an INACTIVE
// "tagged" video would be absent for the wrong reason):
//   - e2e-throwaway-nodam-*  tagged AEM_NO_DAM  -> must NOT become a dam:Asset
//   - e2e-throwaway-dam-*    untagged control   -> MUST become a dam:Asset
// The control is what makes the negative meaningful: it proves the sync ran,
// finished and reached videos created this run. Without it a broken or never-run
// sync would also leave the tagged video absent.
//
// CMS search is eventually consistent, so before syncing the spec polls the CMS
// index until it reports the tag on the tagged video AND lists the control; only
// then is "tagged video not imported" attributable to the filter, not to lag.
//
// Needs BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID / _CLIENT_SECRET of the account AEM is
// configured with (NOT MEASURED, loudly, otherwise). Runs the connector's real
// /bin/brightcove/dataload servlet, the same one the admin Sync button posts to.
// Both videos and any DAM asset they produced are removed in afterAll, even on
// failure. Media source: BRC_E2E_VIDEO_URL (default: a 1 MB CC0 clip).
const { test, expect, resolveAccountId, brightcoveAssetsRoot, slingPost, mediaHostReachable } = require('../fixtures');
const { creds, cmsClient } = require('../cms');

const VIDEO_URL = process.env.BRC_E2E_VIDEO_URL || 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4';
const PREFIX = 'e2e-throwaway-';

test.describe.configure({ mode: 'serial' });
test.setTimeout(900_000);

let cms;
let root;
const created = []; // { id, name }
let tagged;
let control;

async function makeActive(name, fields) {
  const id = await cms.create(name, fields);
  created.push({ id, name });
  await cms.ingest(id, VIDEO_URL);
  return id;
}

test.beforeAll(async ({ request }) => {
  test.skip(!creds(), 'NOT MEASURED: BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set, so no throwaway videos can be created');
  test.skip(!(await mediaHostReachable(VIDEO_URL)), `NOT MEASURED: ${new URL(VIDEO_URL).host} is unreachable from here (set BRC_E2E_VIDEO_URL to a reachable clip), so the throwaways could never become ACTIVE`);
  cms = await cmsClient();
  const aemAccount = await resolveAccountId(request);
  if (cms.accountId !== aemAccount) {
    throw new Error('the credentials in the environment belong to a different account than the one configured in AEM; the sync could never see the throwaway videos');
  }
  root = await brightcoveAssetsRoot(request);
  const stamp = Date.now();
  tagged = await makeActive(`${PREFIX}nodam-${stamp}`, { tags: ['AEM_NO_DAM'] });
  control = await makeActive(`${PREFIX}dam-${stamp}`);
});

test.afterAll(async ({ request }) => {
  // Runs on failure too. Only touches what this file created. The DAM asset is named by the
  // video id (no throwaway prefix of its own), so it is removed only after the SERVER-SIDE name
  // of that video is re-read and carries the prefix; the same guard then deletes the video.
  for (const { id } of created) {
    try {
      const cur = await cms.tryGet(id);
      if (!cur || !String(cur.name || '').startsWith(PREFIX)) {
        console.warn(`cleanup of ${id}: ${cur ? 'NOT deleting, name has no throwaway prefix' : 'video already gone, its DAM asset (if any) is left'}`);
        continue;
      }
      if (await assetStatus(request, id) === 200) { // POSTing a delete to a missing path is a 403
        const res = await slingPost(request, `${root}/${id}.mp4`, { ':operation': 'delete' });
        if (!res.ok()) console.warn(`DAM cleanup of ${id}.mp4 returned HTTP ${res.status()}`);
      }
      await cms.delIfThrowaway(id);
    } catch (e) { console.warn(`cleanup of ${id} failed: ${e.message}`); }
  }
});

async function assetStatus(request, id) {
  return (await request.get(`${root}/${id}.mp4.json`)).status();
}

test('row 35: AEM_NO_DAM video is not imported, untagged control is', async ({ request }) => {
  for (const id of [tagged, control]) {
    const active = await cms.waitActive(id);
    test.skip(!active, `NOT MEASURED: throwaway ${id} never reached state ACTIVE, so its absence from the DAM would prove nothing`);
  }

  // The CMS search index must know about both before the sync is meaningful.
  await expect.poll(async () => (await cms.search(`tags:AEM_NO_DAM name:${PREFIX}nodam-`)).some((v) => String(v.id) === tagged),
    { timeout: 300_000, intervals: [5_000], message: 'CMS search never reported the AEM_NO_DAM tag on the tagged throwaway' }).toBe(true);
  await expect.poll(async () => (await cms.search(`-tags:AEM_NO_DAM name:${PREFIX}dam-`)).some((v) => String(v.id) === control),
    { timeout: 300_000, intervals: [5_000], message: 'CMS search never listed the untagged control throwaway' }).toBe(true);

  // Before the sync neither exists in the DAM (so a pass is not a leftover).
  expect(await assetStatus(request, tagged), 'tagged asset already present before the sync').toBe(404);
  expect(await assetStatus(request, control), 'control asset already present before the sync').toBe(404);

  // The real sync. The servlet answers when its import threads have finished;
  // poll anyway for the control, then assert the tagged one.
  const res = await slingPost(request, '/bin/brightcove/dataload', {});
  expect(res.ok(), `dataload answered HTTP ${res.status()}`).toBeTruthy();

  await expect.poll(() => assetStatus(request, control),
    { timeout: 180_000, intervals: [3_000], message: 'the untagged control never became a DAM asset: the sync did not import it, so the tagged result is not interpretable' }).toBe(200);
  const asset = await (await request.get(`${root}/${control}.mp4.json`)).json();
  expect(asset['jcr:primaryType']).toBe('dam:Asset');

  // The import runs on executor threads: the control landing does not mean the sync is done.
  // Wait until the number of assets under the account folder is stable before asserting an
  // absence, or the negative can pass while the tagged video is still queued.
  const count = async () => {
    const r = await request.get(`${root}.1.json`);
    return r.ok() ? Object.values(await r.json()).filter((n) => n && n['jcr:primaryType'] === 'dam:Asset').length : -1;
  };
  let last = -2; let stable = 0;
  for (let i = 0; i < 40 && stable < 4; i++) {
    const n = await count();
    stable = n === last ? stable + 1 : 0; last = n;
    await new Promise((r) => setTimeout(r, 3_000));
  }
  expect(stable, 'the DAM asset count never settled after the sync, so an absence is not attributable to the filter').toBeGreaterThanOrEqual(4);

  expect(await assetStatus(request, tagged), 'the AEM_NO_DAM-tagged video was imported into the DAM').toBe(404);
});
