// BCON-175 — "Video Panel Visual Issues".
// Ticket enumerates seven distinct visual requirements vs. the Figma:
//   1. Properties panel has a border dividing it from the list section
//   2. Header (Video Name + Updated date) has a grey background
//   3. Image widgets stack vertically (not side-by-side)
//   4. Image buttons say "Enter URL" (not "Change URL")
//   5. Video Info section has an outer border
//   6. Video Info value cells are right-justified
//   7. Economics renders as a bubble/pill around its value
//
// This spec asserts each item via computed style / DOM so a future regression
// (or a half-fix that ships under the BCON-175 ticket) gets caught at CI time
// instead of by hand. Authored to close the verification gap that let the
// original mis-attributed fix slip into PR #108 / Ready-for-QA.
const { test, expect, openAdmin } = require('../fixtures');

test('BCON-175: Properties panel matches Figma visual spec', async ({ page }) => {
  await openAdmin(page);

  // Click the first video row to open the detail panel.
  await page.locator('#tbData tr').first().click();
  await expect(page.locator('#tdMeta')).toBeVisible();
  await page.waitForSelector('.brc-panel-inner', { state: 'visible' });

  // -------- 1. Panel divider: left-only --------
  // commit 1239c07 (BCON-175/BCON-184) narrowed the outer divider to the left
  // side only: a full border put a stray vertical bar down the panel's right
  // edge and doubled the left divider against the list section's own border.
  const innerBorder = await page.locator('.brc-panel-inner').evaluate((el) => {
    const cs = getComputedStyle(el);
    return {
      leftWidth: cs.borderLeftWidth,
      leftStyle: cs.borderLeftStyle,
      leftColor: cs.borderLeftColor,
      topWidth: cs.borderTopWidth,
      topStyle: cs.borderTopStyle,
      rightWidth: cs.borderRightWidth,
      rightStyle: cs.borderRightStyle,
      bottomWidth: cs.borderBottomWidth,
      bottomStyle: cs.borderBottomStyle,
    };
  });
  expect(parseFloat(innerBorder.leftWidth)).toBeGreaterThan(0);
  expect(innerBorder.leftStyle).not.toBe('none');
  // Inverse assertion: the removed full border must not have come back.
  expect(parseFloat(innerBorder.topWidth) || 0).toBe(0);
  expect(parseFloat(innerBorder.rightWidth) || 0).toBe(0);
  expect(parseFloat(innerBorder.bottomWidth) || 0).toBe(0);

  // -------- 2. Header grey background --------
  const headerBg = await page.locator('.brc-panel-header').evaluate(
    (el) => getComputedStyle(el).backgroundColor
  );
  // Must not be transparent or pure white.
  expect(headerBg).not.toBe('rgba(0, 0, 0, 0)');
  expect(headerBg).not.toBe('rgb(255, 255, 255)');

  // -------- 3. Images stack vertically --------
  const flexDir = await page.locator('.brc-panel-images').evaluate(
    (el) => getComputedStyle(el).flexDirection
  );
  expect(flexDir).toBe('column');

  // -------- 4. "Enter URL" text on both image buttons --------
  const urlButtonTexts = await page
    .locator('.brc-image-url-btn')
    .allInnerTexts();
  expect(urlButtonTexts.length).toBeGreaterThanOrEqual(2);
  for (const t of urlButtonTexts) {
    expect(t.trim().toUpperCase()).toBe('ENTER URL');
  }

  // -------- 5. Video Info outer border --------
  const tableBorder = await page.locator('.brc-panel-info-table').evaluate((el) => {
    const cs = getComputedStyle(el);
    return { width: cs.borderTopWidth, style: cs.borderTopStyle };
  });
  expect(parseFloat(tableBorder.width)).toBeGreaterThan(0);
  expect(tableBorder.style).not.toBe('none');

  // -------- 6. Value cells right-justified, label cells left --------
  const alignments = await page
    .locator('.brc-panel-info-table tr')
    .evaluateAll((rows) =>
      rows.map((row) => {
        const cells = row.querySelectorAll('td');
        if (cells.length < 2) return null;
        return {
          label: getComputedStyle(cells[0]).textAlign,
          value: getComputedStyle(cells[1]).textAlign,
        };
      }).filter(Boolean)
    );
  expect(alignments.length).toBeGreaterThan(0);
  for (const row of alignments) {
    expect(row.value).toBe('right');
    expect(row.label).not.toBe('right'); // label stays left
  }

  // -------- 7. Economics pill --------
  // commit 1239c07 (BCON-175) moved the pill styling off the <td> itself
  // (`#divMeta.economics`) onto an inner `.brc-economics-pill` span, because
  // making the cell inline-block pulled it out of the table and broke the row
  // divider (#4/#5). The <td> stays a normal table cell; the pill is the span.
  const economics = await page
    .locator('#divMeta\\.economics .brc-economics-pill')
    .evaluate((el) => ({
      text: (el.textContent || '').trim(),
      bg: getComputedStyle(el).backgroundColor,
      display: getComputedStyle(el).display,
      radius: getComputedStyle(el).borderTopLeftRadius,
    }));
  expect(economics.text.length).toBeGreaterThan(0);
  expect(economics.bg).not.toBe('rgba(0, 0, 0, 0)');
  expect(economics.display).not.toBe('block'); // inline-block, not the default td child
  expect(parseFloat(economics.radius)).toBeGreaterThan(0);
  // Inverse: the <td> itself must NOT be the styled element anymore.
  const tdDisplay = await page
    .locator('#divMeta\\.economics')
    .evaluate((el) => getComputedStyle(el).display);
  expect(tdDisplay).not.toBe('inline-block');
});
