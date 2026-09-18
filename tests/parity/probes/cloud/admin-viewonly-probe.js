// Rows 10, 11, 12: view-only checks. These do NOT save/mutate the live
// account — editing video name/description/tags and posting a new
// poster/thumbnail URL are not in the parity task's allowed-mutation list
// (playlist rename, label apply/remove, folder move), so we only confirm
// the UI affordance renders and, where a cancel path exists, that cancel
// leaves state untouched.
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

  async function openFirstVideoPanel(page) {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.locator('#tbData tr').first().click();
    await page.waitForSelector('#tdMeta', { state: 'visible', timeout: 10000 });
    await page.waitForSelector('.brc-panel-inner', { state: 'visible', timeout: 10000 });
  }

  // ---- Row 10: name/description/tags fields present and editable (no save) ----
  await withPage('row10_metadataFieldsPresent', async (page) => {
    await openFirstVideoPanel(page);
    await page.screenshot({ path: path.join(OUT_DIR, 'row10-metadata-panel.png'), fullPage: true });
    const nameField = await page.locator('.brc-panel-info-table').innerText();
    // Editable fields the panel exposes (best-effort discovery, no submit).
    const editableInputs = await page.locator('.brc-panel-inner input[type="text"], .brc-panel-inner textarea').count();
    return { infoTableText: nameField.slice(0, 500), editableInputCount: editableInputs };
  });

  // ---- Row 11: poster/thumbnail inline edit opens, then Cancel (no save) ----
  await withPage('row11_imageUrlInlineEdit', async (page) => {
    await openFirstVideoPanel(page);
    await page.waitForSelector('.brc-image-widget[data-image-kind="poster"]', { state: 'visible' });
    const $poster = page.locator('.brc-image-widget[data-image-kind="poster"]');
    await $poster.locator('.brc-image-url-btn').click();
    const editVisible = await $poster.locator('.brc-image-url-edit').isVisible();
    await page.screenshot({ path: path.join(OUT_DIR, 'row11-image-url-inline-edit.png'), fullPage: true });
    let requestFired = false;
    page.on('request', (r) => { if (r.url().includes('upload_image')) requestFired = true; });
    await $poster.locator('.brc-image-url-cancel').click();
    const backToButton = await $poster.locator('.brc-image-url-btn').isVisible();
    return { editVisible, backToButtonAfterCancel: backToButton, noRequestFiredOnCancel: !requestFired };
  });

  // ---- Row 12: text tracks section lists existing tracks (no upload) ----
  await withPage('row12_textTracksList', async (page) => {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    const rows = page.locator('#tbData tr');
    const n = await rows.count();
    let found = false;
    let trackRowCount = 0;
    for (let i = 0; i < Math.min(n, 30); i++) {
      await rows.nth(i).click();
      await page.waitForSelector('#tdMeta', { state: 'visible' });
      await page.waitForTimeout(400);
      trackRowCount = await page.locator('#textTracksSection:not([hidden]) .brc-track-row').count();
      if (trackRowCount > 0) { found = true; break; }
    }
    if (found) await page.screenshot({ path: path.join(OUT_DIR, 'row12-text-tracks-list.png'), fullPage: true });
    // Also confirm the Upload New Text Track affordance exists (button present, not clicked/submitted).
    const uploadBtnPresent = await page.locator('#trackarea button[onclick^="uploadtrack"]').count();
    return { foundVideoWithTracks: found, trackRowCount, uploadBtnPresent };
  });

  fs.writeFileSync(path.join(OUT_DIR, 'admin-viewonly-results.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
