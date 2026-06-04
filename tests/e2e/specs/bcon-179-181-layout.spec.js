// BCON-179 + BCON-181 — admin-tool CSS alignment.
//
// BCON-179: the custom checkbox checkmark was pinned to the top-left
//   (left:3px; top:0) instead of centered. Fixed to a centered anchor.
// BCON-181: at narrow widths the bulk-action buttons shrank and wrapped their
//   labels ("Create"/"Playlist"). Fixed so they keep their single-line size
//   and the bar wraps to a new row instead.
const { test, expect, openAdmin } = require('../fixtures');

test('BCON-179: checkbox checkmark is centered, not top-left', async ({ page }) => {
  await openAdmin(page);
  const box = page.locator('#tbData input[type="checkbox"]').first();
  await box.check();

  const pos = await box.evaluate((el) => {
    const cs = getComputedStyle(el, '::after');
    return { left: cs.left, top: cs.top, transform: cs.transform };
  });
  // The anchor is now the box centre (left:50%/top:50%), resolving to equal
  // px on both axes — where it used to be left:3px / top:0px (top-left).
  expect(pos.left).toBe(pos.top);          // symmetric => centred anchor
  expect(pos.left).not.toBe('3px');
  expect(pos.top).not.toBe('0px');
  // and a translate is applied to centre the tick (not the identity/none).
  expect(pos.transform).not.toBe('none');
});

test('BCON-181: bulk-bar buttons stay single-line at narrow width', async ({ page }) => {
  await openAdmin(page);
  const boxes = page.locator('#tbData input[type="checkbox"]');
  await boxes.nth(0).check();
  await boxes.nth(1).check();
  await expect(page.locator('#bulkActionBar')).toBeVisible();

  // Shrink the viewport (the repro condition).
  await page.setViewportSize({ width: 760, height: 900 });
  await page.waitForTimeout(300);

  // Visible bulk buttons keep their single-line height (34px) — no label wrap.
  const heights = await page.locator('.brc-bulk-bar-btn').evaluateAll((els) =>
    els.filter((e) => e.offsetParent !== null).map((e) => Math.round(e.getBoundingClientRect().height)));
  expect(heights.length).toBeGreaterThanOrEqual(2); // Create Playlist + Move to Folder
  for (const h of heights) expect(h).toBe(34);

  // Labels do not wrap.
  const nowrap = await page.locator('#bulkCreatePlaylist').evaluate((el) => getComputedStyle(el).whiteSpace);
  expect(nowrap).toBe('nowrap');
});
