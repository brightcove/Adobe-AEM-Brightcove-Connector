// Live mutation journey: add, update and delete a language variant from the variant dialog on the
// DAM asset metadata editor (Brightcove tab). Converts the view-only
// parity/probes/cloud/dam-variant-view-probe.js into a regression spec. (The variant dialog lives
// on the DAM asset editor, not in the admin tool.)
//
// The spec creates a throwaway video and a throwaway DAM asset carrying the sync marker the connector
// writes on import (brc_id + brc_account_id), so the editor addresses that video and no real one.
// Every mutation is driven through the real dialog, opened from the editor URL, and is asserted TWICE
// against persisted state: an independent CMS read (tests/e2e/cms.js), then a fresh load of the
// editor asserting what the user sees.
//
// Needs the Video Cloud credentials of the account the AEM under test is configured with
// (BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID / _CLIENT_SECRET). Without them the file skips as NOT
// MEASURED. Removes the video and the DAM folder (e2e-throwaway- prefix) in afterAll.
const { test, markLive, expect, throwawayName, expectNoThrowawaysLeft, liveCms, resolveAccountId } = require('../fixtures');
markLive(test); // credential-dependent: counts toward the gate's e2e_live verdict
const dam = require('../dam');

test.describe.configure({ mode: 'serial' });

const METADATA_EDITOR = '/mnt/overlay/dam/gui/content/assets/metadataeditor.external.html';
const DAM_PARENT = '/content/dam';
const LANG = 'fr';
const BRIGHTCOVE_SCHEMA = '/conf/global/settings/dam/adminui-extension/metadataschema/brightcove';

let cms;
let video;      // { id, name }
let folderPath;
let assetPath;

test.beforeAll(async ({ request }) => {
  test.setTimeout(240_000); // asset upload + DAM processing wait
  cms = await liveCms(request);
  await dam.sweepThrowawayNodes(request, DAM_PARENT);
  const name = throwawayName('variant');
  video = { id: await cms.create(name), name };
  folderPath = `${DAM_PARENT}/${throwawayName('variantdam')}`;
  await dam.createDamFolder(request, folderPath);
  // The folder property that puts the Brightcove tab on the metadata editor of every asset in it
  // (the connector sets it on its own asset folder: ui.content .../brightcove_assets/.content.xml).
  await dam.setProps(request, `${folderPath}/jcr:content`, { metadataSchema: BRIGHTCOVE_SCHEMA });
  ({ assetPath } = await dam.uploadAsset(request, folderPath, `${throwawayName('variantasset')}.mp4`));
  await dam.setMetadata(request, assetPath, { brc_id: video.id, brc_account_id: await resolveAccountId(request) });
});

test.afterAll(async ({ request }) => {
  // Runs on failure too. Each delete is prefix-guarded.
  if (!cms) return;
  if (video) await cms.delIfThrowaway(video.id);
  if (folderPath) await dam.deleteNode(request, folderPath);
  await expectNoThrowawaysLeft(cms);
});

async function openEditor(page) {
  await page.goto(METADATA_EDITOR + assetPath, { waitUntil: 'networkidle' });
  await page.locator('coral-tab:has-text("Brightcove")').click();
  await page.locator('.brc-add-variant-btn').first().waitFor({ state: 'visible', timeout: 10_000 });
}

const badge = (page) => page.locator('.brc-variant-badge', { has: page.locator('.brc-variant-language', { hasText: new RegExp(`^${LANG}$`) }) });
const variantOf = async () => (await cms.variants(video.id)).find((v) => v.language === LANG);

// The editor mirrors every variant change into the asset's brc_variants metadata with a separate,
// fire-and-forget POST after the CMS call returns. Wait for it before reloading, as a user who
// pauses before hitting refresh would; reloading in the same instant races it.
const jcrPersisted = (page) => page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/jcr:content/metadata'));

