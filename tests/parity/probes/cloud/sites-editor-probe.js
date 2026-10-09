// Rows 23-27 — Sites editor: Video Player / Playlist Player / Experiences
// components render, the BGS-1690 scroll-preservation fix on component edit,
// and the Content Finder / Asset Finder Brightcove tab.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  const EDITOR_PATH = process.argv[5] || '/editor.html/content/test-site/bgs-test-page.html';

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH, viewport: { width: 1400, height: 900 } });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  const playbackCalls = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));
  page.on('response', (r) => { if (r.url().includes('edge.api.brightcove.com/playback')) playbackCalls.push({ url: r.url(), status: r.status() }); });

  const result = {};
  try {
    await page.goto(EDITOR_PATH, { waitUntil: 'networkidle', timeout: 45000 });
    // The editor UI takes a beat to attach edit overlays to components.
    await page.waitForTimeout(3000);

    // AEM's editor content frame id is usually "ContentFrame". Match the
    // INNER frame precisely: the outer editor.html frame's own URL also
    // contains this path as a substring ("/editor.html/content/..."), so a
    // naive .includes() picks the wrong (outer) frame.
    const contentFrame = page.frames().find((f) => f.url().endsWith('/content/test-site/bgs-test-page.html') && !f.url().includes('editor.html'));
    result.contentFrameFound = !!contentFrame;
    result.allFrameUrls = page.frames().map((f) => f.url());

    await page.screenshot({ path: path.join(OUT_DIR, 'row23-27-editor-loaded.png'), fullPage: true });

    if (contentFrame) {
      // ---- Row 23: Video Player component(s) render ----
      // Confirmed DOM shape (live-inspected): .brightcoveplayer wrapper >
      // .brightcove_player > .brightcove-container > video.vjs-tech (video.js
      // mounted and initialized).
      result.row23_playerWrapperCount = await contentFrame.locator('.brightcoveplayer:not(.brightcoveplayer-playlist)').count();
      result.row23_mountedVideoJsCount = await contentFrame.locator('.brightcove-container video.vjs-tech').count();

      // ---- Row 24: Playlist Player component renders ----
      result.row24_playlistWrapperCount = await contentFrame.locator('.brightcoveplayer-playlist').count();
      const playlistHtml = await contentFrame.locator('.brightcoveplayer-playlist').innerHTML().catch(() => '');
      result.row24_playlistHasContent = playlistHtml.length > 500;

      // ---- Row 25: Experiences component renders ----
      result.row25_experienceWrapperCount = await contentFrame.locator('.brightcoveexperiences').count();
      const expHtml = await contentFrame.locator('.brightcoveexperiences').innerHTML().catch(() => '');
      result.row25_experienceHasContent = expHtml.length > 500;

      // Track per-video playback API results to distinguish a connector
      // rendering defect from stale test-fixture video ids.
    }

    // ---- Row 26: BGS-1690 — edit second player, save, scroll preserved ----
    await page.evaluate(() => { window.__marker = 1; });
    // Scroll the editor's own document (the outer editor page hosts the scrollable content in AEMaaCS's editor layout).
    await page.mouse.wheel(0, 1600);
    await page.waitForTimeout(500);
    const scrollYBefore = await page.evaluate(() => window.scrollY);
    result.scrollYBefore = scrollYBefore;
    await page.screenshot({ path: path.join(OUT_DIR, 'row26-scrolled-to-second-player.png'), fullPage: false });

    // Locate the second Brightcove player's edit overlay/toolbar. AEM overlays
    // live in the top document, positioned over the iframe content.
    const editables = page.locator('.cq-Overlay[data-type="Editable"]');
    const overlayCount = await editables.count();
    result.overlayCount = overlayCount;

    // Find an overlay whose data-path includes "brightcove_player_mid".
    const midOverlay = page.locator('.cq-Overlay[data-path*="brightcove_player_mid"]');
    const midOverlayCount = await midOverlay.count();
    result.midOverlayFound = midOverlayCount > 0;

    if (midOverlayCount > 0) {
      await midOverlay.first().scrollIntoViewIfNeeded();
      await midOverlay.first().click();
      await page.waitForTimeout(500);
      // Open the configure/edit dialog via the toolbar "Configure" action.
      const configureBtn = page.locator('coral-actionbar button[title="Configure" i], button[data-action="CONFIGURE" i], .cq-Overlay button[title="Configure" i]').first();
      const configureBtnCount = await configureBtn.count();
      result.configureButtonFound = configureBtnCount > 0;
      if (configureBtnCount > 0) {
        const scrollYPreClick = await page.evaluate(() => window.scrollY);
        await configureBtn.click();
        // Multiple coral-dialogs can exist in the DOM (e.g. an unrelated,
        // hidden "aem-sites-schedule-dialog"); the real config dialog is the
        // one that is actually visible/open.
        const openDialog = page.locator('coral-dialog[open], .cq-dialog:visible').first();
        await openDialog.waitFor({ state: 'visible', timeout: 10000 });
        await page.screenshot({ path: path.join(OUT_DIR, 'row26-configure-dialog-open.png'), fullPage: false });

        // Change a field (any text input) and save.
        const dialogInput = openDialog.locator('input[type="text"]').first();
        const inputCount = await dialogInput.count();
        if (inputCount > 0) {
          const original = await dialogInput.inputValue().catch(() => '');
          await dialogInput.fill(original); // no-op edit is enough to exercise Save without mutating content meaningfully
        }
        const saveBtn = openDialog.locator('button[type="submit"], button:has-text("Done")').first();
        const saveBtnCount = await saveBtn.count();
        result.saveButtonFound = saveBtnCount > 0;
        if (saveBtnCount > 0) {
          await saveBtn.click();
          await page.waitForTimeout(2000);
        }
        const scrollYAfter = await page.evaluate(() => window.scrollY);
        const markerSurvived = await page.evaluate(() => window.__marker === 1);
        result.scrollYPreClick = scrollYPreClick;
        result.scrollYAfterSave = scrollYAfter;
        result.scrollDelta = Math.abs(scrollYAfter - scrollYPreClick);
        result.scrollPreservedWithin50px = result.scrollDelta <= 50;
        result.markerSurvivedNoReload = markerSurvived;
        await page.screenshot({ path: path.join(OUT_DIR, 'row26-after-save.png'), fullPage: false });
      }
    }

    // ---- Row 27: Content Finder / Asset Finder Brightcove tab ----
    // Open the assets rail (usually a left-side toggle in the editor toolbar).
    const assetsRailToggle = page.locator('button[title="Toggle Side Panel" i]').first();
    const railToggleCount = await assetsRailToggle.count();
    result.assetsRailToggleFound = railToggleCount > 0;
    if (railToggleCount > 0) {
      await assetsRailToggle.click();
      await page.waitForTimeout(1500);
      await page.screenshot({ path: path.join(OUT_DIR, 'row27-assets-rail-open.png'), fullPage: false });
      const bcTab = page.locator('[data-foundation-content-panel-tab-tag*="brightcove" i], coral-tab:has-text("Brightcove")');
      const bcTabCount = await bcTab.count();
      result.row27_brightcoveTabFound = bcTabCount > 0;
      if (bcTabCount > 0) {
        await bcTab.first().click();
        await page.waitForTimeout(1000);
        await page.screenshot({ path: path.join(OUT_DIR, 'row27-brightcove-tab.png'), fullPage: false });
      }
    }
  } catch (err) {
    result.error = String(err && err.stack || err);
  }

  result.consoleErrors = consoleErrors;
  result.pageErrors = pageErrors;
  result.playbackCalls = playbackCalls;
  fs.writeFileSync(path.join(OUT_DIR, 'row23-27-sites-editor-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
