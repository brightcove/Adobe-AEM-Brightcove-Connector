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
  const overlay = page.locator('[data-path$="brightcove_player_top"]').first();
  await overlay.click();
  await page.waitForTimeout(600);
  await page.locator('button[data-action="CONFIGURE"]').first().click();
  await page.waitForTimeout(900);

  const info = await page.evaluate(() => {
    const all = Array.from(document.querySelectorAll('coral-icon[icon="check"]'));
    const visible = all.filter(el => el.offsetParent !== null);
    return {
      totalCount: all.length,
      visibleCount: visible.length,
      visibleAncestors: visible.map(check => {
        let node = check;
        const chain = [];
        for (let i = 0; i < 4 && node; i++) {
          chain.push({ tag: node.tagName, cls: node.className, text: (node.textContent || '').slice(0, 60) });
          node = node.parentElement;
        }
        return chain;
      })
    };
  });
  fs.writeFileSync(path.join(OUT_DIR, 'row26-check-icon-ancestors.json'), JSON.stringify(info, null, 2));
  console.log(JSON.stringify(info, null, 2));
  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