async function submitDialog(page, expectedAction) {
  const saved = page.waitForResponse((r) => r.url().includes('/bin/brightcove/api.json') && r.request().method() === 'POST' && (r.request().postData() || '').includes(`a=${expectedAction}`));
  const persisted = jcrPersisted(page);
  await page.locator('#brc-variant-save').click();
  expect((await saved).status()).toBe(200);
  expect((await persisted).status()).toBe(200);
  await expect(page.locator('#brc-variant-error')).toBeHidden();
}

test('add a variant', async ({ page }) => {
  await openEditor(page);
  await expect(badge(page)).toHaveCount(0);
  await page.locator('.brc-add-variant-btn').first().click();
  await page.locator('#brc-variant-language').fill(LANG);
  await page.locator('#brc-variant-name').fill('Nom original');
  await page.locator('#brc-variant-description').fill('Description originale');
  await submitDialog(page, 'add_variant');

  // 1. CMS: the variant exists with what was typed.
  await expect.poll(async () => (await variantOf()) || null, { timeout: 30_000 }).toMatchObject({ language: LANG, name: 'Nom original', description: 'Description originale' });

  // 2. After a refresh the editor lists it, and its edit dialog carries the saved values.
  await openEditor(page);
  await expect(badge(page)).toHaveCount(1);
  await badge(page).locator('.brc-edit-variant-btn').click();
  await expect(page.locator('#brc-variant-name')).toHaveValue('Nom original');
  await expect(page.locator('#brc-variant-description')).toHaveValue('Description originale');
});

test('update a variant', async ({ page }) => {
  await openEditor(page);
  await badge(page).locator('.brc-edit-variant-btn').click();
  await expect(page.locator('#brc-variant-name')).toHaveValue('Nom original');
  await page.locator('#brc-variant-name').fill('Nom modifie');
  await page.locator('#brc-variant-description').fill('Description modifiee');
  await submitDialog(page, 'update_variant');

  await expect.poll(async () => (await variantOf()) || null, { timeout: 30_000 }).toMatchObject({ language: LANG, name: 'Nom modifie', description: 'Description modifiee' });

  // The editor's own copy of the variants is persisted to the asset, so a reload shows the new values.
  await openEditor(page);
  await badge(page).locator('.brc-edit-variant-btn').click();
  await expect(page.locator('#brc-variant-name')).toHaveValue('Nom modifie');
  await expect(page.locator('#brc-variant-description')).toHaveValue('Description modifiee');
});

test('delete a variant: persisted in the CMS and removed from the open editor', async ({ page }) => {
  await openEditor(page);
  page.once('dialog', (d) => d.accept()); // the "cannot be undone" confirm
  const deleted = page.waitForResponse((r) => r.url().includes('/bin/brightcove/api.json') && (r.request().postData() || '').includes('a=delete_variant'));
  const persisted = jcrPersisted(page);
  await badge(page).locator('.brc-delete-variant-btn').click();
  expect((await deleted).status()).toBe(200);
  expect((await persisted).status()).toBe(200);

  await expect.poll(async () => (await variantOf()) || null, { timeout: 30_000 }).toBeNull();
  await expect(badge(page)).toHaveCount(0);
  await expect(page.locator('.brc-variants-list')).toContainText('No variants configured.');
});

// DEFECT: deleting the LAST variant is not persisted to the DAM asset. The editor mirrors the variant
// list into the asset's brc_variants metadata (persistVariantsToJcr in variant-dialog.js) with a jQuery
// `traditional: true` POST; an empty array serialises to no `brc_variants` parameter at all, so the
// Sling POST carries only `brc_variants@TypeHint` and the stored list keeps the deleted variant.
// Measured on the AEM SDK: after the CMS delete the asset still held the "fr" JSON, and a reload
// showed the "fr" badge again although the video has no variant. Same class as the playlist
// clearVideos guard in epSavePlaylist (brcUI.js). Depends on the previous test having deleted the
// only variant. Flip to `test` once an empty list is written (e.g. brc_variants@Delete).
test.fixme('delete a variant: a reload of the editor no longer lists it', async ({ page }) => {
  await openEditor(page);
  await expect(badge(page)).toHaveCount(0);
  await expect(page.locator('.brc-variants-list')).toContainText('No variants configured.');
});
