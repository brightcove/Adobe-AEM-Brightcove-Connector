// Admin tool regressions that shipped between 2026-03 and 2026-07 without a
// spec (BCON-147, 148, 172, 174, 185, 189, 191, 192 and the filter / bulk
// move-to-folder surfaces of the BCON-121..146 revamp), plus pins for two of
// the open Phase 0 defects in ONPREM-PARITY-PLAN.md §3b so they fail by name.
// Read-only against the live account: every write path is intercepted and
// answered with a plausible body, never sent.
const { test, expect, openAdmin, resolveAccountId, parseJsonp } = require('../fixtures');

const PAGE_SIZE = 30; // paging.size in brcUI.js

function jsonp(route, payload) {
  const cb = new URL(route.request().url()).searchParams.get('callback') || 'cb';
  return route.fulfill({ status: 200, contentType: 'application/javascript', body: `${cb}(${JSON.stringify(payload)});` });
}

test('BCON-191: the count pill shows the account total, not the current page size', async ({ page }) => {
  const initial = page.waitForResponse((r) => r.url().includes('/bin/brightcove/api.js') && r.url().includes('a=search_videos'));
  await openAdmin(page);
  const body = parseJsonp(await (await initial).text());
  const totals = Number(body.totals);
  test.skip(!(totals > PAGE_SIZE), `account has ${totals} videos, not more than one page (${PAGE_SIZE}); the defect is not measurable here`);
  await expect(page.locator('#divVideoCount')).toHaveText(String(totals));
  // Discriminator: the page only holds PAGE_SIZE rows, so the pill would read
  // 30 (or fewer) if it counted rows instead of totals.
  expect(await page.locator('#tbData tr').count()).toBeLessThanOrEqual(PAGE_SIZE);
  await expect(page.locator('select[name="selPageN"] option')).toHaveCount(Math.ceil(totals / PAGE_SIZE));
});

test('BCON-147: the playlists table lists every playlist and hides video paging', async ({ page }) => {
  await openAdmin(page);
  const res = page.waitForResponse((r) => r.url().includes('/bin/brightcove/api.js') && r.url().includes('a=search_playlists'));
  await page.locator('#allPlaylists').click();
  const body = parseJsonp(await (await res).text());
  const items = body.items || [];
  expect(items.length, 'the account returned no playlists').toBeGreaterThan(0);
  await expect(page.locator('#tbData tr .edit-playlist')).toHaveCount(items.length);
  await expect(page.locator('#divVideoCount')).toHaveText(String(items.length));
  await expect(page.locator('#pagination')).toBeHidden();
  // Every playlist name made it to a row.
  const rowText = (await page.locator('#tbData').innerText());
  for (const p of items.slice(0, 5)) expect(rowText).toContain(p.name);
});

test('BCON-172: Enter in the playlist search box submits the search', async ({ page }) => {
  await openAdmin(page);
  await page.locator('#allPlaylists').click();
  await expect(page.locator('#searchDiv_pl')).toBeVisible();
  await page.selectOption('#selField_pl', 'find_playlist_by_name');
  await page.fill('#search_pl', 'Smart');
  const req = page.waitForRequest((r) => r.url().includes('a=search_playlists') && decodeURIComponent(r.url()).includes('query=Smart'), { timeout: 10_000 });
  await page.locator('#search_pl').press('Enter');
  expect((await req).url()).toBeTruthy();
});

test('BCON-174: renaming a playlist refreshes the list in place, without a page reload', async ({ page }) => {
  await openAdmin(page);
  await page.locator('#allPlaylists').click();
  await page.waitForSelector('#tbData tr .edit-playlist', { timeout: 20_000 });
  await page.evaluate(() => { window.__brcE2eMarker = 'kept'; });

  const btn = page.locator('#tbData tr .edit-playlist').first();
  const name = (await btn.innerText()).trim();
  await btn.click();
  await expect(page.locator('#editPlaylistModal')).toBeVisible();

  // Intercept the write; answer success without touching the live playlist.
  await page.route(/\/bin\/brightcove\/api\.js.*a=update_playlist/, (route) => jsonp(route, { id: 'fake', name: `${name} (e2e)` }));
  const refresh = page.waitForRequest((r) => r.url().includes('a=search_playlists'), { timeout: 10_000 });
  await page.fill('#epPlaylistName', `${name} (e2e)`);
  await page.locator('#epUpdate').click();

  // The list re-fetches, the modal closes, and the document is the same one.
  expect((await refresh).url()).toContain('a=search_playlists');
  await expect(page.locator('#editPlaylistModal')).toBeHidden();
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/Playlist updated/);
  expect(await page.evaluate(() => window.__brcE2eMarker), 'the page reloaded after the rename').toBe('kept');
});

