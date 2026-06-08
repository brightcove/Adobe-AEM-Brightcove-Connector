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
//   4. Save POSTs the correct field (poster_source / thumbnail_source) and
//      updates the preview without a page reload.
//   5. Cancel returns the widget to button view without sending anything.
//   6. Switching videos resets a stuck edit-mode (no carryover state).
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
  await page.route(/api\.js$/, async (route) => {
    if (route.request().method() === 'POST' && (route.request().postData() || '').includes('a=upload_image')) {
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

test('Save posts the correct field, updates the preview, no popup, no page reload', async ({ page }) => {
  await openFirstVideoPanel(page);
  const $poster = page.locator('.brc-image-widget[data-image-kind="poster"]');

  // Sentinel — survives if there's no full page reload.
  await page.evaluate(() => { window.__bcon184Sentinel = 'alive'; });

  let postedBody = null;
  await page.route(/api\.js$/, async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    const body = route.request().postData() || '';
    if (!body.includes('a=upload_image')) return route.continue();
    postedBody = body;
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  const url = 'https://example.com/poster-' + Date.now() + '.jpg';
  await $poster.locator('.brc-image-url-btn').click();
  await $poster.locator('.brc-image-url-input').fill(url);
  await $poster.locator('.brc-image-url-save').click();

  // Outgoing POST carries poster_source (NOT thumbnail_source).
  await expect.poll(() => postedBody, { timeout: 10_000 }).not.toBeNull();
  expect(postedBody).toContain('a=upload_image');
  expect(postedBody).toContain('poster_source=' + encodeURIComponent(url));
  expect(postedBody).not.toContain('thumbnail_source=');

  // Preview updated.
  await expect(page.locator('#divMeta\\.posterPreview img')).toHaveAttribute('src', url);
  // Toast.
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/Poster updated/);
  // Returned to button view, no popup.
  await expect($poster.locator('.brc-image-url-edit')).toBeHidden();
  await expect($poster.locator('.brc-image-url-btn')).toBeVisible();
  expect(await page.locator('#upload_poster_dialog').count()).toBe(0);
  // No page reload.
  const sentinel = await page.evaluate(() => window.__bcon184Sentinel);
  expect(sentinel).toBe('alive');
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
