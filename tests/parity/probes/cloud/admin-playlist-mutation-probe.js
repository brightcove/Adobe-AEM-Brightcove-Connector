// Row 18 — playlists: create/rename/add-remove videos. Only the rename half
// is exercised live (cheap + reversible per the parity task's hard rules;
// creating a new playlist or adding/removing videos would leave persistent
// live objects/edits behind and isn't in the allowed-mutation list). Renames
// one real playlist, verifies via a fresh API re-read (not just the toast),
// then renames it back to its original name and verifies that too.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

async function fetchPlaylistList(page, accountId) {
  const url = `/bin/brightcove/api.js?account_id=${accountId}&a=search_playlists&callback=cb&query=&limit=100&start=0`;
  return page.evaluate(async (u) => {
    const res = await fetch(u, { credentials: 'include' });
    const raw = await res.text();
    let json = null;
    const m = raw.match(/^cb\((.*)\);?\s*$/s);
    try { json = m ? JSON.parse(m[1]) : null; } catch (e) { /* leave null */ }
    return { status: res.status, json };
  }, url);
}

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));

  const result = {};
  try {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    const accountId = await page.locator('#selAccount').inputValue();
    result.accountId = accountId;

    await page.locator('#allPlaylists').click();
    await page.waitForSelector('#tbData tr .edit-playlist', { timeout: 20000 });

    const $btn = page.locator('#tbData tr .edit-playlist').first();
    const originalName = (await $btn.innerText()).trim();
    const playlistId = await $btn.getAttribute('data-playlist-id');
    result.playlistId = playlistId;
    result.originalName = originalName;

    const before = await fetchPlaylistList(page, accountId);
    const beforeEntry = before.json && before.json.items ? before.json.items.find((p) => String(p.id) === String(playlistId)) : null;
    result.nameConfirmedBeforeByApi = beforeEntry ? beforeEntry.name : null;

    const newName = originalName + ' (parity-check)';

    await $btn.click();
    await page.waitForSelector('#editPlaylistModal', { state: 'visible' });
    await page.screenshot({ path: path.join(OUT_DIR, 'row18-edit-modal-open.png'), fullPage: true });
    await page.fill('#epPlaylistName', newName);
    const req1 = page.waitForRequest((r) => r.url().includes('a=update_playlist'), { timeout: 10000 });
    await page.locator('#epUpdate').click();
    const r1 = await req1;
    result.renameRequestUrl = decodeURIComponent(r1.url());
    await page.waitForTimeout(1500);

    const afterRename = await fetchPlaylistList(page, accountId);
    const afterEntry = afterRename.json && afterRename.json.items ? afterRename.json.items.find((p) => String(p.id) === String(playlistId)) : null;
    result.nameAfterRenameByApi = afterEntry ? afterEntry.name : null;
    result.renameConfirmedByApi = afterEntry && afterEntry.name === newName;
    await page.screenshot({ path: path.join(OUT_DIR, 'row18-after-rename.png'), fullPage: true });

    // ---- Rename back ----
    await page.locator('#allPlaylists').click();
    await page.waitForSelector('#tbData tr .edit-playlist', { timeout: 20000 });
    const $btnAgain = page.locator(`#tbData tr .edit-playlist[data-playlist-id="${playlistId}"]`).first();
    await $btnAgain.click();
    await page.waitForSelector('#editPlaylistModal', { state: 'visible' });
    await page.fill('#epPlaylistName', originalName);
    const req2 = page.waitForRequest((r) => r.url().includes('a=update_playlist'), { timeout: 10000 });
    await page.locator('#epUpdate').click();
    const r2 = await req2;
    result.renameBackRequestUrl = decodeURIComponent(r2.url());
    await page.waitForTimeout(1500);

    const afterRestore = await fetchPlaylistList(page, accountId);
    const restoreEntry = afterRestore.json && afterRestore.json.items ? afterRestore.json.items.find((p) => String(p.id) === String(playlistId)) : null;
    result.nameAfterRestoreByApi = restoreEntry ? restoreEntry.name : null;
    result.restoreConfirmedByApi = restoreEntry && restoreEntry.name === originalName;
  } catch (err) {
    result.error = String(err && err.stack || err);
  }

  result.consoleErrors = consoleErrors;
  result.pageErrors = pageErrors;
  fs.writeFileSync(path.join(OUT_DIR, 'row18-playlist-mutation-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
