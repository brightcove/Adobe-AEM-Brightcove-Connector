// Rows 23-27 of tests/parity/matrix.md -- Sites editor (Touch UI) on-prem
// 6.0.12 probe. Not a Playwright test file -- run with node directly.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');
const PAGE_PATH = '/content/test-site/bgs-test-page';

function writeJSON(name, obj) { fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(obj, null, 2)); }

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE, viewport: { width: 1400, height: 1000 } });
  const page = await context.newPage();
  let errors = [];
  page.on('pageerror', (err) => errors.push({ type: 'pageerror', message: String(err) }));
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push({ type: 'console.error', message: msg.text() }); });
  function flushErrors(name) { writeJSON(name, errors); errors = []; }

  // ---- Row 23-25: open editor, screenshot, open each component dialog ----
  await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load' });
  await page.waitForSelector('iframe.cq-Overlay-EditableToolbar, iframe#ContentFrame, iframe[name="ContentFrame"]', { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(4000);
  await page.screenshot({ path: path.join(OUT_DIR, 'row23-25-editor-loaded.png'), fullPage: true });
  flushErrors('row23-25-console.json');

  const frame = page.frames().find(f => f !== page.mainFrame() && f.url().includes(PAGE_PATH));
  writeJSON('row23-25-frame-info.json', {
    frameFound: !!frame,
    frameUrl: frame ? frame.url() : null,
    allFrameUrls: page.frames().map(f => f.url())
  });

  if (frame) {
    // Player component (row 23)
    const playerEl = await frame.locator('.brightcoveplayer').first();
    await playerEl.click({ timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(500);
    // Try to open its edit dialog via the toolbar "Configure"/pencil action.
    await page.locator('button[data-action="CONFIGURE"], .cq-Overlay-EditableToolbar-icon.CONFIGURE, coral-icon[icon="edit"]').first().click({ timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT_DIR, 'row23-player-dialog.png'), fullPage: true });
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(300);

    // Playlist player (row 24)
    const playlistEl = await frame.locator('.brightcoveplayer-playlist').first();
    const playlistCount = await frame.locator('.brightcoveplayer-playlist').count();
    if (playlistCount > 0) {
      await playlistEl.click({ timeout: 10000 }).catch(() => {});
      await page.waitForTimeout(500);
      await page.locator('button[data-action="CONFIGURE"], .cq-Overlay-EditableToolbar-icon.CONFIGURE, coral-icon[icon="edit"]').first().click({ timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(800);
      await page.screenshot({ path: path.join(OUT_DIR, 'row24-playlist-player-dialog.png'), fullPage: true });
      await page.keyboard.press('Escape').catch(() => {});
      await page.waitForTimeout(300);
    }

    // Experiences component (row 25)
    const expCount = await frame.locator('.brightcoveexperiences').count();
    if (expCount > 0) {
      await frame.locator('.brightcoveexperiences').first().click({ timeout: 10000 }).catch(() => {});
      await page.waitForTimeout(500);
      await page.locator('button[data-action="CONFIGURE"], .cq-Overlay-EditableToolbar-icon.CONFIGURE, coral-icon[icon="edit"]').first().click({ timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(800);
      await page.screenshot({ path: path.join(OUT_DIR, 'row25-experiences-dialog.png'), fullPage: true });
      await page.keyboard.press('Escape').catch(() => {});
    }
    writeJSON('row23-25-component-counts.json', {
      playerCount: await frame.locator('.brightcoveplayer').count(),
      playlistPlayerCount: playlistCount,
      experiencesCount: expCount
    });
    flushErrors('row23-25-dialogs-console.json');
  }

  await context.close();
  await browser.close();
  console.log('sites editor probe (23-25) complete');
}

main().catch((e) => { console.error(e); process.exit(1); });
