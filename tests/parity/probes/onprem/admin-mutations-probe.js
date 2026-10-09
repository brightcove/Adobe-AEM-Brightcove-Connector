// The three pre-authorized, cheap, reversible mutations for the on-prem
// parity probe (matrix rows 13, 15, 18), each done once and undone once, with
// persisted state re-read from /bin/brightcove/api.js after every change (not
// just the UI). No account IDs hardcoded; read at runtime.
//
// Row 18 (playlist rename) turned out to have NO reachable UI path at all --
// see the code-inspection note baked into the row18 section below -- so no
// rename mutation is attempted; only screenshots of the Create/Edit dialogs
// (unsubmitted) are captured for that row.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const BASE = 'http://localhost:4602';
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');

function writeJSON(name, obj) { fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(obj, null, 2)); }

async function getVideo(page, acct, id) {
  const res = await page.request.get(`/bin/brightcove/api.js?account_id=${acct}&a=search_videos&callback=cb&start=0&limit=100&sort=&query=`);
  const parsed = JSON.parse((await res.text()).replace(/^cb\(/, '').replace(/\);?$/, ''));
  return parsed.items.find(v => v.id === id);
}

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE });
  const page = await context.newPage();

  let errors = [];
  page.on('pageerror', (err) => errors.push({ type: 'pageerror', message: String(err) }));
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push({ type: 'console.error', message: msg.text() }); });
  function flushErrors(name) { writeJSON(name, errors); errors = []; }

  const acctRes = await page.request.get('/bin/brightcove/accounts');
  const ACCOUNT_ID = (await acctRes.json()).accounts[0].value;

  await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20000 });
  await page.waitForTimeout(500);

  // ================= Row 13: label apply + remove =================
  {
    const TARGET_VIDEO_ID = process.env.PARITY_VIDEO_ID_LABELS || (() => { throw new Error('set PARITY_VIDEO_ID_LABELS to a video with no labels'); })(); // "42min - sync", confirmed labels: [] before this run
    const LABEL = '/test2/'; // pre-existing label, not a new one

    const before = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);

    await page.fill('#search', '42min');
    await page.click('#searchDiv #searchBut');
    await page.waitForTimeout(1200);
    await page.locator('#tbData tr', { hasText: '42min - sync' }).first().click();
    await page.waitForTimeout(500);
    await page.click('a:has-text("Edit")');
    await page.waitForTimeout(400);

    // .fill() sets the value via CDP without firing 'keyup', which is what
    // the autocomplete handler listens for -- use type() so real key events
    // fire per character.
    await page.locator('.label-add-input input').click();
    await page.locator('.label-add-input input').type('/test2', { delay: 80 });
    await page.waitForTimeout(1200); // JSONP-style suggestion round trip
    const suggestionVisible = await page.locator('.autocomplete-item', { hasText: '/test2/' }).first().isVisible().catch(() => false);
    await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-autocomplete.png'), fullPage: true });
    if (suggestionVisible) {
      await page.locator('.autocomplete-item', { hasText: '/test2/' }).first().click();
    }
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-added-in-dialog.png'), fullPage: true });

    await page.locator('.pml-dialog button:has-text("Update"), .pml-dialog .btn:has-text("Update")').first().click().catch(async () => {
      await page.click('text=Update');
    });
    await page.waitForTimeout(1500); // editLabels() success handler does location.reload()
    await page.waitForSelector('#tbData tr', { timeout: 20000 }).catch(() => {});
    await page.waitForTimeout(500);

    const afterApply = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);
    const appliedCorrectly = (afterApply.labels || []).includes(LABEL);

    // ---- remove it again (only if it was actually applied -- otherwise
    // there is nothing to remove and the delete-icon locator would hang) ----
    let afterRemove = afterApply;
    let removeAttempted = false;
    if (appliedCorrectly) {
      removeAttempted = true;
      await page.fill('#search', '42min');
      await page.click('#searchDiv #searchBut');
      await page.waitForTimeout(1200);
      await page.locator('#tbData tr', { hasText: '42min - sync' }).first().click();
      await page.waitForTimeout(500);
      await page.click('a:has-text("Edit")');
      await page.waitForTimeout(400);
      await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-before-remove.png'), fullPage: true });
      await page.locator('.label-listing li a').first().click(); // delete icon on the one chip
      await page.waitForTimeout(300);
      await page.locator('.pml-dialog button:has-text("Update"), .pml-dialog .btn:has-text("Update")').first().click().catch(async () => {
        await page.click('text=Update');
      });
      await page.waitForTimeout(1500);
      await page.waitForSelector('#tbData tr', { timeout: 20000 }).catch(() => {});
      await page.waitForTimeout(500);
      afterRemove = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);
    }

    writeJSON('row13-label-mutation.json', {
      targetVideoId: TARGET_VIDEO_ID,
      label: LABEL,
      suggestionVisible,
      labelsBefore: before.labels || [],
      labelsAfterApply: afterApply.labels || [],
      appliedCorrectly,
      removeAttempted,
      labelsAfterRemove: afterRemove.labels || [],
      revertedCorrectly: removeAttempted ? (JSON.stringify(afterRemove.labels || []) === JSON.stringify(before.labels || [])) : null
    });
    flushErrors('row13-console.json');
  }

  // ================= Row 15: move video to folder + back =================
  {
    const TARGET_VIDEO_ID = process.env.PARITY_VIDEO_ID_FOLDER || (() => { throw new Error('set PARITY_VIDEO_ID_FOLDER to a video with folder_id null'); })(); // "708-CJK-sidecar-test-...", confirmed folder_id null
    const FOLDER_ID = '69c3f2f537c42788644a8d6c'; // "aem_test_folder"

    const before = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);

    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.fill('#search', '708-CJK');
    await page.click('#searchDiv #searchBut');
    await page.waitForTimeout(1200);
    const row = page.locator('#tbData tr', { hasText: '708-CJK-sidecar-test' }).first();
    await row.click(); // opens detail panel + triggers brc:checked
    await page.waitForTimeout(400);
    await row.locator('input[type=checkbox]').check();
    await row.click(); // re-click row to re-fire brc:checked with the box now checked
    await page.waitForTimeout(400);
    const butDivVisible = await page.locator('.butDiv').first().isVisible().catch(() => false);
    await page.click('#btn_MoveVideoToFolder');
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(OUT_DIR, 'row15-folder-menu-open.png'), fullPage: true });
    await page.click('.folder-selector .menu-options li:has-text("aem_test_folder")');
    await page.waitForTimeout(1200);

    const afterMove = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);
    await page.screenshot({ path: path.join(OUT_DIR, 'row15-after-move.png'), fullPage: true });

    // move back to none
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.fill('#search', '708-CJK');
    await page.click('#searchDiv #searchBut');
    await page.waitForTimeout(1200);
    const row2 = page.locator('#tbData tr', { hasText: '708-CJK-sidecar-test' }).first();
    await row2.click();
    await page.waitForTimeout(400);
    await row2.locator('input[type=checkbox]').check();
    await row2.click();
    await page.waitForTimeout(400);
    await page.click('#btn_MoveVideoToFolder');
    await page.waitForTimeout(300);
    await page.click('.folder-selector .menu-options li:has-text("[No Folder]")');
    await page.waitForTimeout(1200);

    const afterRevert = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);
    await page.screenshot({ path: path.join(OUT_DIR, 'row15-after-revert.png'), fullPage: true });

    writeJSON('row15-folder-mutation.json', {
      targetVideoId: TARGET_VIDEO_ID,
      folderId: FOLDER_ID,
      butDivVisibleBeforeMove: butDivVisible,
      folderIdBefore: before.folder_id,
      folderIdAfterMove: afterMove.folder_id,
      folderIdAfterRevert: afterRevert.folder_id,
      movedCorrectly: afterMove.folder_id === FOLDER_ID,
      revertedCorrectly: afterRevert.folder_id === before.folder_id
    });
    flushErrors('row15-console.json');
  }

  // ================= Row 18: create/rename/add-remove -- UI inspection only =================
  {
    // Code inspection (brcUI.js, offline copy at /tmp/brcUI.js from
    // origin/onprem-master) shows editPlaylistListingCallback() builds the
    // "Edit Playlist" dialog from ONLY a video-search-to-add form and an
    // "playlist-listing" of current videos (each removable) -- no name /
    // description / reference-id field anywhere in that dialog, and its
    // Update handler posts only {a: 'update_playlist', videos: [...],
    // playlistId}. There is no separate rename affordance anywhere else in
    // brcUI.js or the served markup. Renaming a playlist has no UI path on
    // this on-prem build, so no rename mutation is attempted (there is
    // nothing to revert). Screenshots below are of the Create Playlist and
    // Edit Playlist dialogs, opened but never submitted (create isn't on the
    // pre-authorized mutation list either).
    await page.click('#allVideos');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.click('#allPlaylists');
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    await page.waitForTimeout(400);

    await page.click('#newplstButton');
    await page.waitForTimeout(400);
    await page.screenshot({ path: path.join(OUT_DIR, 'row18-create-playlist-dialog.png'), fullPage: true });
    await page.keyboard.press('Escape').catch(() => {});
    await page.click('button:has-text("Cancel")').catch(() => {});
    await page.waitForTimeout(300);

    await page.locator('.edit-playlist').first().click();
    await page.waitForTimeout(600);
    const dialogHtml = await page.locator('.pml-dialog').first().innerHTML().catch(() => '');
    const hasNameField = /name=["']?(plst\.)?name|Playlist Name|rename/i.test(dialogHtml);
    await page.screenshot({ path: path.join(OUT_DIR, 'row18-edit-playlist-dialog.png'), fullPage: true });
    writeJSON('row18-playlist-crud.json', {
      createDialogOpened: true,
      editDialogOpened: true,
      editDialogHasRenameField: hasNameField,
      note: 'editPlaylistListingCallback() (brcUI.js) only renders a video-search-to-add form plus the current video listing (each removable); its Update handler posts {a: update_playlist, videos, playlistId} with no name field anywhere. No rename UI path exists on-prem 6.0.12. Create Playlist and Edit Playlist dialogs were opened for screenshot evidence only; neither was submitted (create is not on the pre-authorized mutation list, and add/remove-video was left unexercised since it is not on that list either -- only rename+revert, label add+remove, and folder move+revert were pre-approved, and rename has no path to exercise here).'
    });
    flushErrors('row18-console.json');
    await page.keyboard.press('Escape').catch(() => {});
  }

  await context.close();
  await browser.close();
  console.log('mutation pass complete');
}

main().catch((e) => { console.error(e); process.exit(1); });
