// Smoke test: the harness can authenticate to AEM, load the admin tool,
// and the configured account returns real video data.
const { test, expect, openAdmin, waitForVideoRows } = require('../fixtures');

test('admin tool loads with the configured account and shows videos', async ({ page }) => {
  await openAdmin(page);

  // Sync button is part of the tool's own header (proves the tool rendered).
  await expect(page.locator('#syncdbutton')).toBeVisible();

  // Initial video list renders real rows from the configured account.
  await waitForVideoRows(page);
  const rowCount = await page.locator('#tbData tr').count();
  expect(rowCount).toBeGreaterThan(0);
});
