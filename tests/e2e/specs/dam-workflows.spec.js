// DAM workflow coverage: parity-matrix rows 29 (sync asset) and 30 (delete asset).
// Both models ship in ui.content and had only ever been confirmed PRESENT, never run.
//
// THE TRIGGER (read from the code): nothing launches these models automatically. The
// package ships the models (/var/workflow/models/bc-sync-new-asset and
// /var/workflow/models/brightcove-delete-asset, plus their /conf design copies) and NO
// launcher, so they run only when someone starts them on an asset (Timeline "Start
// Workflow", or the workflow REST API used here). Each model has one PROCESS step:
//   bc-sync-new-asset       -> workflow/BrightcoveSyncAssetWorkflowStep
//   brightcove-delete-asset -> workflow/BrightcoveDeleteAssetWorkflowStep
// Both resolve the account by checking whether the payload path CONTAINS a configured
// damIntegrationPath, and use the brightcoveWrite service user.
//   Sync step: same create-vs-update gate as the publish listener (brc_lastsync absent
//   -> createVideoS3 ingest; present -> updateVideo on brc_id), then sleeps 15s and
//   pulls the video back from CMS into the asset (ServiceUtil.updateAsset).
//   Delete step: CMS DELETE of brc_id; ONLY on success removes the DAM asset node.
// No publish instance is involved, but the file still requires AEM_PUBLISH_URL: it is
// the opt-in for the publish-tier bed, and it keeps the routine pre-qa-gate run (which
// often has the Video Cloud creds set for bgs-1600) from uploading DAM assets and
// running workflows. Without it, or without the creds, everything skips NOT MEASURED.
//
// Writes only e2e-throwaway-* objects and removes them in afterAll, on failure too.
// The delete case only ever points the step at a video this file created, and checks
// that before starting the workflow.
const { test, expect, resolveAccountId } = require('../fixtures');
const { creds, cmsClient } = require('../cms');
const dam = require('../dam');

const SYNC_MODEL = '/var/workflow/models/bc-sync-new-asset';
const DELETE_MODEL = '/var/workflow/models/brightcove-delete-asset';
const SYNC_STEP = 'com.coresecure.brightcove.wrapper.workflow.BrightcoveSyncAssetWorkflowStep';
const DELETE_STEP = 'com.coresecure.brightcove.wrapper.workflow.BrightcoveDeleteAssetWorkflowStep';

test.describe.configure({ mode: 'serial' });
test.setTimeout(6 * 60_000);

let cms;
let root;
const t = dam.tracker();
const RUN = dam.stamp();

test.beforeAll(async ({ request }) => {
  test.skip(!creds(), 'NOT MEASURED: BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set');
  test.skip(!dam.AEM_PUBLISH_URL, 'NOT MEASURED: AEM_PUBLISH_URL not set (the opt-in for the DAM write specs)');
  cms = await cmsClient();
  const acct = await resolveAccountId(request);
  if (cms.accountId !== acct) {
    throw new Error('the Video Cloud credentials in the environment belong to a different account than the one configured in AEM; refusing to write');
  }
  root = `/content/dam/brightcove_assets/${acct}`;
  for (const m of [SYNC_MODEL, DELETE_MODEL]) {
    if (!(await dam.json(request, m))) throw new Error(`workflow model ${m} is not installed`);
  }
  for (const c of [SYNC_STEP, DELETE_STEP]) {
    const comp = await dam.component(request, c);
    if (!comp || comp.state !== 'active') throw new Error(`${c} is ${comp ? comp.state : 'not registered'}`);
  }
});

test.afterAll(async ({ request }) => {
  const errors = await t.cleanup({ cms, request, pub: null });
  expect(errors, 'cleanup left throwaway objects behind (see the warning above)').toEqual([]);
});

