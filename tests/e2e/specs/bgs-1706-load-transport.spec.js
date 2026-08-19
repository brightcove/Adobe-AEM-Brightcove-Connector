// BGS-1706 — the admin tool's JSONP transport must exist.
//
// The com.iskitz.ajile vendor bundle was removed to clear a static-analysis
// DOM-XSS finding, on the conclusion that it was an unreferenced include. It
// was not: it defined and exported the transport as a global Load(), which
// brcAdmin.js and brcUI.js call on every data path. Removing it shipped an
// admin tool that threw "Load is not defined" and rendered an empty table
// (7.2.2-cloud). brcTransport.js now supplies Load() as first-party code.
//
// This spec guards the dependency explicitly, so a future "remove unused
// vendor JS" pass that deletes the transport fails here by name rather than
// as a confusing empty-table symptom.
const { test, expect, openAdmin } = require('../fixtures');

// Every Load() call resolves to a GET on the api servlet. Waiting on the
// response (rather than polling the row count) is what makes each step below
// prove a *new* fetch happened, instead of passing on rows the previous step
// had already rendered.
function apiResponse(page, marker) {
  return page.waitForResponse(
    (r) => r.url().includes('/bin/brightcove/api.js') && r.url().includes(marker),
    { timeout: 20_000 },
  );
}

test('the Load() transport global is defined', async ({ page }) => {
  await openAdmin(page);
  expect(await page.evaluate(() => typeof window.Load)).toBe('function');
});

test('no "Load is not defined" error on the admin tool', async ({ page }) => {
  const errs = [];
  page.on('pageerror', (e) => errs.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });

  await openAdmin(page);
  await page.waitForTimeout(2000);

  expect(errs.filter((e) => /Load is not defined/.test(e))).toHaveLength(0);
});

test('every Load()-backed data path fetches and renders', async ({ page }) => {
  await openAdmin(page);

  // Initial listing.
  expect(await page.locator('#tbData tr').count()).toBeGreaterThan(0);

  // Server sorts: each is a Load(getAllVideosURLOrdered(...)) round trip. Await
  // the response before the next interaction so the clicks cannot race.
  for (const col of ['name', 'updated_at', 'reference_id']) {
    const res = apiResponse(page, 'a=search_videos');
    await page.locator(`#trHeader th[data-sortBy="${col}"]`).click();
    expect((await res).ok(), `sort by ${col} did not fetch`).toBeTruthy();
    await expect
      .poll(async () => page.locator('#tbData tr').count(), { timeout: 20_000 })
      .toBeGreaterThan(0);
  }

  // Playlists tab: Load(getAllPlaylistsURL()).
  const plRes = apiResponse(page, 'a=search_playlists');
  await page.locator('#allPlaylists').click();
  expect((await plRes).ok(), 'playlists tab did not fetch').toBeTruthy();

  await expect(page.locator('#searchDiv_pl')).toBeVisible();
  await expect
    .poll(async () => page.locator('#tbData tr').count(), { timeout: 20_000 })
    .toBeGreaterThan(0);
});
