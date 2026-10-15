// Live mutation journey: create, rename, add a video, remove a video and delete a playlist from the
// admin tool. Converts parity/probes/onprem/row18-playlist-crud.js (which only opened the dialogs)
// and parity/probes/cloud/admin-playlist-mutation-probe.js (rename only) into one regression spec.
//
// Every mutation is driven through the real UI from the admin entry route (no route mocks, no
// handler calls) and is asserted TWICE against persisted state: an independent CMS read
// (tests/e2e/cms.js, straight to Video Cloud), then a fresh page load asserting what the user sees.
//
// Needs the Video Cloud credentials of the account the AEM under test is configured with
// (BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID / _CLIENT_SECRET). Without them the file skips as NOT
// MEASURED. It creates two throwaway videos and one throwaway playlist (e2e-throwaway- prefix) and
// removes them in afterAll; it never touches any other object.
const { test, expect, openAdmin: openAdminFast, throwawayName, expectNoThrowawaysLeft, liveCms, searchVideoRow, searchPlaylistRow } = require('../fixtures');

// The admin page and its first list load slowly when the AEM is busy; a 20 s default is too tight here.
const openAdmin = (page) => openAdminFast(page, { timeout: 60_000 });

test.describe.configure({ mode: 'serial' });

let cms;
let videoA;  // { id, name }, the video the playlist is created from
let videoB;  // { id, name }, added and removed in the edit modal
let playlistName;
let playlistId;
const extraVideos = [];    // ids created by the DEFECT test, removed in afterAll
const extraPlaylists = [];

test.beforeAll(async ({ request }) => {
  cms = await liveCms(request);
  const a = throwawayName('plvideo-a');
  const b = throwawayName('plvideo-b');
  videoA = { id: await cms.create(a), name: a };
  videoB = { id: await cms.create(b), name: b };
  playlistName = throwawayName('playlist');
});

test.afterAll(async () => {
  // Runs on failure too. The playlist id is re-found by name when a failed step never recorded it.
  if (!cms) return;
  const ids = new Set([playlistId]);
  for (const n of [playlistName, `${playlistName}-renamed`]) {
    const p = n && await cms.findPlaylistByName(n);
    if (p) ids.add(p.id);
  }
  for (const id of [...ids, ...extraPlaylists]) await cms.delPlaylistIfThrowaway(id);
  for (const id of [videoA && videoA.id, videoB && videoB.id, ...extraVideos]) if (id) await cms.delIfThrowaway(id);
  await expectNoThrowawaysLeft(cms);
});

const videoIdsOf = async () => ((await cms.tryGetPlaylist(playlistId)) || {}).video_ids || [];

// Opens the Edit Playlist modal for `name` and waits until it is safe to act on: the response of the
// request that populates the video list has arrived and the rendered rows match the playlist's
// videos in the CMS (when its id is known). A user edits what they see; saving before the list
// arrives sends an empty list (clearVideos=true, see the DEFECT test below).
async function openEditModal(page, name, { timeout = 90_000 } = {}) {
  const end = Date.now() + timeout;
  for (;;) {
    await openAdmin(page);
    const row = await searchPlaylistRow(page, name);
    const listed = page.waitForResponse((r) => r.url().includes('a=list_videos_in_playlist'));
    await row.locator('.edit-playlist').click();
    await expect(page.locator('#editPlaylistModal')).toBeVisible();
    expect((await listed).status()).toBe(200);
    if (!playlistId) return;
    const want = (await videoIdsOf()).length;
    // ⚠️ The list endpoint can answer with fewer videos than the CMS holds for a while after a
    // change (measured under load: an empty list for a playlist the CMS reported with videos).
    // That is index lag, not a UI fault, so reload and reopen until the rows catch up.
    try { await expect(page.locator('#epPlaylistItems li')).toHaveCount(want, { timeout: 8_000 }); return; } catch (e) {
      if (Date.now() > end) throw new Error(`the modal rows never matched the ${want} video(s) the CMS holds for the playlist (${timeout} ms): ${e.message.split('\n')[0]}`);
    }
  }
}

