// Row 14 — language variants: view/create/edit, on the DAM asset metadata
// editor's Brightcove tab. View-only: opens the Add Variant dialog to confirm
// it renders correctly (per BCON-176/177), but never submits — creating or
// editing a variant would mutate the live video and isn't in the parity
// task's allowed-mutation list.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  const ASSET_PATH = process.argv[5]; // e.g. /content/dam/brightcove_assets/<account>/<id>.mp4

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));

  const result = {};
  try {
    const editorUrl = '/mnt/overlay/dam/gui/content/assets/metadataeditor.external.html' + ASSET_PATH;
    await page.goto(editorUrl, { waitUntil: 'networkidle', timeout: 30000 });
    const bcTab = page.locator('coral-tab:has-text("Brightcove")');
    await bcTab.waitFor({ state: 'visible', timeout: 15000 });
    result.tabPresent = true;
    await bcTab.click();
    await page.waitForTimeout(800);
    result.tabInvalidAfterClick = await bcTab.evaluate((el) => el.classList.contains('is-invalid'));
    await page.screenshot({ path: path.join(OUT_DIR, 'row14-brightcove-tab.png'), fullPage: true });

    const existingVariants = await page.locator('.brc-variant-row, .brc-variant-list li, [data-variant-language]').count().catch(() => 0);
    result.existingVariantElementsFound = existingVariants;

    const addBtn = page.locator('.brc-add-variant-btn').first();
    const addBtnCount = await addBtn.count();
    result.addVariantButtonPresent = addBtnCount > 0;
    if (addBtnCount > 0) {
      await addBtn.click();
      await page.locator('#brc-variant-language').waitFor({ state: 'visible', timeout: 8000 });
      result.dialogOpened = true;
      await page.screenshot({ path: path.join(OUT_DIR, 'row14-variant-dialog-open.png'), fullPage: true });
      // View required-field markers only, don't submit.
      const nameRequired = await page.locator('label[for="brc-variant-name"] span').textContent().catch(() => null);
      result.nameAsteriskText = nameRequired;
    }
  } catch (err) {
    result.error = String(err && err.stack || err);
  }

  result.consoleErrors = consoleErrors;
  result.pageErrors = pageErrors;
  fs.writeFileSync(path.join(OUT_DIR, 'row14-variant-view-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
