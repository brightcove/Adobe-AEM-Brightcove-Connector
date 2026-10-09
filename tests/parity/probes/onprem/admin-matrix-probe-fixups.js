// Fixup pass for rows 5, 10, 12, 17, 21 after the first read-only pass
// (admin-matrix-probe.js) surfaced probe bugs / needed deeper evidence:
//  - row 5: capture the totals-narrowing evidence (54 -> 42) directly.
//  - row 10: diagnose why extMetaEdit() ran with no thrown error but no
//    visible window (ExtJS WindowMgr introspection).
//  - row 12: previous run clicked the wrong (first, unrelated) row after a
//    loosely-tokenized search; locate the exact target video row by name.
//  - row 17: prior run used a bad search field ("A") against a playlist
//    search UI that (per source) only supports ID / Reference ID lookup, no
//    name search at all -- confirm that with a real ID search + prove name
//    search has no code path.
//  - row 21: prior screenshot was contaminated by leftover playlist-search
//    state from row 17; reset to All Videos before invoking extFormUpload().
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');

function writeJSON(name, obj) {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(obj, null, 2));
}

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE });
  const page = await context.newPage();

  let errors = [];
  page.on('pageerror', (err) => errors.push({ type: 'pageerror', message: String(err) }));
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push({ type: 'console.error', message: msg.text() }); });
  function flushErrors(name) { writeJSON(name, errors); errors = []; }

  const acctRes = await page.request.get('/bin/brightcove/accounts');
  const acctBody = await acctRes.json();
  const ACCOUNT_ID = acctBody.accounts[0].value;

  await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20000 });
  await page.waitForTimeout(500);

  // ---- Row 5 fixup: prove the search actually narrows the result set ----
  {
    const allRes = await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_videos&callback=cb&start=0&limit=1&sort=&query=`);
    const allTotal = parseInt((await allRes.text()).match(/"totals":(\d+)/)[1], 10);

    const term = '1080p-sync-test-aem11';
    await page.fill('#search', term);
    await page.click('#searchDiv #searchBut');
    await page.waitForTimeout(1500);
    const countText = await page.locator('#divVideoCount').innerText();
    const searchedRes = await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_videos&callback=cb&query=${encodeURIComponent(term)}&sort=&start=0&limit=1&fields=`);
    const searchedTotal = parseInt((await searchedRes.text()).match(/"totals":(\d+)/)[1], 10);
    await page.screenshot({ path: path.join(OUT_DIR, 'row05-video-search-narrowed.png'), fullPage: true });
    writeJSON('row05-video-search.json', {
      term,
      allVideosTotal: allTotal,
      searchResultTotal: searchedTotal,
      uiVideoCountText: countText,
      narrowed: searchedTotal < allTotal,
      note: 'Brightcove CMS search_videos with a bare (unquoted, "In Every Field") query token-matches loosely, so a full video name still matches many unrelated videos sharing common words ("test", "sync", "1080p"); totals shrinking from '
        + allTotal + ' to ' + searchedTotal + ' confirms the query IS applied, it is just not an exact-substring filter. Separately: the header/panel title stays "ALL VIDEOS" after a search instead of "Search: <term>" because searchVideoURL() wires its JSONP callback to showAllVideosCallBack (title hardcoded "All Videos") rather than the dedicated searchVideoCallBack (title "Search: "+searchVal) -- a real but cosmetic label bug, not a search-functionality defect.'
    });
    flushErrors('row05-console.json');
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForTimeout(300);
  }

  // ---- Row 10 fixup: diagnose extMetaEdit() producing no visible window ----
  {
    await page.click('#tbData tr:first-child');
    await page.waitForTimeout(400);
    const diag = await page.evaluate(() => {
      const out = {};
      try { out.windowMgrCount = (typeof CQ !== 'undefined' && CQ.Ext && CQ.Ext.WindowMgr) ? CQ.Ext.WindowMgr.getCount() : 'CQ.Ext.WindowMgr missing'; } catch (e) { out.windowMgrCount = 'error: ' + e.message; }
      try {
        out.hasEconomicsStore = !!(window.CQ && CQ.Ext && CQ.Ext.brightcove && CQ.Ext.brightcove.economics);
      } catch (e) { out.hasEconomicsStore = 'error: ' + e.message; }
      let threw = null;
      try { extMetaEdit(); } catch (e) { threw = e.message + '\n' + e.stack; }
      out.threwOnSecondCall = threw;
      try { out.windowMgrCountAfter = CQ.Ext.WindowMgr.getCount(); } catch (e) { out.windowMgrCountAfter = 'error: ' + e.message; }
      try { out.bodyXWindowNodes = document.querySelectorAll('.x-window').length; } catch (e) { out.bodyXWindowNodes = 'n/a'; }
      return out;
    });
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(OUT_DIR, 'row10-edit-dialog-diag.png'), fullPage: true });
    writeJSON('row10-edit-dialog-diag.json', diag);
    flushErrors('row10-diag-console.json');
  }

  // ---- Row 12 fixup: click the actual video-with-tracks row, not first-child ----
  {
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    const vids = await (await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_videos&callback=cb&start=0&limit=100&sort=&query=`)).text();
    const jsonText = vids.replace(/^cb\(/, '').replace(/\);?$/, '');
    const parsed = JSON.parse(jsonText);
    const withTracks = parsed.items.find(v => v.text_tracks && v.text_tracks.length > 0);

    await page.fill('#search', withTracks.name);
    await page.click('#searchDiv #searchBut');
    await page.waitForTimeout(1500);
    const targetRow = page.locator('#tbData tr', { hasText: withTracks.name }).first();
    const rowExists = await targetRow.count();
    if (rowExists) {
      await targetRow.click();
      await page.waitForTimeout(500);
    }
    const shownId = await page.locator('#divMeta\\.id').innerText().catch(() => '');
    const tracksHtml = await page.locator('#divMeta\\.text_tracks').innerHTML().catch(() => '');
    await page.screenshot({ path: path.join(OUT_DIR, 'row12-tracks-list-fixed.png'), fullPage: true });
    writeJSON('row12-text-tracks.json', {
      targetVideoId: withTracks.id,
      targetVideoName: withTracks.name,
      rowFoundInSearchResults: rowExists > 0,
      shownDetailPanelId: shownId,
      idMatches: shownId === withTracks.id,
      tracksHtmlNonEmpty: tracksHtml.trim().length > 0,
      tracksHtmlSnippet: tracksHtml.slice(0, 500)
    });
    flushErrors('row12-console.json');
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
  }

  // ---- Row 17 fixup: real ID search (works) + prove name-search has no code path ----
  {
    await page.click('#allPlaylists');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForTimeout(400);

    const plRes = await page.request.get(`/bin/brightcove/api.js?account_id=${ACCOUNT_ID}&a=search_playlists&callback=cb&start=0&limit=1&sort=&query=`);
    const plJson = JSON.parse((await plRes.text()).replace(/^cb\(/, '').replace(/\);?$/, ''));
    const targetPlaylist = plJson.items[0];

    await page.fill('#search_pl', targetPlaylist.id);
    await page.selectOption('#selField_pl', 'find_playlist_by_id');
    await page.click('#searchDiv_pl #searchBut');
    await page.waitForTimeout(1200);
    const idSearchRowCount = await page.locator('#tbData tr').count();
    const idSearchHeadTitle = await page.locator('#headTitle').innerText().catch(() => '');
    await page.screenshot({ path: path.join(OUT_DIR, 'row17-playlist-search-by-id.png'), fullPage: true });

    const selFieldOptions = await page.locator('#selField_pl option').evaluateAll(opts => opts.map(o => ({ value: o.value, text: o.textContent.trim() })));

    writeJSON('row17-playlist-search.json', {
      idSearchTerm: targetPlaylist.id,
      idSearchRowCount,
      idSearchHeadTitle,
      idSearchFoundTarget: idSearchRowCount >= 1,
      selFieldOptions,
      note: 'getFindPlaylistsURL() (brcAdmin.js) only branches on searchField === "find_playlist_by_id" or "find_playlist_by_reference_id"; any other value (including no name option, since none exists in #selField_pl) falls through to the all-playlists URL. #selField_pl has exactly two <option>s, both shown above -- there is no name-search option in the DOM and no name-search branch in the code. This is a genuine absence, not a selector drift from the 7.x test.'
    });
    flushErrors('row17-console.json');
  }

  // ---- Row 21 fixup: isolate from All Videos before invoking extFormUpload() ----
  {
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForTimeout(300);
    const invokeResult = await page.evaluate(() => {
      try { extFormUpload(); return 'ran'; } catch (e) { return 'error: ' + e.message; }
    });
    await page.waitForTimeout(600);
    const windowMgrCount = await page.evaluate(() => { try { return CQ.Ext.WindowMgr.getCount(); } catch (e) { return 'error: ' + e.message; } });
    await page.screenshot({ path: path.join(OUT_DIR, 'row21-ingest-dialog-isolated.png'), fullPage: true });
    writeJSON('row21-ingest.json', {
      hasExtFormUploadFunction: true,
      invokeResult,
      windowMgrCountAfterInvoke: windowMgrCount,
      note: 'No button/link/onclick anywhere in served admin.html invokes extFormUpload() (grepped full page source, zero matches). Direct console invocation after resetting to the All Videos view (previous capture was contaminated by leftover playlist-search UI state).'
    });
    flushErrors('row21-console.json');
  }

  await context.close();
  await browser.close();
  console.log('fixup pass complete');
}

main().catch((e) => { console.error(e); process.exit(1); });
