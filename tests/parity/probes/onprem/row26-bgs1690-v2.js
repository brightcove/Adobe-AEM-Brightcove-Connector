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

  // Find the actual scrollable container: whichever element's scrollHeight
  // exceeds its clientHeight by the most (top document is usually NOT it in
  // the Touch UI editor -- content scrolls inside a wrapper div).
  const scrollInfo = await page.evaluate(() => {
    const candidates = Array.from(document.querySelectorAll('*')).filter(el => {
      const s = getComputedStyle(el);
      return (s.overflowY === 'auto' || s.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 50;
    });
    return candidates.slice(0, 10).map(el => ({
      tag: el.tagName, id: el.id, cls: el.className, scrollHeight: el.scrollHeight, clientHeight: el.clientHeight
    }));
  });
  fs.writeFileSync(path.join(OUT_DIR, 'row26-scroll-container-candidates.json'), JSON.stringify(scrollInfo, null, 2));
  console.log('scroll candidates', JSON.stringify(scrollInfo));

  // Set marker, scroll the winning container (or window as fallback), open
  // dialog for the off-screen mid player, inspect the Video coral-select DOM.
  await page.evaluate(() => { window.__marker = 'bgs1690-test-marker'; });
  const overlay = page.locator('[data-path$="brightcove_player_mid"]').first();
  await overlay.scrollIntoViewIfNeeded();
  await page.waitForTimeout(300);

  const scrollState = await page.evaluate((cands) => {
    const results = { windowScrollY: window.scrollY };
    cands.forEach((c, i) => {
      const el = c.id ? document.getElementById(c.id) : document.getElementsByClassName(c.cls.split(' ')[0])[0];
      if (el) results['candidate' + i + '_scrollTop'] = el.scrollTop;
    });
    return results;
  }, scrollInfo);
  console.log('scrollState after scrollIntoView', JSON.stringify(scrollState));

  await overlay.click();
  await page.waitForTimeout(600);
  await page.locator('button[data-action="CONFIGURE"]').first().click();
  await page.waitForTimeout(900);

  // Inspect the Video field DOM structure.
  const videoFieldHtml = await page.locator('coral-dialog, .cq-dialog, [role="dialog"]').first()
    .locator('text=Video:').first().locator('xpath=following-sibling::*[1]').evaluate(el => el.outerHTML).catch(e => 'error: ' + e.message);
  fs.writeFileSync(path.join(OUT_DIR, 'row26-video-field-html.txt'), videoFieldHtml);
  console.log('video field html snippet', videoFieldHtml.slice(0, 400));

  await page.screenshot({ path: path.join(OUT_DIR, 'row26-dialog-open-v2.png'), fullPage: false });
  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
