const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: 'http://localhost:4602' });
  const page = await context.newPage();
  await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20000 });

  // "Create Playlist" (#newplstButton) turns out to live inside .butDiv,
  // which buildPlaylistList() explicitly hides (span[name=buttonRow].hide());
  // it is reachable only from the Videos view, and only once at least one
  // video checkbox is checked (paging.selectedVideos.length > 0 reveals
  // .butDiv via the brc:checked handler) -- i.e. "create playlist" really
  // means "create playlist from selection", not a blank-form create.
  const row = page.locator('#tbData tr').first();
  await row.click();
  await page.waitForTimeout(300);
  await row.locator('input[type=checkbox]').check();
  await row.click();
  await page.waitForTimeout(300);
  const butDivVisible = await page.locator('.butDiv:visible').first().isVisible().catch(() => false);
  await page.locator('#newplstButton:visible').first().click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT_DIR, 'row18-create-playlist-dialog.png'), fullPage: true });
  await page.locator('#createPlstCancel:visible').first().click().catch(() => {});
  await page.waitForTimeout(300);
  await row.locator('input[type=checkbox]').uncheck().catch(() => {});

  await page.click('#allPlaylists');
  await page.waitForSelector('#tbData tr', { timeout: 20000 });
  await page.waitForTimeout(400);

  await page.locator('.edit-playlist').first().click();
  await page.waitForTimeout(600);
  const dialogHtml = await page.locator('.pml-dialog').first().innerHTML().catch(() => '');
  const hasNameField = /name=["']?(plst\.)?name|Playlist Name|rename/i.test(dialogHtml);
  await page.screenshot({ path: path.join(OUT_DIR, 'row18-edit-playlist-dialog.png'), fullPage: true });
  fs.writeFileSync(path.join(OUT_DIR, 'row18-playlist-crud.json'), JSON.stringify({
    createDialogOpened: true,
    createIsFromSelectionOnly: true,
    butDivVisibleBeforeCreateClick: butDivVisible,
    editDialogOpened: true,
    editDialogHasRenameField: hasNameField,
    note: 'editPlaylistListingCallback() (brcUI.js) only renders a video-search-to-add form plus the current video listing (each removable); its Update handler posts {a: update_playlist, videos, playlistId} with no name field anywhere. No rename UI path exists on-prem 6.0.12. Create Playlist and Edit Playlist dialogs were opened for screenshot evidence only; neither was submitted (create is not on the pre-authorized mutation list, and add/remove-video was left unexercised for the same reason -- only rename+revert, label add+remove, and folder move+revert were pre-approved, and rename has no UI path to exercise here).'
  }, null, 2));
  await context.close();
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
