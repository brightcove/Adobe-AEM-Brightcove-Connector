// Follow-up probe: row 7 (paging via the actual Page Number <select>) and
// row 17 (playlist search with a real term) — the first pass's generic
// selectors/derived term didn't match this tool's actual controls.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];

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

  await withPage('row7_paging', async (page) => {
    await openAdmin(page);
    const firstPageNames = await page.$$eval('#tbData tr', (rows) => rows.map((r) => r.querySelector('td:nth-child(2)')?.textContent.trim()));
    const optionCount = await page.locator('#pagination select[name="selPageN"] option').count();
    await page.screenshot({ path: path.join(OUT_DIR, 'row07-pagination-page1.png'), fullPage: true });
    await page.selectOption('#pagination select[name="selPageN"]', { index: 1 });
    await page.waitForTimeout(1500);
    const secondPageNames = await page.$$eval('#tbData tr', (rows) => rows.map((r) => r.querySelector('td:nth-child(2)')?.textContent.trim()));
    await page.screenshot({ path: path.join(OUT_DIR, 'row07-pagination-page2.png'), fullPage: true });
    return { optionCount, firstPageNames, secondPageNames, changed: JSON.stringify(firstPageNames) !== JSON.stringify(secondPageNames), secondPageCount: secondPageNames.length };
  });

  await withPage('row17_playlistSearch', async (page) => {
    await openAdmin(page);
    await page.locator('#allPlaylists').click();
    await page.waitForSelector('#tbData tr .edit-playlist', { timeout: 20000 });
    const names = await page.locator('#tbData tr .edit-playlist').allInnerTexts();
    // Use a real, distinctive substring from an actual playlist name.
    const full = names[0].trim();
    const term = full.length > 4 ? full.slice(0, 5) : full;
    await page.selectOption('#selField_pl', 'find_playlist_by_name');
    await page.fill('#search_pl', term);
    const reqPromise = page.waitForRequest((r) => r.url().includes('a=search_playlists'));
    await page.locator('#searchBut_pl').click();
    const req = await reqPromise;
    await page.waitForTimeout(1200);
    const resultNames = await page.locator('#tbData tr .edit-playlist').allInnerTexts().catch(() => []);
    await page.screenshot({ path: path.join(OUT_DIR, 'row17-playlist-search.png'), fullPage: true });
    return { allNames: names, term, requestUrl: req.url(), resultCount: resultNames.length, resultNames, matched: resultNames.some((n) => n.toLowerCase().includes(term.toLowerCase())) };
  });

  fs.writeFileSync(path.join(OUT_DIR, 'admin-readonly-results2.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