test('BCON-189: the search button is vertically centred in its pill', async ({ page }) => {
  await openAdmin(page);
  const pill = await page.locator('#searchDiv').boundingBox();
  const button = await page.locator('#searchBut').boundingBox();
  const input = await page.locator('#search').boundingBox();
  expect(pill && button && input, 'search pill parts not rendered').toBeTruthy();
  const centre = (b) => b.y + b.height / 2;
  expect(Math.abs(centre(button) - centre(pill)), `button centre ${centre(button)} vs pill centre ${centre(pill)}`).toBeLessThanOrEqual(1);
  expect(Math.abs(centre(button) - centre(input)), 'button and input are not on one line').toBeLessThanOrEqual(1);
});

test.describe('sync database (BCON-148, BCON-185, BCON-192)', () => {
  test('shows the overlay and a spinning, disabled button while the sync is in flight, then restores both', async ({ page }) => {
    await openAdmin(page);
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));

    // Hold the (intercepted) dataload open until the in-flight state has been measured.
    let release;
    const held = new Promise((resolve) => { release = resolve; });
    await page.route(/\/bin\/brightcove\/dataload/, async (route) => {
      await held;
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"ok"}' });
    });

    const button = page.locator('#syncdbutton');
    await expect(page.locator('#syncOverlay')).toBeHidden();
    await button.click();

    await expect(page.locator('#syncOverlay')).toBeVisible();
    await expect(button).toHaveClass(/is-loading/);
    await expect(button).toBeDisabled();
    await expect(button.locator('.brc-sync-btn-label')).toHaveText('Loading sync');
    // BCON-192: the icon spins rather than sitting static and dimmed.
    const animation = await button.locator('.brc-sync-icon').evaluate((el) => getComputedStyle(el).animationName);
    expect(animation, 'sync icon has no animation while loading').not.toBe('none');

    release();
    await expect(page.locator('#syncOverlay')).toBeHidden({ timeout: 10_000 });
    await expect(button).not.toHaveClass(/is-loading/);
    await expect(button).toBeEnabled();
    await expect(button.locator('.brc-sync-btn-label')).toHaveText('Sync database');
    // The state machine is intact even though the response handler throws
    // (see the pinned defect below): syncEnd() runs before the parse.
  });

  // ONPREM-PARITY-PLAN.md §3b item 2, widened 2026-09-18: syncDB() calls
  // $.parseJSON on whatever jQuery hands its success callback. The real
  // servlet answers 200 with an EMPTY body ("Unexpected end of JSON input");
  // a well-formed JSON body is no better, because jQuery has already parsed
  // it into an object and re-parsing an object throws '"[object Object]" is
  // not valid JSON'. Every sync therefore ends in an uncaught exception
  // whatever the server returns. Flip to `test` when the parse is removed.
  // The real servlet answers `200`, `Content-Length: 0`, no Content-Type
  // (measured 2026-09-18 on 7.4.0); the empty-body case mirrors those headers
  // exactly, because with a JSON content type jQuery takes the error path
  // instead and the parse is never reached.
  const responses = [
    ['an empty 200', { status: 200, headers: { 'Content-Length': '0' }, body: '' }],
    ['a well-formed JSON 200', { status: 200, contentType: 'application/json', body: '{"status":"ok"}' }],
  ];
  for (const [label, response] of responses) {
    test.fixme(`${label} from dataload does not throw`, async ({ page }) => {
      await openAdmin(page);
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await page.route(/\/bin\/brightcove\/dataload/, (route) => route.fulfill(response));
      await page.locator('#syncdbutton').click();
      await expect(page.locator('#syncOverlay')).toBeHidden({ timeout: 10_000 });
      expect(errors.filter((e) => /JSON/.test(e))).toEqual([]);
    });
  }
});

