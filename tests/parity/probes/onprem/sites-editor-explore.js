const path = require('path');
const { chromium } = require('playwright');
const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');
const PAGE_PATH = '/content/test-site/bgs-test-page';

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE, viewport: { width: 1400, height: 1000 } });
  const page = await context.newPage();
  await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load' });
  await page.waitForTimeout(4000);
  const frame = page.frames().find(f => f !== page.mainFrame() && f.url().includes(PAGE_PATH));
  // The Touch UI editor renders an "OverlayWrapper" in the TOP document with
  // droptarget divs keyed by data-path, sitting above the iframe content and
  // intercepting clicks -- click that overlay div, not the iframe element.
  const overlay = page.locator('[data-path$="brightcove_player_top"]').first();
  await overlay.scrollIntoViewIfNeeded();
  await overlay.click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT_DIR, 'explore-01-after-click.png'), fullPage: false });
  await page.locator('button[data-action="CONFIGURE"]').first().click();
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT_DIR, 'explore-02-configure-dialog.png'), fullPage: false });

  // list any visible toolbar/overlay icons in the top document
  const icons = await page.locator('coral-icon, [is="coral-icon"], .cq-Overlay-EditableToolbar button, button').evaluateAll(nodes =>
    nodes.filter(n => n.offsetParent !== null).slice(0, 60).map(n => ({
      tag: n.tagName, icon: n.getAttribute('icon'), title: n.getAttribute('title'), cls: n.className, dataAction: n.getAttribute('data-action')
    }))
  );
  require('fs').writeFileSync(path.join(OUT_DIR, 'explore-visible-icons.json'), JSON.stringify(icons, null, 2));

  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
