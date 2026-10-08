// BGS-1600 live coverage: parity-matrix rows 10, 11 and 36.
//   row 10  edit + save video metadata from the admin tool to CMS
//   row 11  poster / thumbnail image URL update
//   row 36  metadata PATCH to CMS on Java 21 (BGS-1600): every CMS PATCH goes
//           through HttpServices.executePatch
//
// Every assertion re-reads the PERSISTED state with an independent CMS GET
// (tests/e2e/cms.js, straight to Video Cloud), never the toast or the response.
//
// Needs a Video Cloud test account whose credentials are in the environment
// (BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID / _CLIENT_SECRET, e.g. `set -a; source
// ~/.brightcove/<label>.env; set +a`) and that is the account the AEM under test
// is configured with. Without them the whole file skips (NOT MEASURED, loudly).
// The spec creates ONE throwaway video named e2e-throwaway-<timestamp> and
// deletes it in afterAll; it never touches any other video.
//
// What the admin tool can and cannot save today (measured on AEM 6.5 LTS, Java 21):
//   - Name / description / tags have NO edit control in the panel. The legacy
//     a=update_video endpoint and its dead ExtJS metaEdit() dialog were deleted.
//   - Labels (Save Labels) and the poster/thumbnail URL are the UI saves that
//     exist. Save Labels is the live UI path through executePatch.
const { test, expect, openAdmin, resolveAccountId, mediaHostReachable } = require('../fixtures');
const { AEM_USER, AEM_PASS } = require('../target');
const { creds, cmsClient } = require('../cms');
const { readConfig, setProxy, restoreConfig, backupConfig } = require('../connector-config');
const { LoggingProxy } = require('../proxy-harness');
const fs = require('fs');

const IMAGE_URL = process.env.BRC_E2E_IMAGE_URL || 'https://httpbin.org/image/jpeg';

test.describe.configure({ mode: 'serial' });

let cms;
let videoId;
let videoName;

test.beforeAll(async ({ request }) => {
  test.skip(!creds(), 'NOT MEASURED: BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set, so no throwaway video can be created');
  cms = await cmsClient();
  const aemAccount = await resolveAccountId(request);
  if (cms.accountId !== aemAccount) {
    throw new Error('the credentials in the environment belong to a different account than the one configured in AEM; refusing to create a video the admin tool cannot see');
  }
  videoName = `e2e-throwaway-${Date.now()}`;
  videoId = await cms.create(videoName);
});

test.afterAll(async () => {
  // Runs on failure too. Only ever deletes the video this file created, and only after the
  // server-side name is re-read (delIfThrowaway), not on the strength of a local variable.
  if (cms && videoId) {
    await cms.delIfThrowaway(videoId);
    videoId = null;
  }
});

// Find the throwaway in the admin list by searching for it and open its panel.
// CMS search is eventually consistent: a video created seconds ago can be absent
// from search for a while, so the search is repeated until the row shows up.
async function openThrowawayPanel(page) {
  await openAdmin(page);
  const row = page.locator('#tbData tr', { hasText: videoName });
  await expect.poll(async () => {
    await page.locator('#search').click();
    await page.locator('#search').fill(videoName);
    await page.locator('#searchBut').click();
    try {
      await row.first().waitFor({ state: 'visible', timeout: 8_000 });
    } catch (e) { /* not indexed yet, search again */ }
    return row.count();
  }, { timeout: 120_000, intervals: [2_000], message: `${videoName} never appeared in the admin video list` }).toBe(1);
  await row.click();
  await expect(page.locator('#tdMeta')).toBeVisible();
  await expect(page.locator('#divMeta\\.id')).toHaveText(videoId);
}

test('the instance JVM is recorded (row 36 is a Java 21 claim)', async ({ request }) => {
  const res = await request.get('/system/console/status-System%20Properties.txt', {
    headers: { Authorization: 'Basic ' + Buffer.from(`${AEM_USER}:${AEM_PASS}`).toString('base64') },
  });
  expect(res.ok(), `system properties HTTP ${res.status()}`).toBeTruthy();
  const m = /^\s*java\.version\s*=\s*(\S+)/m.exec(await res.text());
  expect(m, 'java.version not found in the system properties').not.toBeNull();
  console.log(`[bgs-1600] instance java.version = ${m[1]}`);
  test.info().annotations.push({ type: 'java.version', description: m[1] });
});

