// BCON-184 — "The Image URL workflow is different than Figma Designs".
// Was: clicking "Change URL"/"Enter URL" opened a Coral.Dialog modal popup
// over the whole screen. Expected (per Figma): the image widget itself
// transforms inline — input + Save + Cancel appear where the button was,
// the URL is listed in the panel, no popup.
//
// Asserts each enumerated requirement:
//   1. No popup dialog is created when ENTER URL is clicked.
//   2. The inline edit row becomes visible inside the image widget.
//   3. The URL input is in the side panel (no global overlay).
//   4. Save sends the correct field (poster_source / thumbnail_source) and
//      updates the preview without a page reload.
//   5. Cancel returns the widget to button view without sending anything.
//   6. Switching videos resets a stuck edit-mode (no carryover state).
//
// commit 764e5f2 (2026-06-11, "JSONP response parsing") changed the save call
// from a POST with dataType:'json' to a GET-via-JSONP call: the .js endpoint
// always responds `callback({...})` (BrcApi's `.js` branch pins HTTP 200
// always, since a script-tag JSONP load can't surface an error status), and
// the old dataType:'json' POST made jQuery try to JSON.parse that wrapper,
// throw, and report a successful queue as "update failed". Tests 3 and 4
// mock the query-string GET and echo the caller's `callback` param back,
// matching the shape brcUI.js's `.brc-image-url-save` handler parses.
const { test, expect, openAdmin } = require('../fixtures');

async function openFirstVideoPanel(page) {
  await openAdmin(page);
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();
  await page.waitForSelector('.brc-image-widget[data-image-kind="poster"]', { state: 'visible' });
}

test('clicking ENTER URL opens inline edit (no Coral.Dialog popup)', async ({ page }) => {
  await openFirstVideoPanel(page);

  const $poster = page.locator('.brc-image-widget[data-image-kind="poster"]');
  await $poster.locator('.brc-image-url-btn').click();

  // Inline edit row visible, button hidden.
  await expect($poster.locator('.brc-image-url-edit')).toBeVisible();
  await expect($poster.locator('.brc-image-url-btn')).toBeHidden();
  await expect($poster.locator('.brc-image-url-input')).toBeVisible();
  await expect($poster.locator('.brc-image-url-save')).toBeVisible();
  await expect($poster.locator('.brc-image-url-cancel')).toBeVisible();

  // No Coral.Dialog popup created in the body. (Generic `coral-dialog`
  // elements may exist from other Coral components — the specific assertion
  // is that THIS code path no longer creates `#upload_poster_dialog`.)
  expect(await page.locator('#upload_poster_dialog').count()).toBe(0);
  expect(await page.locator('#upload_thumbnail_dialog').count()).toBe(0);
  // And no visible Coral popup is open as a result of this click.
  expect(await page.locator('coral-dialog[open]').count()).toBe(0);
});

test('Cancel returns the widget to button view without sending anything', async ({ page }) => {
  await openFirstVideoPanel(page);
  const $thumb = page.locator('.brc-image-widget[data-image-kind="thumbnail"]');

  let uploadFired = false;
  // Match on the URL, not the method: the save request is now a GET/JSONP
  // call (commit 764e5f2), so filtering on POST here would never catch a
  // regression where Cancel accidentally still fires the save.
  await page.route('**/bin/brightcove/api.js**', async (route) => {
    if (route.request().url().includes('a=upload_image')) {
      uploadFired = true;
    }
    return route.continue();
  });

  await $thumb.locator('.brc-image-url-btn').click();
  await expect($thumb.locator('.brc-image-url-edit')).toBeVisible();
  await $thumb.locator('.brc-image-url-input').fill('https://example.com/never-saved.jpg');
  await $thumb.locator('.brc-image-url-cancel').click();

  await expect($thumb.locator('.brc-image-url-edit')).toBeHidden();
  await expect($thumb.locator('.brc-image-url-btn')).toBeVisible();
  expect(uploadFired).toBe(false);
});

