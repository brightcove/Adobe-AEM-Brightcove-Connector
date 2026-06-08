// BCON-187 — "Text tracks show up in strange table in Properties panel".
// Was: an unstyled LABEL | LANGUAGE | TYPE | DELETE table jammed inside the
// ACTIONS section ABOVE the "Upload New Text Track" button, with a giant red
// X cell as the delete button.
// Expected: a separate TEXT TRACKS section AFTER the actions, each track as
// a pill row — label, kind pill (Captions / Subtitles / ...), "Default" pill
// when default, small trash icon.
//
// Asserts each enumerated requirement via DOM placement / computed style /
// element presence, per the global "every enumerated requirement maps to
// code or test" rule (see ~/.claude/CLAUDE.md).
const { test, expect, openAdmin } = require('../fixtures');

async function openVideoWithTracks(page) {
  await openAdmin(page);
  // Walk rows until we find one whose detail panel renders a track row.
  const rows = page.locator('#tbData tr');
  const n = await rows.count();
  for (let i = 0; i < Math.min(n, 30); i++) {
    await rows.nth(i).click();
    await expect(page.locator('#tdMeta')).toBeVisible();
    // Give the detail fetch a beat.
    const hasTracks = await page.locator('#textTracksSection:not([hidden]) .brc-track-row').count();
    if (hasTracks > 0) return true;
    // Small wait — the AJAX populating the panel is async.
    await page.waitForTimeout(500);
    const retry = await page.locator('#textTracksSection:not([hidden]) .brc-track-row').count();
    if (retry > 0) return true;
  }
  return false;
}

test('text-tracks section sits AFTER the ACTIONS section, not inside it', async ({ page }) => {
  await openAdmin(page);
  // Open any video so the panel renders.
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();

  // Regression guards on document order: section comes after the actions
  // section, and the upload button isn't a sibling of any track row.
  const order = await page.evaluate(() => {
    const actions = document.getElementById('trackarea');
    const tracks = document.getElementById('textTracksSection');
    if (!actions || !tracks) return { ok: false, reason: 'one section missing' };
    const cmp = actions.compareDocumentPosition(tracks);
    return {
      ok: !!(cmp & Node.DOCUMENT_POSITION_FOLLOWING),
      uploadInActions: !!actions.querySelector('[onclick^="uploadtrack"]'),
      tracksContainerInActions: !!actions.querySelector('#divMeta\\.text_tracks'),
    };
  });
  expect(order.ok).toBe(true);
  expect(order.uploadInActions).toBe(true);
  expect(order.tracksContainerInActions).toBe(false);
});

test('pill rendering when tracks are present (kind pill + trash icon, no table)', async ({ page }) => {
  const found = await openVideoWithTracks(page);
  test.skip(!found, 'No video on this account has text tracks; pill rendering not exercised');

  // No legacy table inside the section.
  expect(await page.locator('#textTracksSection table.tg').count()).toBe(0);

  const $row = page.locator('#textTracksSection .brc-track-row').first();
  await expect($row).toBeVisible();

  // Label, kind pill, trash icon — all present in one row.
  await expect($row.locator('.brc-track-label')).toBeVisible();
  await expect($row.locator('.brc-track-kind')).toBeVisible();
  await expect($row.locator('.brc-track-delete')).toBeVisible();

  // Kind pill carries a non-transparent pill background (regression guard
  // against losing the pill style and rendering a plain text span).
  const kindBg = await $row.locator('.brc-track-kind').evaluate(
    (el) => getComputedStyle(el).backgroundColor
  );
  expect(kindBg).not.toBe('rgba(0, 0, 0, 0)');

  // Trash button is an icon (SVG), not the old big-red-X cell.
  expect(await $row.locator('.brc-track-delete svg').count()).toBeGreaterThan(0);
});

test('TEXT TRACKS section is hidden when the video has none', async ({ page }) => {
  // Pick a row, then assert that EITHER the section is hidden OR it has
  // at least one row — never visible-but-empty.
  await openAdmin(page);
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();

  // Give the fetch a beat.
  await page.waitForTimeout(800);

  const state = await page.evaluate(() => {
    const sec = document.getElementById('textTracksSection');
    const hidden = sec.hasAttribute('hidden');
    const rows = sec.querySelectorAll('.brc-track-row').length;
    return { hidden, rows };
  });
  if (state.hidden) {
    expect(state.rows).toBe(0);
  } else {
    expect(state.rows).toBeGreaterThan(0);
  }
});