// Save Labels under a CMS that cannot be reached: the PATCH happens SERVER-SIDE, so a browser
// route-intercept cannot cause it. The connector's own `proxyServer` is pointed at a logging
// proxy that refuses cms.api.brightcove.com (OAuth stays allowed), so the real PATCH fails.
// The UI must say so; before the fix the servlet answered {} and the UI toasted "Labels saved".
// Runs BEFORE the persistence test so the throwaway still has no labels. The original config
// is restored by value in a finally and verified; a 0600 backup (gitignored run folder) is kept for
// crash recovery and deleted on success.
// Context: ../../parity/probes/proxy/README.md
test('row 10/36: Save Labels shows an error, not "Labels saved", when the CMS PATCH fails', async ({ page }) => {
  expect((await cms.get(videoId)).labels || []).toEqual([]); // nothing to pass vacuously

  await openThrowawayPanel(page);
  await page.waitForFunction(
    () => document.querySelectorAll('#label_list option[value^="/"]').length > 0, null, { timeout: 20_000 });
  const label = await page.locator('#label_list option[value^="/"]').first().getAttribute('value');
  await page.locator('#labelInput').fill(label.replace(/^\/+|\/+$/g, ''));
  await page.locator('#labelInput').press('Enter');
  await expect(page.locator('#divMeta\\.labels .brc-label-pill')).toContainText(label);

  const orig = await readConfig();
  const backup = backupConfig(orig, 'bgs-1600-metadata');
  const proxy = await new LoggingProxy({ deny: [/^cms\.api\.brightcove\.com$/] }).start();
  let restored = false;
  try {
    await setProxy(orig, proxy.address);
    proxy.closeTunnels();
    const mark = proxy.mark();
    await page.locator('#saveLabelsBtn').click();

    await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/not saved/i);
    await expect(page.locator('#brcToast .brc-toast-msg')).not.toHaveText(/^Labels saved$/);
    // The failure came from the deny (OAuth allowed, CMS refused), not from something else.
    expect(proxy.hostsSince(mark, 'deny')).toContain('cms.api.brightcove.com');
    expect(proxy.hostsSince(mark, 'allow')).toContain('oauth.brightcove.com');
    // Independent re-read: nothing persisted.
    expect((await cms.get(videoId)).labels || []).toEqual([]);
  } finally {
    restored = await restoreConfig(orig);
    await proxy.stop();
    if (restored) fs.unlinkSync(backup);
  }
  expect(restored, `connector config was NOT restored; recover from ${backup}`).toBe(true);
});

test('row 10/36: Save Labels in the admin UI persists to CMS through the PATCH path', async ({ page }) => {
  expect((await cms.get(videoId)).labels || []).toEqual([]); // nothing to pass vacuously

  await openThrowawayPanel(page);
  await page.waitForFunction(
    () => document.querySelectorAll('#label_list option[value^="/"]').length > 0, null, { timeout: 20_000 });
  const label = await page.locator('#label_list option[value^="/"]').first().getAttribute('value');
  expect(label).toBeTruthy();

  await page.locator('#labelInput').fill(label.replace(/^\/+|\/+$/g, ''));
  await page.locator('#labelInput').press('Enter');
  await expect(page.locator('#divMeta\\.labels .brc-label-pill')).toContainText(label);
  await page.locator('#saveLabelsBtn').click();
  await expect(page.locator('#brcToast .brc-toast-msg')).toHaveText(/^Labels saved$/);

  // Independent re-read: the persisted video, not the toast.
  const persisted = await cms.get(videoId);
  expect(persisted.labels).toContain(label);
  expect(persisted.name).toBe(videoName); // the PATCH changed labels only
});

