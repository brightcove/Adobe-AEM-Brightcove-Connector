// BCON-182 — "Create New Label modal does not work".
// Old behaviour: any name not starting with '/' just turned the input red with
// no message (Brightcove labels are paths and must start with '/'). The fix:
// show a real message for an empty name, and auto-prepend '/' otherwise so a
// normal name is accepted.
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

test('a plain name is accepted and sent as a /-prefixed path', async ({ page }) => {
  await openAdmin(page);

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
  await page.fill('.pml-dialog .input-label-name', 'QA Test Label');

  const reqPromise = page.waitForRequest((r) => r.url().includes('a=create_label'));
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();
  await reqPromise;

  // The fix prepends '/'; a bare name must not be rejected.
  expect(createUrl).toContain('label=/QA Test Label');

  // Because Brightcove's GET /labels index lags, success must NOT depend on a
  // reload: the new label is added to the dropdown optimistically and a toast
  // confirms it.
  await expect(page.locator('#brcToast')).toBeVisible();
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText('/QA Test Label');
  await expect(page.locator('#label_list option[value="/QA Test Label"]')).toHaveCount(1);
});

test('a name already starting with / is not double-prefixed', async ({ page }) => {
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

  expect(createUrl).toContain('label=/already/pathy');
  expect(createUrl).not.toContain('label=//');
});
