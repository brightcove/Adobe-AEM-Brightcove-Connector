// Row 28 — DAM asset: Brightcove metadata tab on a video asset. View-only.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  const ASSET_PATH = process.argv[5];

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH, viewport: { width: 1400, height: 900 } });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));

  const result = {};
  try {
    const propsUrl = '/mnt/overlay/dam/gui/content/assets/metadataeditor.external.html' + ASSET_PATH;
    await page.goto(propsUrl, { waitUntil: 'networkidle', timeout: 30000 });
    const bcTab = page.locator('coral-tab:has-text("Brightcove")');
    await bcTab.waitFor({ state: 'visible', timeout: 15000 });
    result.tabPresent = true;
    await bcTab.click();
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(OUT_DIR, 'row28-dam-brightcove-tab.png'), fullPage: true });
    const fieldCount = await page.locator('coral-panel.is-selected input, coral-panel.is-selected coral-select, coral-panel.is-selected textarea').count().catch(() => 0);
    result.metadataFieldCount = fieldCount;
  } catch (err) {
    result.error = String(err && err.stack || err);
  }

  result.consoleErrors = consoleErrors;
  result.pageErrors = pageErrors;
  fs.writeFileSync(path.join(OUT_DIR, 'row28-dam-asset-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