// Row 13 / §3b-1 (moved here from bcon-admin-regressions.spec.js, which never writes): removing a
// video's LAST label. jQuery's $.param drops the empty array, so the request carries no `labels`
// parameter; the server must read that as "clear all". Before BGS-1600 it was a silent no-op
// under a "Labels saved" toast, then briefly a 400. Context: ../../../current/docs/admin-labels.md
test('row 13 / §3b-1: removing the last label in the admin UI clears it in CMS', async ({ page }) => {
  await openThrowawayPanel(page);
  await page.waitForFunction(
    () => document.querySelectorAll('#label_list option[value^="/"]').length > 0, null, { timeout: 20_000 });
  if (((await cms.get(videoId)).labels || []).length === 0) {
    // Precondition: exactly the situation the defect needs, a video with a label to remove.
    const label = await page.locator('#label_list option[value^="/"]').first().getAttribute('value');
    await page.locator('#labelInput').fill(label.replace(/^\/+|\/+$/g, ''));
    await page.locator('#labelInput').press('Enter');
    await page.locator('#saveLabelsBtn').click();
    await expect(page.locator('#brcToast .brc-toast-msg')).toHaveText(/^Labels saved$/);
    await expect.poll(async () => ((await cms.get(videoId)).labels || []).length, { timeout: 30_000 }).toBeGreaterThan(0);
    await openThrowawayPanel(page);
  }
  const before = (await cms.get(videoId)).labels || [];
  expect(before.length, 'no label to remove; the check would pass vacuously').toBeGreaterThan(0);
  await expect(page.locator('#divMeta\\.labels .brc-label-pill')).toHaveCount(before.length);

  while ((await page.locator('#divMeta\\.labels .brc-label-pill').count()) > 0) {
    await page.locator('#divMeta\\.labels .brc-label-pill-remove').first().click();
  }
  const sent = page.waitForRequest((r) => r.url().includes('/bin/brightcove/api.js') && r.url().includes('a=update_labels'));
  await page.locator('#saveLabelsBtn').click();
  // The defect's precondition: the request really carries no labels parameter.
  expect(new URL((await sent).url()).searchParams.has('labels'), 'the UI now sends labels; this no longer exercises the absent-parameter path').toBe(false);
  await expect(page.locator('#brcToast .brc-toast-msg')).toHaveText(/^Labels saved$/);

  // Independent re-read: the persisted video, not the toast.
  // The CMS reports a video with no labels as null/absent, the same as the [] the other tests read.
  await expect.poll(async () => (await cms.get(videoId)).labels || [], { timeout: 30_000 }).toEqual([]);
  expect((await cms.get(videoId)).name).toBe(videoName);
});

test('row 11: poster URL saved in the admin UI is ingested and persisted to CMS', async ({ page }) => {
  test.skip(!(await mediaHostReachable(IMAGE_URL)), `NOT MEASURED: ${new URL(IMAGE_URL).host} is unreachable from here (set BRC_E2E_IMAGE_URL to a reachable image)`);
  expect((await cms.get(videoId)).images || {}).not.toHaveProperty('poster');

  await openThrowawayPanel(page);
  const poster = page.locator('.brc-image-widget[data-image-kind="poster"]');
  await poster.locator('.brc-image-url-btn').click();
  await poster.locator('.brc-image-url-input').fill(IMAGE_URL);
  await poster.locator('.brc-image-url-save').click();
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/queued/i);

  // Dynamic Ingest is asynchronous: poll the persisted video until the poster lands.
  await expect.poll(async () => { const i = (await cms.get(videoId)).images || {}; return i.poster && i.poster.src; },
    { timeout: 120_000, intervals: [3_000] }).toMatch(/^https:\/\//);
  const persisted = await cms.get(videoId);
  expect(persisted.images.poster.src).not.toBe(IMAGE_URL); // re-hosted by Brightcove, not echoed back
});

test('row 11: thumbnail URL saved in the admin UI is ingested and persisted to CMS', async ({ page }) => {
  test.skip(!(await mediaHostReachable(IMAGE_URL)), `NOT MEASURED: ${new URL(IMAGE_URL).host} is unreachable from here (set BRC_E2E_IMAGE_URL to a reachable image)`);
  expect((await cms.get(videoId)).images || {}).not.toHaveProperty('thumbnail');

  await openThrowawayPanel(page);
  const thumb = page.locator('.brc-image-widget[data-image-kind="thumbnail"]');
  await thumb.locator('.brc-image-url-btn').click();
  await thumb.locator('.brc-image-url-input').fill(IMAGE_URL);
  await thumb.locator('.brc-image-url-save').click();
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/queued/i);

  await expect.poll(async () => { const i = (await cms.get(videoId)).images || {}; return i.thumbnail && i.thumbnail.src; },
    { timeout: 120_000, intervals: [3_000] }).toMatch(/^https:\/\//);
  const persisted = await cms.get(videoId);
  expect(persisted.images.thumbnail.src).not.toBe(IMAGE_URL); // re-hosted by Brightcove, not echoed back
});
