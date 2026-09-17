const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');
const PAGE_PATH = '/content/test-site/bgs-test-page';

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE, viewport: { width: 1400, height: 900 } });
  const page = await context.newPage();
  await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);

  await page.evaluate(() => { window.__marker = 'bgs1690-test-marker'; });
  const frame = page.frames().find(f => f !== page.mainFrame() && f.url().includes(PAGE_PATH));
  const scrollYBefore_top = await page.evaluate(() => window.scrollY);
  const scrollYBefore_frame = await frame.evaluate(() => window.scrollY);
  const scrollTopBefore_container = await page.evaluate(() => document.getElementById('ContentScrollView') ? document.getElementById('ContentScrollView').scrollTop : null);

  const overlay = page.locator('[data-path$="brightcove_player_mid"]').first();
  await overlay.scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);

  const scrollYAfterScrollTo_top = await page.evaluate(() => window.scrollY);
  const scrollYAfterScrollTo_frame = await frame.evaluate(() => window.scrollY);
  const scrollTopAfter_container = await page.evaluate(() => document.getElementById('ContentScrollView') ? document.getElementById('ContentScrollView').scrollTop : null);

  await overlay.click();
  await page.waitForTimeout(600);
  await page.locator('button[data-action="CONFIGURE"]').first().click();
  await page.waitForTimeout(900);

  // Dump the DOM of the Video field for a real selector.
  const html = await page.evaluate(() => {
    const dlg = document.querySelector('coral-dialog, .cq-dialog, [role="dialog"]');
    return dlg ? dlg.outerHTML.slice(0, 6000) : 'no dialog found';
  });
  fs.writeFileSync(path.join(OUT_DIR, 'row26-dialog-dom.html'), html);

  fs.writeFileSync(path.join(OUT_DIR, 'row26-scroll-diagnostics.json'), JSON.stringify({
    scrollYBefore_top, scrollYBefore_frame, scrollTopBefore_container,
    scrollYAfterScrollTo_top, scrollYAfterScrollTo_frame, scrollTopAfter_container
  }, null, 2));

  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
