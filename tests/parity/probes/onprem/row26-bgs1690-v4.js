const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');
const PAGE_PATH = '/content/test-site/bgs-test-page';

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE, viewport: { width: 1400, height: 700 } });
  const page = await context.newPage();
  await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);

  const frame = page.frames().find(f => f !== page.mainFrame() && f.url().includes(PAGE_PATH));
  // Mark BOTH the top document and the iframe's own window, and give the
  // iframe document a synthetic identity token, so we can tell apart
  // "iframe navigated to a fresh document" from "outer shell reloaded".
  await page.evaluate(() => { window.__marker = 'top-marker'; });
  await frame.evaluate(() => { window.__marker = 'frame-marker'; window.__frameIdentity = Math.random().toString(36); });
  const frameIdentityBefore = await frame.evaluate(() => window.__frameIdentity);

  // Force a real, substantial scroll on the outer content container.
  await page.evaluate(() => { document.getElementById('ContentScrollView').scrollTop = 350; });
  await page.waitForTimeout(300);
  const scrollTopBefore = await page.evaluate(() => document.getElementById('ContentScrollView').scrollTop);
  await page.screenshot({ path: path.join(OUT_DIR, 'row26-scrolled-350.png'), fullPage: false });

  const overlay = page.locator('[data-path$="brightcove_player_mid"]').first();
  await overlay.click();
  await page.waitForTimeout(600);
  await page.locator('button[data-action="CONFIGURE"]').first().click();
  await page.waitForTimeout(900);
  await page.screenshot({ path: path.join(OUT_DIR, 'row26-dialog-open-scrolled.png'), fullPage: false });

  // Save without changing any field -- the visible checkmark is
  // button.cq-dialog-submit inside coral-dialog-header (confirmed via DOM
  // inspection: there are 2 other icon="check" elements in the page but they
  // belong to a hidden editor-StyleSelector popover).
  const doneClicked = await page.locator('button.cq-dialog-submit:visible').first().click({ timeout: 5000 }).then(() => 'clicked').catch(e => 'error: ' + e.message);
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(OUT_DIR, 'row26-after-save-v4.png'), fullPage: false });

  const scrollTopAfter = await page.evaluate(() => {
    const el = document.getElementById('ContentScrollView');
    return el ? el.scrollTop : 'container gone';
  });
  const topMarkerAfter = await page.evaluate(() => window.__marker).catch(() => 'error');
  // The definitive check: did THIS SPECIFIC frame object get detached (torn
  // down and replaced by a new document), rather than inferring it from a
  // token that could itself throw/undefined its way to a false positive.
  const frameIsDetached = frame.isDetached();
  let frameMarkerAfter = 'n/a (frame detached)';
  let frameIdentityAfter = 'n/a (frame detached)';
  if (!frameIsDetached) {
    frameMarkerAfter = await frame.evaluate(() => window.__marker).catch(e => 'threw: ' + e.message);
    frameIdentityAfter = await frame.evaluate(() => window.__frameIdentity).catch(e => 'threw: ' + e.message);
  }
  const framesNow = page.frames().map(f => f.url());

  fs.writeFileSync(path.join(OUT_DIR, 'row26-bgs1690.json'), JSON.stringify({
    doneClicked,
    scrollTopBefore,
    scrollTopAfter,
    scrollPreserved: typeof scrollTopAfter === 'number' && Math.abs(scrollTopAfter - scrollTopBefore) < 20,
    topMarkerAfter,
    topShellReloaded: topMarkerAfter !== 'top-marker',
    frameIsDetached,
    frameIdentityBefore,
    frameIdentityAfter,
    frameMarkerAfter,
    framesNow,
    note: 'frameIsDetached is the definitive signal: Playwright tears down and replaces a Frame object when its iframe navigates to a new document, so isDetached()===true on the ORIGINAL frame handle means the ContentFrame reloaded (a REFRESH_PAGE-style cq:editConfig, the mechanism BGS-1690 fixed on cloud) rather than an in-place AJAX component refresh. topShellReloaded checks whether the outer editor.html document itself navigated (expected false regardless). scrollPreserved checks the outer scroll container position across the save.'
  }, null, 2));

  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