test.describe('filter panel and move-to-folder (BCON-121/128/131/141/142/146)', () => {
  test('the filter panel opens and its folder and label lists come from the live account', async ({ page }) => {
    await openAdmin(page);
    const toggle = page.locator('#filterToggle');
    const panel = page.locator('#filterPanel');
    await expect(panel).toBeHidden();
    await toggle.click();
    await expect(panel).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    // "All ..." plus at least one real entry each, populated after load.
    await expect.poll(() => page.locator('#fldr_list option').count(), { timeout: 15_000 }).toBeGreaterThan(1);
    await expect.poll(() => page.locator('#label_list option').count(), { timeout: 15_000 }).toBeGreaterThan(1);

    // Choosing a folder narrows the list through a request that names it.
    const folderId = await page.locator('#fldr_list option').nth(1).getAttribute('value');
    const req = page.waitForRequest((r) => r.url().includes('/bin/brightcove/api.js') && r.url().includes(folderId), { timeout: 10_000 });
    await page.selectOption('#fldr_list', folderId);
    expect((await req).url()).toContain(folderId);
    await expect(page.locator('#filterClearAll')).toBeVisible();

    await toggle.click();
    await expect(panel).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  test('bulk move-to-folder lists the account folders and only enables Move once one is picked', async ({ page }) => {
    await openAdmin(page);
    await page.locator('#tbData tr input[type="checkbox"]').first().check();
    await expect(page.locator('#bulkActionBar')).toBeVisible();
    await expect(page.locator('#bulkCount')).toHaveText('1');

    await page.locator('#bulkMoveToFolder').click();
    const modal = page.locator('#moveToFolderModal');
    await expect(modal).toBeVisible();
    const items = modal.locator('#mtfFolderList .brc-mtf-list-item');
    await expect.poll(() => items.count(), { timeout: 15_000 }).toBeGreaterThan(0);
    await expect(page.locator('#mtfMove')).toBeDisabled();
    await items.first().click();
    await expect(items.first()).toHaveClass(/is-selected/);
    await expect(page.locator('#mtfMove')).toBeEnabled();
    // Cancel: nothing is sent to the account.
    let moved = false;
    await page.route(/a=(move_video_to_folder|remove_video_from_folder)/, (route) => { moved = true; return route.abort(); });
    await page.locator('#mtfCancel').click();
    await expect(modal).toBeHidden();
    expect(moved).toBe(false);
  });

  // §3b item 3: there is no way to take a video OUT of a folder; the modal
  // lists real folders only. Flip to `test` when a "no folder" choice ships.
  test.fixme('the move-to-folder modal offers a way to remove the video from its folder', async ({ page }) => {
    await openAdmin(page);
    await page.locator('#tbData tr input[type="checkbox"]').first().check();
    await page.locator('#bulkMoveToFolder').click();
    await expect(page.locator('#mtfFolderList [data-folder-id="none"], #mtfFolderList .brc-mtf-list-item--none')).toHaveCount(1);
  });
});

// §3b item 1: removing the LAST label is a silent no-op with a "Labels saved"
// toast. jQuery's $.param drops an empty array, so the request carries no
// `labels` parameter at all and the server treats "absent" as "leave alone".
// The write is intercepted; the assertion is on the outgoing request shape.
test.fixme('§3b-1: saving with every label removed still sends an (empty) labels parameter', async ({ page }) => {
  await openAdmin(page);
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();
  await expect.poll(() => page.locator('#label_list option[value^="/"]').count(), { timeout: 15_000 }).toBeGreaterThan(0);
  // Make sure there is at least one pill to remove, then remove them all.
  if ((await page.locator('#divMeta\\.labels .brc-label-pill').count()) === 0) {
    const known = await page.locator('#label_list option[value^="/"]').first().getAttribute('value');
    await page.locator('#labelInput').fill(known);
    await page.locator('#labelInput').press('Enter');
  }
  while ((await page.locator('#divMeta\\.labels .brc-label-pill').count()) > 0) {
    await page.locator('#divMeta\\.labels .brc-label-pill-remove').first().click();
  }
  let captured = null;
  await page.route(/\/bin\/brightcove\/api\.js.*a=update_labels/, (route) => { captured = route.request().url(); return jsonp(route, { id: 'fake', labels: [] }); });
  await page.locator('#saveLabelsBtn').click();
  await expect.poll(() => captured, { timeout: 10_000 }).not.toBeNull();
  expect(decodeURIComponent(captured), 'the empty labels list was dropped from the request').toMatch(/[?&]labels=/);
});

// §3b item 5: get_videos_with_label without `start` is a server 500 with an
// empty body (NumberFormatException in the param parsing). API-level pin.
test.fixme('§3b-5: get_videos_with_label without start is not a 500', async ({ request }) => {
  const acct = await resolveAccountId(request);
  const res = await request.get(`/bin/brightcove/api.js?account_id=${acct}&a=get_videos_with_label&labels=/e2e/&limit=1&callback=cb`);
  expect(res.status()).toBeLessThan(500);
});
