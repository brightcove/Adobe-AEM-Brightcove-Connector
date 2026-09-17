// Rows 23-27 (on-prem 7.3.0 pass) — Sites editor: Video Player / Playlist
// Player / Experiences components, BGS-1690 scroll preservation, Content
// Finder Brightcove tab.
//
// Why this isn't just ../cloud/sites-editor-probe.js re-pointed at :4602:
// that probe assumes the AEMaaCS SDK author's editor layout, where the outer
// editor.html document itself scrolls (window.scrollY) and the dialog Save
// control is `button[type=submit]`/`button:has-text("Done")`. This AEM 6.5.0
// Touch UI shell scrolls a DIFFERENT container (`#ContentScrollView`;
// window.scrollY stays 0 always) and the dialog's Done control is an icon
// button (`button.cq-dialog-submit`, title="Done" but no text content) in
// the dialog HEADER, not a footer submit button — so both of those checks
// silently no-op'd against this shell. This shell-specific fix was already
// discovered and proven for the on-prem 6.0.12 pass against this SAME AEM
// 6.5.0 shell (../onprem/row26-bgs1690-v4.js) — reused verbatim here since
// it's a shell property, not a connector-version property. Also, this
// instance's test-page player components (`brightcove_player_top`/`_mid`)
// have no video actually picked (bare/placeholder nodes — see NOTES.md),
// unlike cloud's fixture which had real videos pre-selected, so rows 23-25
// are measured the same way the on-prem 6.0.12 pass measured them: the
// component places and its Configure dialog renders real Account/Video
// affordances, not an end-to-end video.js mount+playback check.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4602';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  const EDITOR_PATH = process.argv[5] || '/editor.html/content/test-site/bgs-test-page.html';
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH, viewport: { width: 1400, height: 900 } });
  const result = {};

  // ---- Rows 23-25: component placement + render ----
  {
    const page = await context.newPage();
    const consoleErrors = []; const pageErrors = [];
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
    page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));
    await page.goto(EDITOR_PATH, { waitUntil: 'networkidle', timeout: 45000 });
    await page.waitForTimeout(3000);
    const contentFrame = page.frames().find((f) => f.url().endsWith('/content/test-site/bgs-test-page.html') && !f.url().includes('editor.html'));
    result.contentFrameFound = !!contentFrame;
    await page.screenshot({ path: path.join(OUT_DIR, 'row23-27-editor-loaded.png'), fullPage: true });

    if (contentFrame) {
      result.row23_playerWrapperCount = await contentFrame.locator('.brightcoveplayer:not(.brightcoveplayer-playlist)').count();
      result.row24_playlistWrapperCount = await contentFrame.locator('.brightcoveplayer-playlist').count();
      const playlistHtml = await contentFrame.locator('.brightcoveplayer-playlist').innerHTML().catch(() => '');
      result.row24_playlistHasContent = playlistHtml.length > 500;
      result.row25_experienceWrapperCount = await contentFrame.locator('.brightcoveexperiences').count();
      const expHtml = await contentFrame.locator('.brightcoveexperiences').innerHTML().catch(() => '');
      result.row25_experienceHasContent = expHtml.length > 500;
    }

    // Row 23: open Configure on the placeholder "top" player, confirm real
    // Account/Video affordances render (view-only, Cancel to close).
    const topOverlay = page.locator('.cq-Overlay[data-path*="brightcove_player_top"]');
    result.row23_topOverlayFound = (await topOverlay.count()) > 0;
    if (result.row23_topOverlayFound) {
      await topOverlay.first().scrollIntoViewIfNeeded();
      await topOverlay.first().click();
      await page.waitForTimeout(500);
      const configureBtn = page.locator('button[data-action="CONFIGURE" i]').first();
      result.row23_configureButtonFound = (await configureBtn.count()) > 0;
      if (result.row23_configureButtonFound) {
        await configureBtn.click();
        await page.waitForTimeout(1200);
        const dialog = page.locator('coral-dialog[open], .cq-dialog:visible').first();
        await dialog.waitFor({ state: 'visible', timeout: 10000 });
        await page.screenshot({ path: path.join(OUT_DIR, 'row23-player-dialog.png'), fullPage: false });
        const accountButtonText = await dialog.locator('button', { hasText: /\[\d+\]/ }).first().textContent().catch(() => null);
        const selectButtonPresent = await dialog.locator('button', { hasText: 'Select' }).count();
        result.row23_accountPrePopulated = accountButtonText;
        result.row23_videoSelectButtonPresent = selectButtonPresent > 0;
        await page.locator('button.cq-dialog-cancel:visible').first().click({ timeout: 5000 }).catch(() => {});
        await page.waitForTimeout(500);
      }
    }

    result.consoleErrors_rows23_25 = consoleErrors;
    result.pageErrors_rows23_25 = pageErrors;
    await page.close();
  }

  // ---- Row 26: BGS-1690 scroll-preservation, this shell's real scroll container ----
  {
    const page = await context.newPage();
    await page.goto(EDITOR_PATH, { waitUntil: 'load', timeout: 45000 });
    await page.waitForTimeout(3000);

    const frame = page.frames().find((f) => f !== page.mainFrame() && f.url().includes('/content/test-site/bgs-test-page'));
    await page.evaluate(() => { window.__marker = 'top-marker'; });
    let frameIdentityBefore = null;
    if (frame) {
      await frame.evaluate(() => { window.__marker = 'frame-marker'; window.__frameIdentity = Math.random().toString(36); });
      frameIdentityBefore = await frame.evaluate(() => window.__frameIdentity).catch(() => null);
    }

    const hasScrollContainer = await page.evaluate(() => !!document.getElementById('ContentScrollView'));
    result.row26_hasContentScrollView = hasScrollContainer;
    if (hasScrollContainer) {
      await page.evaluate(() => { document.getElementById('ContentScrollView').scrollTop = 350; });
    } else {
      await page.mouse.wheel(0, 1600);
    }
    await page.waitForTimeout(300);
    const scrollTopBefore = hasScrollContainer
      ? await page.evaluate(() => document.getElementById('ContentScrollView').scrollTop)
      : await page.evaluate(() => window.scrollY);
    result.row26_scrollBefore = scrollTopBefore;
    await page.screenshot({ path: path.join(OUT_DIR, 'row26-scrolled-350.png'), fullPage: false });

    const overlay = page.locator('[data-path$="brightcove_player_mid"]').first();
    const overlayCount = await overlay.count();
    result.row26_midOverlayFound = overlayCount > 0;
    if (overlayCount > 0) {
      await overlay.click();
      await page.waitForTimeout(600);
      await page.locator('button[data-action="CONFIGURE" i]').first().click();
      await page.waitForTimeout(900);
      await page.screenshot({ path: path.join(OUT_DIR, 'row26-configure-dialog-open.png'), fullPage: false });

      const doneClicked = await page.locator('button.cq-dialog-submit:visible').first().click({ timeout: 5000 }).then(() => 'clicked').catch((e) => 'error: ' + e.message);
      result.row26_doneClicked = doneClicked;
      await page.waitForTimeout(2000);
      await page.screenshot({ path: path.join(OUT_DIR, 'row26-after-save.png'), fullPage: false });

      const scrollTopAfter = hasScrollContainer
        ? await page.evaluate(() => { const el = document.getElementById('ContentScrollView'); return el ? el.scrollTop : 'container gone'; })
        : await page.evaluate(() => window.scrollY);
      const topMarkerAfter = await page.evaluate(() => window.__marker).catch(() => 'error');
      const frameIsDetached = frame ? frame.isDetached() : null;
      let frameMarkerAfter = 'n/a (no frame handle)';
      let frameIdentityAfter = 'n/a (no frame handle)';
      if (frame && !frameIsDetached) {
        frameMarkerAfter = await frame.evaluate(() => window.__marker).catch((e) => 'threw: ' + e.message);
        frameIdentityAfter = await frame.evaluate(() => window.__frameIdentity).catch((e) => 'threw: ' + e.message);
      }
      result.row26_scrollAfter = scrollTopAfter;
      result.row26_scrollPreserved = typeof scrollTopAfter === 'number' && Math.abs(scrollTopAfter - scrollTopBefore) < 50;
      result.row26_topMarkerAfter = topMarkerAfter;
      result.row26_topShellReloaded = topMarkerAfter !== 'top-marker';
      result.row26_frameIsDetached = frameIsDetached;
      result.row26_frameIdentityBefore = frameIdentityBefore;
      result.row26_frameIdentityAfter = frameIdentityAfter;
      result.row26_frameMarkerAfter = frameMarkerAfter;
      result.row26_note = 'frameIsDetached===true on the ORIGINAL frame handle means the ContentFrame iframe navigated to a fresh document (a REFRESH_PAGE-style reload), matching the mechanism BGS-1690 fixed on cloud; scrollPreserved checks the outer #ContentScrollView position (the shell the user actually sees) across the save.';
    }
    await page.close();
  }

  // ---- Row 27: Content Finder / Asset Finder Brightcove tab (fresh page load, no leftover dialog) ----
  {
    const page = await context.newPage();
    await page.goto(EDITOR_PATH, { waitUntil: 'networkidle', timeout: 45000 });
    await page.waitForTimeout(3000);
    const assetsRailToggle = page.locator('button[title="Toggle Side Panel" i]').first();
    const railToggleCount = await assetsRailToggle.count();
    result.row27_assetsRailToggleFound = railToggleCount > 0;
    if (railToggleCount > 0) {
      await assetsRailToggle.click({ force: true });
      await page.waitForTimeout(1500);
      await page.screenshot({ path: path.join(OUT_DIR, 'row27-assets-rail-open.png'), fullPage: false });
      const bcTab = page.locator('[data-foundation-content-panel-tab-tag*="brightcove" i], coral-tab:has-text("Brightcove")');
      const bcTabCount = await bcTab.count();
      result.row27_brightcoveTabFound = bcTabCount > 0;
      // Record every visible tab label for reference regardless.
      result.row27_allTabLabels = await page.locator('coral-tab').allInnerTexts().catch(() => []);
      if (bcTabCount > 0) {
        await bcTab.first().click();
        await page.waitForTimeout(1000);
        await page.screenshot({ path: path.join(OUT_DIR, 'row27-brightcove-tab.png'), fullPage: false });
      }
    }
    await page.close();
  }

  fs.writeFileSync(path.join(OUT_DIR, 'row23-27-sites-editor-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