test('create a playlist from a selected video', async ({ page }) => {
  test.setTimeout(150_000);
  await openAdmin(page);
  const row = await searchVideoRow(page, videoA.name);
  await row.locator('input[type="checkbox"]').check();
  await page.locator('#bulkCreatePlaylist').click();
  await expect(page.locator('#createPlaylistModal')).toBeVisible();
  await expect(page.locator('#cpVideoTableBody tr', { hasText: videoA.name })).toHaveCount(1);
  await page.locator('#cpTitle_input').fill(playlistName);
  const created = page.waitForResponse((r) => r.url().includes('a=create_playlist'));
  await page.locator('#cpSubmitBtn').click();
  expect((await created).status()).toBe(200);

  // 1. CMS: the playlist exists with exactly the selected video.
  await expect.poll(async () => !!(await cms.findPlaylistByName(playlistName)), { timeout: 30_000 }).toBe(true);
  const p = await cms.findPlaylistByName(playlistName);
  playlistId = p.id;
  expect(p.video_ids.map(String)).toEqual([videoA.id]);

  // 2. After a refresh the Playlists tab lists it.
  await openAdmin(page);
  await expect(await searchPlaylistRow(page, playlistName)).toBeVisible();
});

test('rename the playlist', async ({ page }) => {
  test.setTimeout(150_000);
  expect(playlistId, 'the create step did not record a playlist').toBeTruthy();
  const renamed = `${playlistName}-renamed`;
  await openEditModal(page, playlistName);
  await expect(page.locator('#epPlaylistName')).toHaveValue(playlistName);
  await expect(page.locator('#epPlaylistItems li', { hasText: videoA.name })).toHaveCount(1);
  await page.locator('#epPlaylistName').fill(renamed);
  const saved = page.waitForResponse((r) => r.url().includes('a=update_playlist'));
  await page.locator('#epUpdate').click();
  expect((await saved).status()).toBe(200);

  await expect.poll(async () => (await cms.tryGetPlaylist(playlistId)).name, { timeout: 30_000 }).toBe(renamed);
  expect(await videoIdsOf(), 'a rename must not drop the playlist videos').toEqual([videoA.id]);

  await openAdmin(page);
  await expect(await searchPlaylistRow(page, renamed)).toBeVisible();
  await expect(page.locator('#tbData tr', { hasText: `${playlistName}` }).filter({ hasNotText: 'renamed' })).toHaveCount(0);
  playlistName = renamed;
});

test('add a video to the playlist', async ({ page }) => {
  test.setTimeout(300_000); // search retry (120 s) plus two modal opens
  expect(playlistId, 'the create step did not record a playlist').toBeTruthy();
  await openEditModal(page, playlistName);
  // The search is debounced (750 ms) and CMS search is eventually consistent: retype until the
  // new video is offered.
  const result = page.locator('#epSearchResults .brc-ep-search-result-item', { hasText: videoB.name });
  const end = Date.now() + 120_000;
  for (;;) {
    await page.locator('#epVideoSearch').fill('');
    await page.locator('#epVideoSearch').fill(videoB.name);
    try { await result.first().waitFor({ state: 'visible', timeout: 5_000 }); break; } catch (e) {
      if (Date.now() > end) throw new Error(`video "${videoB.name}" never offered by the playlist video search`);
    }
  }
  await result.first().click();
  await expect(page.locator('#epPlaylistItems li', { hasText: videoB.name })).toHaveCount(1);
  const saved = page.waitForResponse((r) => r.url().includes('a=update_playlist'));
  await page.locator('#epUpdate').click();
  expect((await saved).status()).toBe(200);

  await expect.poll(async () => (await videoIdsOf()).map(String).sort(), { timeout: 30_000 }).toEqual([videoA.id, videoB.id].sort());

  await openEditModal(page, playlistName);
  await expect(page.locator('#epPlaylistItems li', { hasText: videoA.name })).toHaveCount(1);
  await expect(page.locator('#epPlaylistItems li', { hasText: videoB.name })).toHaveCount(1);
});

