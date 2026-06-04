// BCON-176 + BCON-177 — the variant dialog on the DAM asset metadata editor
// (Brightcove tab), a different surface from the admin tool.
//
// BCON-176: Brightcove requires the variant `name` (POST rejects an empty name
//   with "name: REQUIRED_FIELD"), but the dialog neither marked Name required
//   nor validated it, then mislabelled the resulting error as a language-code
//   problem. Fix: mark Name required + validate client-side with a Name message.
// BCON-177: required custom-field asterisks rendered black (plain " *") instead
//   of red. Fix: wrap in the same red span the Language Code already used. This
//   account has no required custom fields, so we verify the shared red-asterisk
//   rendering via the Language Code + Name required fields.
const { test, expect } = require('../fixtures');

// A Brightcove-linked asset present in the local DAM.
const ASSET = '/content/dam/brightcove_assets/5822937471001/6238746490001.mp4';
const EDITOR = '/mnt/overlay/dam/gui/content/assets/metadataeditor.external.html' + ASSET;
const RED = 'rgb(217, 83, 79)'; // #d9534f

async function openVariantDialog(page) {
  await page.goto(EDITOR, { waitUntil: 'networkidle' });
  // Activate the Brightcove tab so the variant section (and its Add button) is interactable.
  await page.locator('coral-tab:has-text("Brightcove")').click();
  const addBtn = page.locator('.brc-add-variant-btn').first();
  await addBtn.waitFor({ state: 'visible', timeout: 10_000 });
  await addBtn.click();
  // Dialog content becomes visible once Coral upgrades + shows it.
  await page.locator('#brc-variant-language').waitFor({ state: 'visible', timeout: 8_000 });
}

test('the Brightcove tab is not falsely flagged invalid on load', async ({ page }) => {
  // Regression: marking the dialog's Name field aria-required flagged the whole
  // Brightcove tab as invalid (red icon, no message) because the dialog markup
  // lives inside the asset metadata form. The tab must load clean.
  await page.goto(EDITOR, { waitUntil: 'networkidle' });
  const bcTab = page.locator('coral-tab:has-text("Brightcove")');
  await expect(bcTab).not.toHaveClass(/is-invalid/);
  await bcTab.click();
  await page.waitForTimeout(500);
  await expect(bcTab).not.toHaveClass(/is-invalid/);
  // and no field inside is marked invalid
  expect(await page.locator('#brc-variant-name[aria-invalid="true"]').count()).toBe(0);
});

test('BCON-176: empty variant name shows a Name error, not a language-code error', async ({ page }) => {
  await openVariantDialog(page);

  await page.fill('#brc-variant-language', 'es');
  // leave Name empty
  await page.locator('#brc-variant-save').click();

  const err = page.locator('#brc-variant-error');
  await expect(err).toBeVisible();
  await expect(err).toHaveText(/name is required/i);
  await expect(err).not.toHaveText(/language code/i);
  // Dialog stays open (no submit happened).
  await expect(page.locator('#brc-variant-language')).toBeVisible();
});

test('BCON-177: required-field asterisks render in red', async ({ page }) => {
  await openVariantDialog(page);

  // Name is now a required field (BCON-176) and its asterisk uses the exact
  // red-span markup applied to required custom fields (BCON-177).
  const nameAsterisk = page.locator('label[for="brc-variant-name"] span');
  await expect(nameAsterisk).toHaveText('*');
  expect(await nameAsterisk.evaluate(el => getComputedStyle(el).color)).toBe(RED);

  // Language Code (always required) likewise.
  const langAsterisk = page.locator('label[for="brc-variant-language"] span');
  await expect(langAsterisk).toHaveText('*');
  expect(await langAsterisk.evaluate(el => getComputedStyle(el).color)).toBe(RED);
});
