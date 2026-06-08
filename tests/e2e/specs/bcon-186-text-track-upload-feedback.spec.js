// BCON-186 — "No Confirmation Toast or loading screen after uploading a
// Text Track". Original behaviour: click Upload → no visible feedback,
// then suddenly the whole page reloads.
//
// After this fix:
//   - Upload button disables + label becomes "Uploading…" while in flight.
//   - On success: brcToast("Text track uploaded"), modal closes, panel
//     re-fetches via showMetaDataByVideoID (no location.reload).
//   - On failure: brcToast("Text track upload failed — please try again"),
//     button re-enabled, modal stays open.
//
// The re-fetch-not-reload behaviour is verified by a window-sentinel that
// would be wiped by a full navigation.
const { test, expect, openAdmin } = require('../fixtures');

async function openUploadModalForFirstVideo(page) {
  await openAdmin(page);
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();
  await page.locator('#trackarea button[onclick^="uploadtrack"]').click();
  await expect(page.locator('#uploadTextTrackModal')).toBeVisible();
  // Minimal valid form. The language autocomplete validates against BCP-47
  // codes (its option list is ["ar","ar-AE",...,"en","en-US",...]) — fill
  // with "en" so the exact-match validator flips _uttLangValid=true.
  await page.fill('#uttLanguage', 'en');
  await page.locator('#uttLanguage').press('Tab');
  await page.fill('#uttSourceUrl', 'https://example.com/track.vtt');
  await page.fill('#uttLabel', 'e2e-upload-test');
  // Wait until the Upload button becomes enabled.
  await expect(page.locator('#uttUpload')).toBeEnabled();
}

test('upload shows loading state then success toast and re-renders without page reload', async ({ page }) => {
  await openUploadModalForFirstVideo(page);

  // Plant a sentinel so we can detect any full page reload.
  await page.evaluate(() => { window.__bcon186Sentinel = 'alive'; });

  // Mock upload — delay so we can observe the "Uploading…" state.
  await page.route(/api\.js$/, async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    const body = route.request().postData() || '';
    if (!body.includes('a=upload_text_track')) return route.continue();
    await new Promise((r) => setTimeout(r, 250));
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.locator('#uttUpload').click();

  // Loading state: button reads "Uploading…" + is disabled.
  await expect(page.locator('#uttUpload')).toHaveText(/Uploading/i);
  await expect(page.locator('#uttUpload')).toBeDisabled();

  // Success: toast + modal hidden + sentinel survives (no full reload).
  await expect(page.locator('#brcToast .brc-toast-msg')).toHaveText(/Text track uploaded/i);
  await expect(page.locator('#uploadTextTrackModal')).toBeHidden();
  const sentinel = await page.evaluate(() => window.__bcon186Sentinel);
  expect(sentinel).toBe('alive');
});

test('upload failure surfaces an error toast and leaves the modal open', async ({ page }) => {
  await openUploadModalForFirstVideo(page);

  await page.route(/api\.js$/, async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    const body = route.request().postData() || '';
    if (!body.includes('a=upload_text_track')) return route.continue();
    await route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
  });

  await page.locator('#uttUpload').click();

  await expect(page.locator('#brcToast .brc-toast-msg')).toHaveText(/upload failed/i);
  await expect(page.locator('#uploadTextTrackModal')).toBeVisible();
  // Button re-enabled so the user can retry without dismissing.
  await expect(page.locator('#uttUpload')).toBeEnabled();
});
