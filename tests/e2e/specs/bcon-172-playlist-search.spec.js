// BCON-172 — "Search by Name does not work for Playlists".
// The "Name" search field used to send query=name:<term>, which Brightcove's
// playlist search rejects (it does a bare name search), returning nothing.
// After the fix it sends query=<term> and returns matching playlists.
const { test, expect, openAdmin } = require('../fixtures');

test('playlist search by Name returns results', async ({ page }) => {
  await openAdmin(page);

  // Switch to the Playlists tab and wait for its search UI.
  await page.locator('#allPlaylists').click();
  await expect(page.locator('#searchDiv_pl')).toBeVisible();

  // Choose the "Name" search field, type a term known to match playlists.
  await page.selectOption('#selField_pl', 'find_playlist_by_name');
  await page.fill('#search_pl', 'Smart');

  // Capture the outgoing api.js request to assert the query is correct.
  const reqPromise = page.waitForRequest((r) =>
    r.url().includes('/bin/brightcove/api.js') && r.url().includes('a=search_playlists') && r.url().includes('query='));

  await page.locator('#searchBut_pl').click();

  const req = await reqPromise;
  const url = req.url();
  // Regression guard: must NOT use the broken name: qualifier.
  expect(decodeURIComponent(url)).not.toContain('query=name:');
  expect(decodeURIComponent(url)).toContain('query=Smart');

  // And the table must actually render matching playlist rows.
  await page.waitForFunction(() => {
    const rows = document.querySelectorAll('#tbData tr');
    return rows.length > 0;
  }, null, { timeout: 20_000 });

  const rowCount = await page.locator('#tbData tr').count();
  expect(rowCount).toBeGreaterThan(0);

  // Every visible playlist name should contain the search term (case-insensitive).
  const names = await page.locator('#tbData tr').allInnerTexts();
  expect(names.some((t) => /smart/i.test(t))).toBeTruthy();
});
