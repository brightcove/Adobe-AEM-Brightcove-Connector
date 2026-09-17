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

  const toolbarButtons = await page.locator('header button, .editor-GlobalBar button, .editor-GlobalBar coral-icon').evaluateAll(nodes =>
    nodes.map(n => ({ tag: n.tagName, title: n.getAttribute('title'), icon: n.getAttribute('icon'), cls: n.className }))
  );
  fs.writeFileSync(path.join(OUT_DIR, 'row27-toolbar-buttons.json'), JSON.stringify(toolbarButtons, null, 2));

  await page.locator('button.toggle-sidepanel').first().click({ timeout: 5000 });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT_DIR, 'row27-after-icon1.png'), fullPage: false });

  const tabTexts1 = await page.locator('coral-tab, [role="tab"]').allTextContents().catch(() => []);
  const brightcoveTabVisible = tabTexts1.some(t => /brightcove/i.test(t));
  fs.writeFileSync(path.join(OUT_DIR, 'row27-tabtexts-icon1.json'), JSON.stringify({ tabTexts1, brightcoveTabVisible }, null, 2));

  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