test('remove a video from the playlist', async ({ page }) => {
  test.setTimeout(150_000);
  expect(playlistId, 'the create step did not record a playlist').toBeTruthy();
  await openEditModal(page, playlistName);
  const item = page.locator('#epPlaylistItems li', { hasText: videoA.name });
  await expect(item).toHaveCount(1);
  // ⚠️ One click currently sends TWO update_playlist requests (see the DEFECT test below) and the
  // concurrent second one can answer 500 first, so gate on any 200, then on persisted state.
  const statuses = [];
  page.on('response', (r) => { if (r.url().includes('a=update_playlist')) statuses.push(r.status()); });
  await item.locator('.brc-ep-item-delete').click(); // saves immediately, no Update click
  await expect.poll(() => statuses.includes(200), { timeout: 30_000 }).toBe(true);

  await expect.poll(async () => (await videoIdsOf()).map(String), { timeout: 30_000 }).toEqual([videoB.id]);

  await openEditModal(page, playlistName);
  await expect(page.locator('#epPlaylistItems li')).toHaveCount(1);
  await expect(page.locator('#epPlaylistItems li', { hasText: videoB.name })).toHaveCount(1);
});

// DEFECT: removing a video from the Edit Playlist modal sends two identical update_playlist requests
// per click, because brcUI.js registers the `.brc-ep-item-delete` click handler twice (the
// "Click delete on a playlist item" block exists at two places). The two PATCHes race at Video Cloud
// and one of them answers HTTP 500 (measured under load: 500 and 200 logged in the same second for
// the same URL). Flip to `test` once the duplicate handler is removed.
test.fixme('removing a video sends exactly one update_playlist request', async ({ page }) => {
  const a = throwawayName('plvideo-c');
  const b = throwawayName('plvideo-d');
  const ids = [await cms.create(a), await cms.create(b)];
  extraVideos.push(...ids);
  const pl = await cms.createPlaylist(throwawayName('playlist-dup'), ids);
  extraPlaylists.push(pl.id);
  await openAdmin(page);
  const row = await searchPlaylistRow(page, pl.name);
  const listed = page.waitForResponse((r) => r.url().includes('a=list_videos_in_playlist'));
  await row.locator('.edit-playlist').click();
  expect((await listed).status()).toBe(200);
  await expect(page.locator('#epPlaylistItems li')).toHaveCount(2);
  const statuses = [];
  page.on('response', (r) => { if (r.url().includes('a=update_playlist')) statuses.push(r.status()); });
  await page.locator('#epPlaylistItems li', { hasText: a }).locator('.brc-ep-item-delete').click();
  await expect.poll(() => statuses.length, { timeout: 30_000 }).toBeGreaterThan(0);
  await page.waitForTimeout(3_000); // let a duplicate request land
  expect(statuses).toEqual([200]);
});

test('delete the playlist', async ({ page }) => {
  test.setTimeout(150_000);
  expect(playlistId, 'the create step did not record a playlist').toBeTruthy();
  await openAdmin(page);
  const row = await searchPlaylistRow(page, playlistName);
  await row.locator('input[type="checkbox"]').check();
  await page.locator('#bulkDeletePlaylists').click();
  await expect(page.locator('.pml-dialog')).toBeVisible();
  await expect(page.locator('.pml-dialog_content')).toContainText(playlistName);
  const deleted = page.waitForResponse((r) => r.url().includes('a=delete_playlist'));
  await page.locator('.pml-dialog .btn-primary').click();
  expect((await deleted).status()).toBe(200);

  await expect.poll(async () => await cms.tryGetPlaylist(playlistId), { timeout: 30_000 }).toBeNull();

  await openAdmin(page);
  await page.locator('#allPlaylists').click();
  await page.waitForSelector('#tbData tr', { timeout: 20_000 });
  await page.locator('#search_pl').fill(playlistName);
  await page.locator('#searchBut_pl').click();
  await expect(page.locator('#tbData tr', { hasText: playlistName })).toHaveCount(0, { timeout: 20_000 });
});
