// Cloud parity matrix — read-only admin-tool probe (rows 5-9, 16-17, 19-20, 22).
// Committed script: never hardcode the account id, read it from
// /bin/brightcove/accounts at run time.
//
// Usage:
//   NODE_PATH=<bcon-parity>/tests/e2e/node_modules node admin-readonly-probe.js \
//     <AEM_BASE> <state.json path> <output dir>
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH });
  const results = {};

  async function withPage(name, fn) {
    const page = await context.newPage();
    const consoleErrors = [];
    const pageErrors = [];
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
    page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));
    try {
      const r = await fn(page);
      results[name] = { ok: true, data: r || null, consoleErrors, pageErrors };
    } catch (err) {
      results[name] = { ok: false, error: String(err && err.message || err), consoleErrors, pageErrors };
    }
    await page.close();
  }

  async function openAdmin(page) {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
  }

  // ---- Row 5: video search by text ----
  await withPage('row5_search', async (page) => {
    await openAdmin(page);
    const before = await page.locator('#tbData tr').count();
    const firstName = (await page.locator('#tbData tr').first().locator('td').nth(1).textContent() || '').trim();
    const term = firstName.split(/\s+/)[0] || firstName;
    await page.screenshot({ path: path.join(OUT_DIR, 'row05-search-before.png'), fullPage: true });
    await page.fill('#search', term);
    const reqPromise = page.waitForRequest((r) => r.url().includes('/bin/brightcove/api.js') && r.url().includes('a=search_videos'), { timeout: 15000 });
    await page.locator('#searchBut').click();
    const req = await reqPromise;
    await page.waitForTimeout(1500);
    const after = await page.locator('#tbData tr').count();
    await page.screenshot({ path: path.join(OUT_DIR, 'row05-search-after.png'), fullPage: true });
    const names = await page.locator('#tbData tr').allInnerTexts();
    return { before, after, term, requestUrl: req.url(), namesContainTerm: names.some((t) => t.toLowerCase().includes(term.toLowerCase())) };
  });

  // ---- Row 6: sort by name and by date ----
  await withPage('row6_sort', async (page) => {
    await openAdmin(page);
    const readNames = () => page.$$eval('#tbData tr', (rows) => rows.map((r) => {
      const c = r.querySelectorAll('td');
      return c.length >= 2 ? c[1].textContent.trim() : '';
    }).filter(Boolean));
    const initial = await readNames();
    await page.locator('#nameCol').click();
    await page.waitForTimeout(1500);
    const afterNameSort = await readNames();
    await page.screenshot({ path: path.join(OUT_DIR, 'row06-sort-name.png'), fullPage: true });

    await page.locator('#lastUpdated').click();
    await page.waitForTimeout(1500);
    const afterDateSort = await readNames();
    await page.screenshot({ path: path.join(OUT_DIR, 'row06-sort-date.png'), fullPage: true });

    return {
      initial, afterNameSort, afterDateSort,
      nameSortChangedOrder: JSON.stringify(initial) !== JSON.stringify(afterNameSort),
      dateSortChangedOrder: JSON.stringify(afterNameSort) !== JSON.stringify(afterDateSort),
      rowCountStable: initial.length === afterNameSort.length && afterNameSort.length === afterDateSort.length,
    };
  });

  // ---- Row 7: paging ----
  await withPage('row7_paging', async (page) => {
    await openAdmin(page);
    await page.screenshot({ path: path.join(OUT_DIR, 'row07-pagination-panel.png'), fullPage: true });
    const paginationHtml = await page.locator('#pagination').innerHTML().catch(() => null);
    const pageLinks = await page.locator('#pagination a, #pagination button, #pagination li').count();
    let clickedNext = false;
    let firstPageNames = await page.$$eval('#tbData tr', (rows) => rows.map((r) => r.querySelector('td:nth-child(2)')?.textContent.trim()));
    let secondPageNames = null;
    const nextControl = page.locator('#pagination a:has-text("Next"), #pagination a:has-text(">"), #pagination [data-page]:not([data-page="1"])').first();
    if (await nextControl.count() > 0) {
      await nextControl.click().catch(() => {});
      await page.waitForTimeout(1500);
      secondPageNames = await page.$$eval('#tbData tr', (rows) => rows.map((r) => r.querySelector('td:nth-child(2)')?.textContent.trim()));
      clickedNext = true;
      await page.screenshot({ path: path.join(OUT_DIR, 'row07-pagination-page2.png'), fullPage: true });
    }
    return { paginationHtml, pageLinks, clickedNext, firstPageNames, secondPageNames, changed: clickedNext ? JSON.stringify(firstPageNames) !== JSON.stringify(secondPageNames) : null };
  });

  // ---- Row 8: filter panel (folder/label/state) ----
  await withPage('row8_filterPanel', async (page) => {
    await openAdmin(page);
    await page.locator('#filterToggle').click();
    await page.waitForSelector('#filterPanel', { state: 'visible', timeout: 10000 });
    await page.screenshot({ path: path.join(OUT_DIR, 'row08-filter-panel-open.png'), fullPage: true });
    const folderOptions = await page.locator('#fldr_list option').count();
    const labelOptions = await page.locator('#label_list option').count();
    const clipsOptions = await page.locator('#filter_clips option').count().catch(() => 0);
    // Exercise the label filter read-only: pick the first real label option and confirm the list re-renders.
    let labelFilterChanged = null;
    const realLabel = await page.locator('#label_list option[value^="/"]').first().getAttribute('value').catch(() => null);
    if (realLabel) {
      const before = await page.locator('#tbData tr').count();
      await page.selectOption('#label_list', realLabel);
      await page.waitForTimeout(1500);
      const after = await page.locator('#tbData tr').count();
      labelFilterChanged = { before, after, realLabel };
      await page.screenshot({ path: path.join(OUT_DIR, 'row08-filter-by-label.png'), fullPage: true });
      // Reset filter
      await page.locator('#filterClearAll').click().catch(() => {});
    }
    return { folderOptions, labelOptions, clipsOptions, labelFilterChanged };
  });

  // ---- Row 9: video detail panel opens with metadata ----
  await withPage('row9_detailPanel', async (page) => {
    await openAdmin(page);
    await page.locator('#tbData tr').first().click();
    await page.waitForSelector('#tdMeta', { state: 'visible', timeout: 10000 });
    await page.waitForSelector('.brc-panel-inner', { state: 'visible', timeout: 10000 });
    await page.screenshot({ path: path.join(OUT_DIR, 'row09-detail-panel.png'), fullPage: true });
    const infoRows = await page.locator('.brc-panel-info-table tr').count();
    const titleText = await page.locator('.brc-panel-header').innerText().catch(() => null);
    return { infoRows, titleText };
  });

  // ---- Row 16/17: playlists list all + search by name ----
  await withPage('row16_17_playlists', async (page) => {
    await openAdmin(page);
    await page.locator('#allPlaylists').click();
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForSelector('#searchDiv_pl', { state: 'visible' });
    const listCount = await page.locator('#tbData tr').count();
    const hasPagination = await page.locator('#pagination').isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row16-playlists-list.png'), fullPage: true });

    const firstName = (await page.locator('#tbData tr .edit-playlist').first().innerText()).trim();
    const term = firstName.split(/\s+/)[0];
    await page.selectOption('#selField_pl', 'find_playlist_by_name');
    await page.fill('#search_pl', term);
    const reqPromise = page.waitForRequest((r) => r.url().includes('a=search_playlists'));
    await page.locator('#searchBut_pl').click();
    const req = await reqPromise;
    await page.waitForTimeout(1000);
    const resultCount = await page.locator('#tbData tr').count();
    await page.screenshot({ path: path.join(OUT_DIR, 'row17-playlist-search.png'), fullPage: true });
    return { listCount, hasPagination, searchTerm: term, requestUrl: req.url(), resultCount };
  });

  // ---- Row 19: bulk action bar (selection only, no destructive bulk op) ----
  await withPage('row19_bulkBar', async (page) => {
    await openAdmin(page);
    const boxes = page.locator('#tbData input[type="checkbox"]');
    await boxes.nth(0).check();
    await boxes.nth(1).check();
    await page.waitForSelector('#bulkActionBar', { state: 'visible', timeout: 5000 });
    const count = await page.locator('#bulkCount').innerText().catch(() => null);
    await page.screenshot({ path: path.join(OUT_DIR, 'row19-bulk-bar.png'), fullPage: true });
    await page.locator('#checkToggle').check();
    const total = await page.locator('#tbData input[type="checkbox"]').count();
    const checked = await page.locator('#tbData input[type="checkbox"]:checked').count();
    await page.screenshot({ path: path.join(OUT_DIR, 'row19-select-all.png'), fullPage: true });
    await page.locator('#bulkClear').click().catch(() => {});
    return { bulkCountLabel: count, selectAllTotal: total, selectAllChecked: checked };
  });

  // ---- Row 20: account switch trigger + popover (single account) ----
  await withPage('row20_accountSwitch', async (page) => {
    await openAdmin(page);
    await page.locator('#accountTrigger').click();
    await page.waitForSelector('#accountPopover', { state: 'visible', timeout: 5000 });
    await page.screenshot({ path: path.join(OUT_DIR, 'row20-account-popover.png'), fullPage: true });
    const rowCount = await page.locator('.brc-account-row').count();
    return { accountRowCount: rowCount };
  });

  // ---- Row 22: sync overlay ----
  await withPage('row22_syncOverlay', async (page) => {
    await openAdmin(page);
    const overlayPromise = page.waitForSelector('#syncOverlay', { state: 'visible', timeout: 5000 }).catch(() => null);
    await page.locator('#syncdbutton').click();
    const overlay = await overlayPromise;
    let shot = false;
    if (overlay) {
      await page.screenshot({ path: path.join(OUT_DIR, 'row22-sync-overlay.png'), fullPage: true });
      shot = true;
    }
    await page.waitForTimeout(3000);
    const overlayGone = !(await page.locator('#syncOverlay').isVisible().catch(() => false));
    return { overlayAppeared: !!overlay, screenshotTaken: shot, overlayGoneAfter: overlayGone };
  });

  fs.writeFileSync(path.join(OUT_DIR, 'row-console-errors.json'), JSON.stringify(
    Object.fromEntries(Object.entries(results).map(([k, v]) => [k, { consoleErrors: v.consoleErrors, pageErrors: v.pageErrors }]))
    , null, 2));

  fs.writeFileSync(path.join(OUT_DIR, 'admin-readonly-results.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify(Object.fromEntries(Object.entries(results).map(([k, v]) => [k, v.ok])), null, 2));

  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