async function upload(request, label) {
  const name = `${dam.PREFIX}${label}-${RUN}`;
  const { assetPath, assetState } = await dam.uploadAsset(request, root, `${name}.mp4`);
  t.assets.add(assetPath);
  test.info().annotations.push({ type: 'upload', description: `${assetPath} dam:assetState=${assetState}` });
  return { name, assetPath };
}

async function throwawayVideo(label) {
  const name = `${dam.PREFIX}${label}-${RUN}`;
  const id = await cms.create(name);
  t.videos.add(id);
  return { id, name };
}

test('row 29: the sync-asset workflow pushes a synced asset\'s metadata to its video', async ({ request }) => {
  const v = await throwawayVideo('r29');
  const { assetPath } = await upload(request, 'r29');
  const title = `${v.name}-wf`;
  await dam.setMetadata(request, assetPath, { brc_id: v.id, brc_lastsync: { date: new Date() }, 'dc:title': title });
  expect((await cms.get(v.id)).name).toBe(v.name);

  const wf = await dam.runWorkflow(request, SYNC_MODEL, assetPath);
  expect(wf.status, `workflow ${wf.instance}`).toBe('COMPLETED');

  const after = await dam.pollUntil(async () => {
    const x = await cms.get(v.id);
    return x.name === title ? x : null;
  }, { timeout: 60_000, interval: 3_000 });
  expect(after, `CMS name is still "${(await cms.get(v.id)).name}"`).not.toBeNull();
  // The step then pulled the video back into the asset.
  const m = await dam.metadata(request, assetPath);
  expect(m.brc_id).toBe(v.id);
  expect(m.brc_state).toBe('ACTIVE');
});

test('row 29: the sync-asset workflow on an unsynced asset ingests exactly one new video', async ({ request }) => {
  const { name, assetPath } = await upload(request, 'r29new');
  await dam.setMetadata(request, assetPath, { 'dc:title': name });
  const uuid = (await dam.assetNode(request, assetPath))['jcr:uuid'];
  expect(uuid, 'no jcr:uuid: the BGS-1705 reference_id default would be empty').toBeTruthy();
  expect(await cms.getByRef(uuid)).toBeNull();

  const wf = await dam.runWorkflow(request, SYNC_MODEL, assetPath);
  expect(wf.status, `workflow ${wf.instance}`).toBe('COMPLETED');

  const m = await dam.metadata(request, assetPath);
  expect(m.brc_id, 'the step never wrote brc_id').toBeTruthy();
  t.videos.add(String(m.brc_id));
  const v = await cms.getByRef(uuid);
  expect(v).not.toBeNull();
  expect(String(v.id)).toBe(String(m.brc_id));
  expect(v.name).toBe(name);
});

test('row 30: the delete-asset workflow deletes the throwaway video and then the asset', async ({ request }) => {
  const victim = await throwawayVideo('r30');
  const bystander = await throwawayVideo('r30-bystander'); // must survive
  const { assetPath } = await upload(request, 'r30');
  await dam.setMetadata(request, assetPath, { brc_id: victim.id, brc_lastsync: { date: new Date() } });

  // Guard before anything destructive: the asset points at OUR throwaway and nothing else.
  expect((await dam.metadata(request, assetPath)).brc_id).toBe(victim.id);
  expect((await cms.get(victim.id)).name.startsWith(dam.PREFIX)).toBe(true);

  const wf = await dam.runWorkflow(request, DELETE_MODEL, assetPath);
  expect(wf.status, `workflow ${wf.instance}`).toBe('COMPLETED');

  const gone = await dam.pollUntil(async () => ((await cms.tryGet(victim.id)) === null ? true : null), { timeout: 60_000, interval: 3_000 });
  expect(gone, `video ${victim.id} still exists in Video Cloud`).toBe(true);
  expect(await dam.assetNode(request, assetPath), 'the step removes the DAM asset after a successful CMS delete').toBeNull();
  expect(await cms.tryGet(bystander.id), 'an unrelated video was touched').not.toBeNull();
});
