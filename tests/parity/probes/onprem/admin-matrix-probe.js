// One-off parity probe for the on-prem 6.0.12 admin tool (rows 5-22 of
// tests/parity/matrix.md), read-only pass. Not a Playwright test file -- run
// directly with node so we can branch freely without a pass/fail gate.
// Reuses the auth state captured by the e2e harness's global-setup.
//
// Run:
//   NODE_PATH="$(pwd)/tests/e2e/node_modules" \
//     node tests/parity/probes/onprem/admin-matrix-probe.js
//
// No account IDs are hardcoded; the account id is read at runtime from
// /bin/brightcove/accounts.

const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');

fs.mkdirSync(OUT_DIR, { recursive: true });

function writeJSON(name, obj) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(obj, null, 2));
}

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE });
  const page = await context.newPage();

  let errors = [];
  page.on('pageerror', (err) => errors.push({ type: 'pageerror', message: String(err) }));
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push({ type: 'console.error', message: msg.text() });
  });
  function flushErrors(name) {
    writeJSON(name, errors);
    errors = [];
  }

  const acctRes = await page.request.get('/bin/brightcove/accounts');
  const acctBody = await acctRes.json();
  const ACCOUNT_ID = acctBody.accounts[0].value;
  console.log('ACCOUNT_ID length', ACCOUNT_ID.length);

  await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20000 });
  await page.waitForTimeout(500);
  flushErrors('row00-baseline-console.json');

  // ---- Row 5: video search by text ----
  {
    const firstName = await page.locator('#tbData tr').first().locator('td').nth(1).innerText();
    const term = firstName.trim().split(/\s+/)[0] || 'TEST';
    await page.fill('#search', term);
    await page.click('#searchDiv #searchBut');
    await page.waitForTimeout(1500);
    const rowCount = await page.locator('#tbData tr').count();
    const headTitle = await page.locator('#headTitle').innerText().catch(() => '');
    await page.screenshot({ path: path.join(OUT_DIR, 'row05-video-search.png'), fullPage: true });
    writeJSON('row05-video-search.json', { term, rowCount, headTitle });
    flushErrors('row05-console.json');
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForTimeout(300);
  }

  // ---- Row 7: paging ----
  {
    const vidsRes = await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_videos&callback=cb&start=0&limit=1&sort=&query=`);
    const vidsText = await vidsRes.text();
    const totalMatch = vidsText.match(/"totals":(\d+)/);
    const total = totalMatch ? parseInt(totalMatch[1], 10) : null;
    const pageDivVisible = await page.locator('div[name=pageDiv]').isVisible().catch(() => false);
    const pageOptions = await page.locator('select[name=selPageN] option').count().catch(() => 0);
    await page.screenshot({ path: path.join(OUT_DIR, 'row07-paging.png'), fullPage: true });
    writeJSON('row07-paging.json', { total, pageDivVisible, pageOptions, pageSize: 30 });
    flushErrors('row07-console.json');
  }

  // ---- Row 8: filter panel (clip checkbox + label/folder selects) ----
  {
    const beforeRows = await page.locator('#tbData tr').count();
    await page.check('#filter_clips');
    await page.waitForTimeout(300);
    const clipVisibleRows = await page.locator('#tbData tr:visible').count();
    await page.screenshot({ path: path.join(OUT_DIR, 'row08-filter-clips.png'), fullPage: true });
    await page.uncheck('#filter_clips');
    await page.waitForTimeout(300);

    const folderOptions = await page.locator('#fldr_list option').allTextContents();
    const labelOptions = await page.locator('#label_list option').allTextContents();
    await page.screenshot({ path: path.join(OUT_DIR, 'row08-filter-selects.png'), fullPage: true });
    writeJSON('row08-filter-panel.json', { beforeRows, clipVisibleRows, folderOptions, labelOptions });
    flushErrors('row08-console.json');
  }

  // ---- Row 9: video detail panel opens with metadata ----
  {
    await page.click('#tbData tr:first-child');
    await page.waitForTimeout(500);
    const name = await page.locator('#divMeta\\.name').innerText().catch(() => '');
    const idTxt = await page.locator('#divMeta\\.id').innerText().catch(() => '');
    const tdMetaVisible = await page.locator('#tdMeta').isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row09-detail-panel.png'), fullPage: true });
    writeJSON('row09-detail-panel.json', { name, id: idTxt, tdMetaVisible });
    flushErrors('row09-console.json');
  }

  // ---- Row 10: edit + save video metadata -- evidence gathering only ----
  // Grep-confirmed (offline, against saved served HTML) that no element in
  // admin.html carries an onclick invoking extMetaEdit()/metaEdit(); the
  // detail panel (#tdMeta) renders only read-only <div> text. We still probe
  // whether the JS function is reachable by direct invocation, to distinguish
  // "removed from code" vs "present but unwired", without ever clicking Send.
  {
    const fnResult = await page.evaluate(() => {
      try {
        if (typeof extMetaEdit === 'function') {
          extMetaEdit();
          return 'extMetaEdit ran';
        }
        return 'extMetaEdit not a function';
      } catch (e) { return 'error: ' + e.message; }
    });
    await page.waitForTimeout(600);
    const dialogVisible = await page.locator('.x-window').first().isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row10-edit-dialog-direct-invoke.png'), fullPage: true });
    writeJSON('row10-edit-dialog.json', {
      fnResult,
      dialogVisibleWhenCalledDirectly: dialogVisible,
      note: 'No onclick/handler in served admin.html invokes extMetaEdit() or metaEdit() anywhere (grep of saved DOM); only reachable this way via direct console call. Send never clicked: no CMS mutation attempted.'
    });
    flushErrors('row10-console.json');
    // best-effort close any ExtJS window opened
    await page.evaluate(() => { try { Ext.WindowMgr.each(w => w.destroy()); } catch (e) {} }).catch(() => {});
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(300);
  }

  // ---- Row 11: poster / thumbnail update dialogs (open only, no submit) ----
  {
    await page.click('#tbData tr:first-child');
    await page.waitForTimeout(400);
    await page.click('a[onclick="uploadPoster()"]');
    await page.waitForTimeout(500);
    const posterDialogVisible = await page.locator('#upload_poster_dialog').isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row11-poster-dialog.png'), fullPage: true });
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(300);

    await page.click('a[onclick="uploadThumbnail()"]');
    await page.waitForTimeout(500);
    const thumbDialogVisible = await page.locator('#upload_thumbnail_dialog').isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row11-thumbnail-dialog.png'), fullPage: true });
    writeJSON('row11-poster-thumbnail.json', { posterDialogVisible, thumbDialogVisible });
    flushErrors('row11-console.json');
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(300);
  }

  // ---- Row 12: text tracks list + upload dialog ----
  {
    // Find a video known (via API) to carry text_tracks, so the list-render
    // path is exercised with real data, not just the empty case.
    const vids = await (await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_videos&callback=cb&start=0&limit=100&sort=&query=`)).text();
    const jsonText = vids.replace(/^cb\(/, '').replace(/\);?$/, '');
    const parsed = JSON.parse(jsonText);
    const withTracks = parsed.items.find(v => v.text_tracks && v.text_tracks.length > 0);
    writeJSON('row12-video-with-tracks.json', { found: !!withTracks, id: withTracks ? withTracks.id : null, trackCount: withTracks ? withTracks.text_tracks.length : 0 });

    if (withTracks) {
      await page.fill('#search', withTracks.name.split(/\s+/)[0]);
      await page.click('#searchDiv #searchBut');
      await page.waitForTimeout(1200);
      await page.click('#tbData tr:first-child');
      await page.waitForTimeout(500);
    }
    const tracksHtml = await page.locator('#divMeta\\.text_tracks').innerHTML().catch(() => '');
    await page.screenshot({ path: path.join(OUT_DIR, 'row12-tracks-list.png'), fullPage: true });

    await page.click('#uploadtrackbutton');
    await page.waitForTimeout(500);
    const uploadDialogVisible = await page.locator('#upload_text_track_dialog').isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row12-upload-dialog.png'), fullPage: true });
    writeJSON('row12-text-tracks.json', { tracksHtmlNonEmpty: tracksHtml.trim().length > 0, uploadDialogVisible });
    flushErrors('row12-console.json');
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(300);
    // reset search
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
  }

  // ---- Row 16: playlists list all (no pagination) ----
  {
    const plRes = await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_playlists&callback=cb&start=0&limit=1&sort=&query=`);
    const plText = await plRes.text();
    const totalMatch = plText.match(/"totals":(\d+)/);
    const apiTotal = totalMatch ? parseInt(totalMatch[1], 10) : null;

    await page.click('#allPlaylists');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForTimeout(500);
    const uiRowCount = await page.locator('#tbData tr').count();
    const paginationVisible = await page.locator('#pagination').isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row16-playlists-all.png'), fullPage: true });
    writeJSON('row16-playlists-list.json', { apiTotal, uiRowCount, paginationVisible });
    flushErrors('row16-console.json');
  }

  // ---- Row 17: playlist search by name ----
  {
    const firstName = await page.locator('#tbData tr').first().locator('td').nth(1).innerText();
    const term = firstName.trim().split(/\s+/)[0];
    await page.fill('#search_pl', term);
    await page.click('#searchDiv_pl #searchBut');
    await page.waitForTimeout(1200);
    const rowCount = await page.locator('#tbData tr').count();
    const headTitle = await page.locator('#headTitle').innerText().catch(() => '');
    await page.screenshot({ path: path.join(OUT_DIR, 'row17-playlist-search.png'), fullPage: true });
    writeJSON('row17-playlist-search.json', { term, rowCount, headTitle });
    flushErrors('row17-console.json');
  }

  // ---- Row 21: upload video (ingest) UI reachability ----
  {
    const hasExtFormUpload = await page.evaluate(() => typeof extFormUpload === 'function');
    // Directly invoke to confirm the dialog CAN render (proves code isn't
    // deleted), while recording it is unreachable from any visible control.
    const invokeResult = await page.evaluate(() => {
      try { extFormUpload(); return 'ran'; } catch (e) { return 'error: ' + e.message; }
    });
    await page.waitForTimeout(500);
    const dialogVisible = await page.locator('.x-window:has-text("Dynamic Ingest")').first().isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row21-ingest-dialog-direct-invoke.png'), fullPage: true });
    writeJSON('row21-ingest.json', {
      hasExtFormUploadFunction: hasExtFormUpload,
      invokeResult,
      dialogVisibleWhenCalledDirectly: dialogVisible,
      note: 'No button/link/onclick anywhere in served admin.html invokes extFormUpload(); grepped full page source, zero matches. Only reachable via direct console call, which is not a real user path.'
    });
    flushErrors('row21-console.json');
    await page.evaluate(() => { try { Ext.WindowMgr.each(w => w.destroy()); } catch (e) {} }).catch(() => {});
    await page.keyboard.press('Escape').catch(() => {});
  }

  // ---- Row 22: sync overlay / loading state ----
  {
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.evaluate(() => { syncStart(); });
    await page.waitForTimeout(150);
    const loadingVisibleDuring = await page.locator('#loading').isVisible().catch(() => false);
    const buttonTextDuring = await page.locator('#syncdbutton').innerText().catch(() => '');
    await page.screenshot({ path: path.join(OUT_DIR, 'row22-sync-loading.png'), fullPage: true });
    await page.evaluate(() => { syncEnd(); });
    await page.waitForTimeout(300);
    const loadingVisibleAfter = await page.locator('#loading').isVisible().catch(() => false);
    writeJSON('row22-sync-loading.json', { loadingVisibleDuring, buttonTextDuring, loadingVisibleAfter, note: 'Exercised via syncStart()/syncEnd() JS calls directly (toggle #loading/.loading and button state); avoided clicking #syncdbutton itself to skip an actual /bin/brightcove/dataload resync round trip, since the CSS/DOM toggle is identical either way.' });
    flushErrors('row22-console.json');
  }

  await context.close();
  await browser.close();
  console.log('read-only pass complete');
}

main().catch((e) => { console.error(e); process.exit(1); });
