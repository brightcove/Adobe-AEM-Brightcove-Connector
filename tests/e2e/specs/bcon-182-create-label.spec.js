// BCON-182 — "Create New Label modal does not work".
// Old behaviour: any name not starting with '/' just turned the input red with
// no message (Brightcove labels are paths and must start with '/'). The fix:
// show a real message for an empty name, and normalize otherwise so a normal
// name is accepted.
//
// commit 5a9cfc8 (2026-06-11) later changed that normalization from
// leading-slash-only to leading AND trailing slash (normalizeLabelPath), so
// the label a user creates matches the canonical path the apply-to-video step
// computes without requiring a hard refresh first.
//
// The create_label request is mocked so the test is deterministic and does NOT
// mutate the live Brightcove account (the connector has no delete-label
// endpoint). The full real-backend path — type "name" -> connector receives
// "/name" -> CMS creates it -> cleaned up via the CMS API — was verified
// manually during development; see the commit message / wiki.
const { test, expect, openAdmin } = require('../fixtures');

async function openCreateLabelModal(page) {
  await page.locator('#filterToggle').click();
  await expect(page.locator('#filterPanel')).toBeVisible();
  // The "+ Create New Label" option is appended asynchronously by loadLabelCallback.
  // <option> elements are never "visible", so wait for it to be attached.
  await page.waitForSelector('#label_list option[value="create"]', { state: 'attached', timeout: 15_000 });
  await page.selectOption('#label_list', 'create');
  await expect(page.locator('.pml-dialog .input-label-name')).toBeVisible();
}

test('empty label name shows a message and does not fire a request', async ({ page }) => {
  await openAdmin(page);

  let createCalls = 0;
  await page.route('**/bin/brightcove/api.js**', (route) => {
    if (route.request().url().includes('a=create_label')) createCalls++;
    return route.continue();
  });

  await openCreateLabelModal(page);

  // Leave the field empty, click Create.
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();

  // A real, visible error message — not just a red box.
  await expect(page.locator('.input-label-error')).toBeVisible();
  await expect(page.locator('.input-label-error')).toHaveText(/enter a label name/i);
  // Dialog stays open, no request fired.
  await expect(page.locator('.pml-dialog .input-label-name')).toBeVisible();
  expect(createCalls).toBe(0);
});

test('a plain name is accepted and sent as a fully slash-normalized path', async ({ page }) => {
  await openAdmin(page);

  // commit 5a9cfc8 (BCON-182) fully normalizes the label name on create (both
  // a leading AND a trailing slash, e.g. /name/) so the created path matches
  // what normalizeLabelPath() computes at apply-to-video time — previously
  // only the leading slash was added, so a freshly-created label couldn't be
  // applied until a hard refresh re-fetched it in canonical form.
  //
  // The create_label call is mocked (see file header), so this never reaches
  // the live account, but use a timestamp-suffixed name anyway: the connector
  // has no delete-label endpoint, so a fixed name risks colliding with a
  // dropdown option a prior run's mock left in a shared browser profile.
  const labelName = `QA Test Label ${Date.now()}`;
  const normalizedPath = `/${labelName}/`;

  let createUrl = null;
  // Intercept create_label: record the URL and return a success (empty body).
  await page.route('**/bin/brightcove/api.js**', (route) => {
    const url = route.request().url();
    if (url.includes('a=create_label')) {
      createUrl = decodeURIComponent(url);
      return route.fulfill({ status: 200, contentType: 'text/plain', body: '' });
    }
    return route.continue();
  });

  await openCreateLabelModal(page);
  await page.fill('.pml-dialog .input-label-name', labelName);

  const reqPromise = page.waitForRequest((r) => r.url().includes('a=create_label'));
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();
  await reqPromise;

  // The fix normalizes to leading AND trailing slash, not just leading.
  expect(createUrl).toContain(`label=${normalizedPath}`);

  // Because Brightcove's GET /labels index lags, success must NOT depend on a
  // reload: the new label is added to the dropdown optimistically and a toast
  // confirms it, in the exact canonical form the apply-to-video step expects.
  await expect(page.locator('#brcToast')).toBeVisible();
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(normalizedPath);
  await expect(
    page.locator(`#label_list option[value="${normalizedPath}"]`)
  ).toHaveCount(1);
});

test('a duplicate label shows an error and is not added to the dropdown', async ({ page }) => {
  await openAdmin(page);

  // Mock the server reporting a duplicate (BrcApi maps the CMS 422 to {"error":409}).
  await page.route('**/bin/brightcove/api.js**', (route) => {
    if (route.request().url().includes('a=create_label')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{"error":409}' });
    }
    return route.continue();
  });

  await openCreateLabelModal(page);
  await page.fill('.pml-dialog .input-label-name', 'Dup Label');
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();

  // Error message shown, dialog stays open, no toast, no dropdown entry.
  await expect(page.locator('.input-label-error')).toBeVisible();
  await expect(page.locator('.input-label-error')).toHaveText(/already exists/i);
  await expect(page.locator('.pml-dialog .input-label-name')).toBeVisible();
  await expect(page.locator('#brcToast')).toHaveCount(0);
  await expect(page.locator('#label_list option[value="/Dup Label"]')).toHaveCount(0);
});

test('a non-duplicate server error shows "Could not create", not "already exists"', async ({ page }) => {
  // Regression guard for the cursorbot finding on PR #108: BrcApi.createLabel
  // used to collapse every failure to {"error":409}, so the JS told users
  // "That label already exists" for 4xx/5xx unrelated to duplicates. After
  // the fix, non-422 errors propagate as themselves (e.g. 500), and the JS
  // shows the generic "Could not create" copy.
  await openAdmin(page);

  await page.route('**/bin/brightcove/api.js**', (route) => {
    if (route.request().url().includes('a=create_label')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{"error":500}' });
    }
    return route.continue();
  });

  await openCreateLabelModal(page);
  await page.fill('.pml-dialog .input-label-name', 'Server Error Label');
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();

  await expect(page.locator('.input-label-error')).toBeVisible();
  await expect(page.locator('.input-label-error')).toHaveText(/could not create/i);
  await expect(page.locator('.input-label-error')).not.toHaveText(/already exists/i);
  await expect(page.locator('#brcToast')).toHaveCount(0);
});

test('a name already starting with / is not double-prefixed, and gets a single trailing slash', async ({ page }) => {
  await openAdmin(page);

  let createUrl = null;
  await page.route('**/bin/brightcove/api.js**', (route) => {
    const url = route.request().url();
    if (url.includes('a=create_label')) {
      createUrl = decodeURIComponent(url);
      return route.fulfill({ status: 200, contentType: 'text/plain', body: '' });
    }
    return route.continue();
  });

  await openCreateLabelModal(page);
  await page.fill('.pml-dialog .input-label-name', '/already/pathy');

  const reqPromise = page.waitForRequest((r) => r.url().includes('a=create_label'));
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();
  await reqPromise;

  // normalizeLabelPath() (commit 5a9cfc8) leaves an existing leading slash
  // alone and adds exactly one trailing slash.
  expect(createUrl).toContain('label=/already/pathy/');
  expect(createUrl).not.toContain('label=//');
  expect(createUrl).not.toContain('pathy//');
});
