// Label persistence — three bugs caught after PR #108 went up for QA:
//
//   1. The free-text #labelInput pushed bare typed strings into _currentLabels
//      (e.g. "mylabel"). Brightcove rejects any label that isn't /-prefixed-/
//      AND already on the account's known list with HTTP 422 ILLEGAL_VALUE.
//
//   2. CmsAPI.updateLabels did `(ObjectNode) MAPPER.readTree(response)` — that
//      cast threw on Brightcove's array-shaped error body
//      `[{"error_code":"VALIDATION_ERROR","message":"..."}]`, the exception
//      was swallowed, and the JS client received an empty `{}` that looked
//      like success.
//
//   3. saveLabels' $.ajax had no dataType, so the JSONP response was executed
//      as a script and the success handler unconditionally toasted "Labels
//      saved" — even on a 422 from Brightcove.
//
// This spec covers the full chain: input rejection of unknown labels, success
// toast on a real save, and error toast surfacing when Brightcove rejects.
const { test, expect, openAdmin } = require('../fixtures');

async function openFirstVideoPanel(page) {
  await openAdmin(page);
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();
  await expect(page.locator('#labelInput')).toBeVisible();
  // Wait until the labels dropdown has been populated (loadLabelCallback).
  await page.waitForFunction(() => {
    return document.querySelectorAll('#label_list option[value^="/"]').length > 0;
  }, null, { timeout: 20_000 });
}

test('unknown typed label is rejected before save', async ({ page }) => {
  await openFirstVideoPanel(page);

  const pillsBefore = await page.locator('#divMeta\\.labels .brc-label-pill').count();

  await page.locator('#labelInput').fill('bogus-never-exists');
  await page.locator('#labelInput').press('Enter');

  // Toast surfaces the rejection.
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/not found/i);

  // No pill added.
  const pillsAfter = await page.locator('#divMeta\\.labels .brc-label-pill').count();
  expect(pillsAfter).toBe(pillsBefore);
});

test('known label saves successfully and outgoing request carries the /-form', async ({ page }) => {
  await openFirstVideoPanel(page);

  // Use the first canonical label path the dropdown advertises. Strip the
  // leading and trailing slashes so we exercise the JS normalizer too.
  const knownPath = await page.locator('#label_list option[value^="/"]').first().getAttribute('value');
  expect(knownPath).toBeTruthy();
  const bareName = knownPath.replace(/^\/+|\/+$/g, '');

  await page.locator('#labelInput').fill(bareName);
  await page.locator('#labelInput').press('Enter');

  // A pill rendered with the canonical /-form (proves the normalizer ran).
  await expect(page.locator('#divMeta\\.labels .brc-label-pill')).toContainText(knownPath);

  // Mock update_labels so the save doesn't depend on a live API and stays
  // deterministic. Return a Brightcove-shaped success body (the full video).
  let capturedUrl = null;
  await page.route(/\/bin\/brightcove\/api\.js.*a=update_labels/, async (route) => {
    capturedUrl = route.request().url();
    const cb = new URL(capturedUrl).searchParams.get('callback') || 'cb';
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: `${cb}({"id":"fake","labels":["${knownPath}"]});`,
    });
  });

  await page.locator('#saveLabelsBtn').click();

  await expect.poll(() => capturedUrl, { timeout: 10_000 }).not.toBeNull();
  // The outgoing request must carry the canonical /-prefixed label, not the
  // bare name the user typed.
  expect(decodeURIComponent(capturedUrl)).toContain('labels=' + knownPath);

  await expect(page.locator('#brcToast .brc-toast-msg')).toHaveText(/^Labels saved$/);
});

test('Brightcove 422 surfaces an error toast (no false-positive "saved")', async ({ page }) => {
  await openFirstVideoPanel(page);

  const knownPath = await page.locator('#label_list option[value^="/"]').first().getAttribute('value');
  await page.locator('#labelInput').fill(knownPath.replace(/^\/+|\/+$/g, ''));
  await page.locator('#labelInput').press('Enter');

  await page.route(/\/bin\/brightcove\/api\.js.*a=update_labels/, async (route) => {
    const cb = new URL(route.request().url()).searchParams.get('callback') || 'cb';
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: `${cb}({"error_code":422,"message":"/whatever/: ILLEGAL_VALUE"});`,
    });
  });

  await page.locator('#saveLabelsBtn').click();

  // Must surface the failure — not a phantom success.
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/not saved/i);
  await expect(page.locator('#brcToast .brc-toast-msg')).not.toContainText(/^Labels saved$/);
});
