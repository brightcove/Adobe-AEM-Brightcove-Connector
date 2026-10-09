// Rows 23-27 of tests/parity/matrix.md, take 2, using the correct overlay
// interaction pattern discovered via sites-editor-explore.js: the Touch UI
// editor renders an "OverlayWrapper" in the TOP document with droptarget
// divs keyed by data-path sitting above the iframe; click that overlay (not
// the iframe element) to select a component, then button[data-action=
// CONFIGURE] to open its dialog.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');
const PAGE_PATH = '/content/test-site/bgs-test-page';

function writeJSON(name, obj) { fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(obj, null, 2)); }

async function openConfigureFor(page, dataPathSuffix, screenshotName) {
  const overlay = page.locator(`[data-path$="${dataPathSuffix}"]`).first();
  await overlay.scrollIntoViewIfNeeded();
  await overlay.click();
  await page.waitForTimeout(600);
  await page.locator('button[data-action="CONFIGURE"]').first().click();
  await page.waitForTimeout(900);
  await page.screenshot({ path: path.join(OUT_DIR, screenshotName), fullPage: false });
  const dialogVisible = await page.locator('coral-dialog, .cq-dialog, [role="dialog"]').first().isVisible().catch(() => false);
  await page.keyboard.press('Escape').catch(() => {});
  await page.waitForTimeout(400);
  return dialogVisible;
}

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE, viewport: { width: 1400, height: 1000 } });
  const page = await context.newPage();
  let errors = [];
  page.on('pageerror', (err) => errors.push({ type: 'pageerror', message: String(err) }));
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push({ type: 'console.error', message: msg.text() }); });
  function flushErrors(name) { writeJSON(name, errors); errors = []; }

  await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load' });
  await page.waitForTimeout(3500);
  await page.screenshot({ path: path.join(OUT_DIR, 'row23-25-editor-loaded.png'), fullPage: true });
  flushErrors('row23-25-console.json');

  // Row 23: Video Player component (top)
  const row23 = await openConfigureFor(page, 'brightcove_player_top', 'row23-player-dialog.png');
  writeJSON('row23-player.json', { dialogOpened: row23 });

  // Row 24: Playlist Player component
  const row24 = await openConfigureFor(page, 'brightcove_playlist', 'row24-playlist-player-dialog.png');
  writeJSON('row24-playlist-player.json', { dialogOpened: row24 });

  // Row 25: Experiences component
  const row25 = await openConfigureFor(page, 'brightcove_experiences', 'row25-experiences-dialog.png');
  writeJSON('row25-experiences.json', { dialogOpened: row25 });
  flushErrors('row23-25-dialogs-console.json');

  // Row 26: BGS-1690 -- scroll position + marker survival across a
  // component-edit save, using the off-screen "mid" player specifically
  // called out by the fixture page's own description.
  {
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(300);
    await page.evaluate(() => { window.__marker = 'bgs1690-test-marker'; });

    const overlay = page.locator('[data-path$="brightcove_player_mid"]').first();
    await overlay.scrollIntoViewIfNeeded();
    const scrollYBeforeEdit = await page.evaluate(() => window.scrollY);
    await overlay.click();
    await page.waitForTimeout(600);
    await page.locator('button[data-action="CONFIGURE"]').first().click();
    await page.waitForTimeout(900);
    await page.screenshot({ path: path.join(OUT_DIR, 'row26-bgs1690-dialog-open.png'), fullPage: false });

    // Change a field: pick the first available Video option.
    await page.locator('coral-select[name*="video"], [name="./videoId"]').first().click({ timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(500);
    const optionCount = await page.locator('coral-selectlist-item, li[role="option"]').count().catch(() => 0);
    if (optionCount > 0) {
      await page.locator('coral-selectlist-item, li[role="option"]').first().click().catch(() => {});
    }
    await page.waitForTimeout(400);
    await page.screenshot({ path: path.join(OUT_DIR, 'row26-bgs1690-field-changed.png'), fullPage: false });

    // Save (checkmark icon in the dialog header).
    await page.locator('button[title="Done"], coral-dialog-header button, button[icon="check"], .cq-dialog-submit, button:has(coral-icon[icon="check"])').first().click({ timeout: 5000 }).catch(async () => {
      // fallback: click the checkmark glyph seen in the earlier screenshot
      await page.locator('coral-icon[icon="check"]').first().click().catch(() => {});
    });
    await page.waitForTimeout(1500);

    const markerSurvived = await page.evaluate(() => window.__marker).catch(() => undefined);
    const scrollYAfterSave = await page.evaluate(() => window.scrollY).catch(() => null);
    const urlAfterSave = page.url();
    await page.screenshot({ path: path.join(OUT_DIR, 'row26-bgs1690-after-save.png'), fullPage: false });

    writeJSON('row26-bgs1690.json', {
      scrollYBeforeEdit,
      scrollYAfterSave,
      markerSetBeforeEdit: 'bgs1690-test-marker',
      markerAfterSave: markerSurvived,
      markerSurvived: markerSurvived === 'bgs1690-test-marker',
      urlAfterSave,
      note: 'markerSurvived=false (marker undefined, i.e. window was reset) plus scrollY reset toward 0 both indicate a full page reload/navigation on save, which is the expected on-prem 6.0.12 behavior relative to the cloud BGS-1690 fix (partial refresh preserving scroll position). If markerSurvived=true and scrollY is close to scrollYBeforeEdit, that would mean on-prem already does NOT fully reload.'
    });
    flushErrors('row26-console.json');
  }

  // Row 27: Content Finder / Asset Finder Brightcove tab
  {
    await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load' });
    await page.waitForTimeout(3000);
    // Left rail toggle (top-left icon) opens the Sites/Assets/Content-Finder panel.
    await page.locator('button[icon="assetSelector"], button[title="Content Finder"], button[title="Assets"], #SidePanel button, .cq-sidepanel-toggle, coral-icon[icon="viewColumn"]').first().click({ timeout: 5000 }).catch(async () => {
      await page.locator('button').first().click().catch(() => {});
    });
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(OUT_DIR, 'row27-sidepanel-open.png'), fullPage: false });

    const tabTexts = await page.locator('coral-tab, [role="tab"]').allTextContents().catch(() => []);
    const brightcoveTabVisible = tabTexts.some(t => /brightcove/i.test(t));
    let brightcoveTabScreenshot = false;
    if (brightcoveTabVisible) {
      await page.locator('coral-tab:has-text("Brightcove"), [role="tab"]:has-text("Brightcove")').first().click().catch(() => {});
      await page.waitForTimeout(700);
      await page.screenshot({ path: path.join(OUT_DIR, 'row27-brightcove-tab.png'), fullPage: false });
      brightcoveTabScreenshot = true;
    }
    writeJSON('row27-content-finder.json', {
      tabTexts,
      brightcoveTabVisible,
      brightcoveTabScreenshot,
      note: 'The on-prem contentfinder extension (apps/brightcove/extensions/contentfinder/*.js, xtype "contentfindertab") targets the classic/CQ5-era ExtJS Content Finder sidekick widget, not the modern AEM 6.x Touch UI Sites rail. Checked whether the modern rail exposes a Brightcove tab regardless.'
    });
    flushErrors('row27-console.json');
  }

  await context.close();
  await browser.close();
  console.log('sites editor probe v2 complete');
}

main().catch((e) => { console.error(e); process.exit(1); });
