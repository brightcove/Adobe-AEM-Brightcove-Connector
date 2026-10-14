// Live mutation journey: put a throwaway video into a folder from the admin tool, then take it out.
// Converts parity/probes/cloud/admin-folder-mutation-probe.js into a regression spec.
//
// Every mutation is driven through the real UI from the admin entry route (no route mocks, no
// handler calls) and is asserted TWICE against persisted state: an independent CMS read
// (tests/e2e/cms.js, straight to Video Cloud), then a fresh page load asserting what the user sees.
//
// Needs the Video Cloud credentials of the account the AEM under test is configured with
// (BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID / _CLIENT_SECRET). Without them the file skips as NOT
// MEASURED. It creates one throwaway video and one throwaway folder (e2e-throwaway- prefix) and
// removes both in afterAll; it never touches any other object.
const { test, expect, openAdmin, throwawayName, liveCms, searchVideoRow } = require('../fixtures');

test.describe.configure({ mode: 'serial' });

let cms;
let video;   // { id, name }
let folder;  // { id, name }

test.beforeAll(async ({ request }) => {
  cms = await liveCms(request);
  folder = await cms.createFolder(throwawayName('folder'));
  const name = throwawayName('video');
  video = { id: await cms.create(name), name };
});

test.afterAll(async () => {
  // Runs on failure too. Each delete re-reads the object and checks its name prefix first.
  if (!cms) return;
  if (video) await cms.delIfThrowaway(video.id);
  if (folder) await cms.delFolderIfThrowaway(folder);
});

async function moveVideoIntoFolderViaUi(page) {
  await openAdmin(page);
  const row = await searchVideoRow(page, video.name);
  await row.locator('input[type="checkbox"]').check();
  await page.locator('#bulkMoveToFolder').click();
  const item = page.locator('#mtfFolderList li', { hasText: folder.name });
  await expect(item).toHaveCount(1);
  await item.click();
  const moved = page.waitForResponse((r) => r.url().includes('a=move_video_to_folder'));
  await page.locator('#mtfMove').click();
  expect((await moved).status()).toBe(200);
}

test('move a video into a folder: persisted in the CMS and visible after a reload', async ({ page }) => {
  await moveVideoIntoFolderViaUi(page);

  // 1. Independent CMS reads: the video record and the folder's own listing agree.
  await expect.poll(async () => (await cms.get(video.id)).folder_id, { timeout: 30_000 }).toBe(folder.id);
  expect(await cms.folderVideoIds(folder.id)).toContain(video.id);

  // 2. What the user sees after a refresh: the folder filter lists the video.
  await openAdmin(page);
  await page.locator('#filterToggle').click();
  await page.locator('#fldr_list').selectOption({ label: folder.name });
  await expect(page.locator('#tbData tr', { hasText: video.name })).toBeVisible({ timeout: 30_000 });
});

// DEFECT: there is no way to take a video OUT of a folder in the admin tool. The Move to Folder
// modal lists real folders only; the legacy "[No Folder]" menu (.folder-selector, folder id "none")
// is never shown. The probe had to call remove_video_from_folder directly. Same gap as the pin in
// bcon-admin-regressions.spec.js (§3b item 3). Flip to `test` when a "no folder" choice ships.
test.fixme('remove a video from its folder: persisted in the CMS and visible after a reload', async ({ page }) => {
  await openAdmin(page);
  const row = await searchVideoRow(page, video.name);
  await row.locator('input[type="checkbox"]').check();
  await page.locator('#bulkMoveToFolder').click();
  const none = page.locator('#mtfFolderList [data-folder-id="none"], #mtfFolderList .brc-mtf-list-item--none');
  await expect(none).toHaveCount(1);
  await none.click();
  await page.locator('#mtfMove').click();

  await expect.poll(async () => (await cms.get(video.id)).folder_id || null, { timeout: 30_000 }).toBeNull();
  expect(await cms.folderVideoIds(folder.id)).not.toContain(video.id);

  await openAdmin(page);
  await page.locator('#filterToggle').click();
  await page.locator('#fldr_list').selectOption({ label: folder.name });
  await expect(page.locator('#tbData tr', { hasText: video.name })).toHaveCount(0);
});
