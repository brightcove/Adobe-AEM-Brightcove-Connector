// BCON-178 — "There is no confirmation toast when switching accounts".
// Selecting a different account used to switch immediately (set cookie +
// reload). It now asks for confirmation first and only switches on confirm.
//
// Only one account is configured in local AEM (and it's active), so to exercise
// the switch path we drop `is-active` from the real, handler-bound account row.
// Confirming reloads to that same account, which is harmless.
const { test, expect, openAdmin } = require('../fixtures');

async function openPopoverAndArmRow(page) {
  await page.locator('#accountTrigger').click();
  await expect(page.locator('#accountPopover')).toBeVisible();
  await page.evaluate(() => {
    var row = document.querySelector('.brc-account-row');
    if (row) row.classList.remove('is-active'); // bypass the active-row early return
  });
}

test('switching accounts asks for confirmation and does not switch immediately', async ({ page }) => {
  await openAdmin(page);
  await openPopoverAndArmRow(page);

  // Sentinel to detect whether a reload happened.
  await page.evaluate(() => { window.__noReload = true; });

  await page.locator('.brc-account-row').first().click();

  // A confirmation dialog appears, and NO switch/reload has happened yet.
  await expect(page.locator('.pml-dialog')).toBeVisible();
  await expect(page.locator('.pml-dialog .pml-dialog_content')).toContainText(/switch to/i);
  expect(await page.evaluate(() => window.__noReload)).toBe(true);

  // Cancel → dialog closes, still no switch.
  await page.locator('.pml-dialog .pml-dialog_footer .btn-secondary').click();
  await expect(page.locator('.pml-dialog')).toBeHidden();
  expect(await page.evaluate(() => window.__noReload)).toBe(true);
});

test('confirming the switch reloads and surfaces the switched toast', async ({ page }) => {
  await openAdmin(page);
  await openPopoverAndArmRow(page);

  await page.locator('.brc-account-row').first().click();
  await expect(page.locator('.pml-dialog')).toBeVisible();

  // Confirm → cookie + reload (to the same configured account here).
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary').click();

  // After the reload, the post-switch toast confirms it.
  await expect(page.locator('#brcToast')).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('#brcToast .brc-toast-msg')).toContainText(/switched to/i);
});