test('Save posts the correct field, queues the ingest, no popup, no page reload', async ({ page }) => {
  await openFirstVideoPanel(page);
  const $poster = page.locator('.brc-image-widget[data-image-kind="poster"]');

  // Sentinel — survives if there's no full page reload.
  await page.evaluate(() => { window.__bcon184Sentinel = 'alive'; });

  let requestUrl = null;
  // GET/JSONP mock (commit 764e5f2): match on URL, echo the caller-supplied
  // `callback` query param back wrapping the real success shape
  // ({"job_id":"<id>"}), content-type text/javascript, status 200 — exactly
  // what BrcApi's `.js` branch always sends (it never sets a non-200 status,
  // since a script-tag JSONP load can't surface one to the browser).
  await page.route('**/bin/brightcove/api.js**', async (route) => {
    const url = route.request().url();
    if (!url.includes('a=upload_image')) return route.continue();
    requestUrl = url;
    const callback = new URL(url).searchParams.get('callback') || 'callback';
    await route.fulfill({
      status: 200,
      contentType: 'text/javascript',
      body: `${callback}({"job_id":"fake-job-id-12345"});`,
    });
  });

  const url = 'https://example.com/poster-' + Date.now() + '.jpg';
  await $poster.locator('.brc-image-url-btn').click();
  await $poster.locator('.brc-image-url-input').fill(url);
  await $poster.locator('.brc-image-url-save').click();

  // Outgoing GET carries poster_source (NOT thumbnail_source) as a query param.
  await expect.poll(() => requestUrl, { timeout: 10_000 }).not.toBeNull();
  expect(requestUrl).toContain('a=upload_image');
  expect(requestUrl).toContain('poster_source=' + encodeURIComponent(url));
  expect(requestUrl).not.toContain('thumbnail_source=');

  // Optimistic preview updated (real CDN URL appears on refresh once
  // Brightcove finishes processing — see toast copy).
  await expect(page.locator('#divMeta\\.posterPreview img')).toHaveAttribute('src', url);
  // Toast is honest about the async nature — not "Poster updated".
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/queued/i);
  await expect(page.locator('#brcToast .brc-toast-msg')).not.toContainText(/^Poster updated$/);
  // Returned to button view, no popup.
  await expect($poster.locator('.brc-image-url-edit')).toBeHidden();
  await expect($poster.locator('.brc-image-url-btn')).toBeVisible();
  expect(await page.locator('#upload_poster_dialog').count()).toBe(0);
  // No page reload.
  const sentinel = await page.evaluate(() => window.__bcon184Sentinel);
  expect(sentinel).toBe('alive');
});

test('Brightcove ingest error surfaces a real error toast (not phantom success)', async ({ page }) => {
  // Regression guard for the silent-failure bug: BrcApi.uploadImage used
  // to return null unconditionally so the JS always toasted success even
  // when Brightcove rejected the queue request. Now the JS inspects
  // resp.error_code and surfaces the real message.
  await openFirstVideoPanel(page);
  const $thumb = page.locator('.brc-image-widget[data-image-kind="thumbnail"]');

  // Capture the preview src BEFORE the save attempt so we can assert it
  // didn't get optimistically overwritten on a failure.
  const previewBefore = await page.locator('#divMeta\\.thumbPreview img').count() > 0
    ? await page.locator('#divMeta\\.thumbPreview img').getAttribute('src')
    : null;

  // GET/JSONP mock (commit 764e5f2): the server ALWAYS returns HTTP 200 for
  // the `.js` endpoint (a script-tag JSONP load can't surface a non-200
  // status), so the failure is communicated purely via the wrapped body's
  // error_code/message — exactly what BrcApi.uploadImage emits when
  // Brightcove's Dynamic Ingest rejects the queue request.
  await page.route('**/bin/brightcove/api.js**', async (route) => {
    const url = route.request().url();
    if (!url.includes('a=upload_image')) return route.continue();
    const callback = new URL(url).searchParams.get('callback') || 'callback';
    await route.fulfill({
      status: 200,
      contentType: 'text/javascript',
      body: `${callback}({"error_code":422,"message":"Invalid URL: must be HTTPS and reachable"});`,
    });
  });

  await $thumb.locator('.brc-image-url-btn').click();
  await $thumb.locator('.brc-image-url-input').fill('https://example.com/bogus.jpg');
  await $thumb.locator('.brc-image-url-save').click();

  // Error surfaces in the toast with the upstream message.
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/Thumbnail update failed/);
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/Invalid URL/);
  // The edit row stays open + re-enabled so the user can fix the URL.
  await expect($thumb.locator('.brc-image-url-edit')).toBeVisible();
  await expect($thumb.locator('.brc-image-url-save')).toBeEnabled();
  await expect($thumb.locator('.brc-image-url-input')).toBeEnabled();
  // No optimistic preview update on failure.
  const previewAfter = await page.locator('#divMeta\\.thumbPreview img').count() > 0
    ? await page.locator('#divMeta\\.thumbPreview img').getAttribute('src')
    : null;
  expect(previewAfter).toBe(previewBefore);
});

test('switching videos closes any stuck inline edit state', async ({ page }) => {
  await openFirstVideoPanel(page);
  const $poster = page.locator('.brc-image-widget[data-image-kind="poster"]');

  await $poster.locator('.brc-image-url-btn').click();
  await $poster.locator('.brc-image-url-input').fill('https://example.com/dirty.jpg');
  await expect($poster.locator('.brc-image-url-edit')).toBeVisible();

  // Click a different row — edit mode should reset.
  const rows = page.locator('#tbData tr');
  const total = await rows.count();
  test.skip(total < 2, 'Need at least 2 videos to test cross-video reset');
  await rows.nth(1).click();
  await expect(page.locator('#tdMeta')).toBeVisible();

  await expect($poster.locator('.brc-image-url-edit')).toBeHidden();
  await expect($poster.locator('.brc-image-url-btn')).toBeVisible();
});
