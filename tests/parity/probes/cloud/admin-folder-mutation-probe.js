// Row 15 — folders: list, move video to folder (and back).
// "Move to folder" is UI-driven (bulk-select -> Move to Folder -> modal ->
// pick a real folder). Moving a video BACK to "All Videos" has no reachable
// UI control in this build: the modal (#moveToFolderModal/#mtfFolderList)
// only lists real folders (confirmed live — no "[No Folder]" entry renders),
// even though stale markup for a `.folder-selector` menu with a
// `data-folder-id="none"` / "[No Folder]" option still exists in admin.html
// and its click handler still exists in brcUI.js — that DOM element is
// simply never shown by the current modal-based flow. To restore the live
// account to its pre-test state (per the parity task's "cheap and reversible,
// done once" rule) the "back" half is done via the underlying
// `remove_video_from_folder` connector API action directly (the same action
// dispatched by BrcApi.java for that legacy menu), since no UI reaches it.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

async function fetchVideoById(page, accountId, videoId) {
  const url = `/bin/brightcove/api.json?isID=true&account_id=${accountId}&a=search_videos&query=${videoId}`;
  return page.evaluate(async (u) => {
    const res = await fetch(u, { credentials: 'include' });
    const raw = await res.text();
    let json = null;
    try { json = JSON.parse(raw); } catch (e) { /* leave null */ }
    return { status: res.status, json };
  }, url);
}

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  const VIDEO_ID = process.argv[5]; // pre-selected, currently unfoldered video

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));

  const result = { videoId: VIDEO_ID };
  try {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    const accountId = await page.locator('#selAccount').inputValue();
    result.accountId = accountId;

    const before = await fetchVideoById(page, accountId, VIDEO_ID);
    result.folderBefore = before.json && before.json.items && before.json.items[0] ? before.json.items[0].folder_id || null : undefined;

    // Row 15a: list folders (filter panel dropdown).
    await page.locator('#filterToggle').click();
    await page.waitForSelector('#filterPanel', { state: 'visible' });
    const folderListOptions = await page.locator('#fldr_list option').allTextContents();
    result.folderListOptions = folderListOptions;
    await page.screenshot({ path: path.join(OUT_DIR, 'row15-folder-list.png'), fullPage: true });
    await page.locator('#filterToggle').click(); // close panel

    // Row 15b: move the video INTO a folder via the real UI flow.
    const row = page.locator(`#tbData tr:has(td:has-text("${VIDEO_ID}"))`).first();
    // Fall back: find by scanning if the ID isn't in a visible column.
    const rowCount = await page.locator('#tbData tr').count();
    let targetIndex = -1;
    for (let i = 0; i < rowCount; i++) {
      await page.locator('#tbData tr').nth(i).click();
      await page.waitForSelector('#tdMeta', { state: 'visible' });
      await page.waitForTimeout(150);
      const id = await page.locator('.brc-panel-info-table').evaluate(() => {
        const rows = document.querySelectorAll('.brc-panel-info-table tr');
        for (const r of rows) { const tds = r.querySelectorAll('td'); if (tds.length >= 2 && /video id/i.test(tds[0].textContent)) return tds[1].textContent.trim(); }
        return null;
      });
      if (id === VIDEO_ID) { targetIndex = i; break; }
    }
    result.targetIndex = targetIndex;
    if (targetIndex === -1) throw new Error('target video not found in current list');

    await page.locator('#tbData input[type="checkbox"]').nth(targetIndex).check();
    await page.waitForSelector('#bulkActionBar', { state: 'visible' });
    await page.locator('#bulkMoveToFolder').click();
    await page.waitForSelector('#mtfFolderList li', { timeout: 8000 });
    const folderItems = await page.locator('#mtfFolderList li').allInnerTexts();
    result.modalFolderOptions = folderItems;
    result.modalHasNoFolderOption = folderItems.some((t) => /no folder/i.test(t));
    await page.screenshot({ path: path.join(OUT_DIR, 'row15-move-modal-open.png'), fullPage: true });

    const targetFolder = page.locator('#mtfFolderList li').filter({ hasText: 'aem_test_folder' }).first();
    await targetFolder.click();
    const moveReq = page.waitForRequest((r) => r.url().includes('a=move_video_to_folder'), { timeout: 10000 });
    await page.locator('#mtfMove').click();
    const req = await moveReq;
    result.moveRequestUrl = decodeURIComponent(req.url());
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(OUT_DIR, 'row15-after-move.png'), fullPage: true });

    const afterMove = await fetchVideoById(page, accountId, VIDEO_ID);
    result.folderAfterMove = afterMove.json && afterMove.json.items && afterMove.json.items[0] ? afterMove.json.items[0].folder_id || null : undefined;
    result.moveConfirmedByApi = !!result.folderAfterMove && result.folderAfterMove !== result.folderBefore;

    // Row 15c: move BACK to All Videos. No UI control reaches this in the
    // current build (see file header) — use the underlying connector API
    // action directly to restore the live account's pre-test state.
    const removeUrl = `/bin/brightcove/api.js?a=remove_video_from_folder&folder=${result.folderAfterMove}&video=${VIDEO_ID}&account_id=${accountId}&callback=cb`;
    const removeResp = await page.evaluate(async (u) => {
      const res = await fetch(u, { credentials: 'include' });
      return { status: res.status, body: await res.text() };
    }, removeUrl);
    result.removeFromFolderResponse = removeResp;
    await page.waitForTimeout(1000);

    const afterBack = await fetchVideoById(page, accountId, VIDEO_ID);
    result.folderAfterMoveBack = afterBack.json && afterBack.json.items && afterBack.json.items[0] ? afterBack.json.items[0].folder_id || null : undefined;
    result.movedBackConfirmedByApi = !result.folderAfterMoveBack;
  } catch (err) {
    result.error = String(err && err.stack || err);
  }

  result.consoleErrors = consoleErrors;
  result.pageErrors = pageErrors;
  fs.writeFileSync(path.join(OUT_DIR, 'row15-folder-mutation-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
