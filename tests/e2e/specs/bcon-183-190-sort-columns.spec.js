// BCON-183 — "Sorting by ID removes all Videos from the list".
// Clicking the ID column used to issue a server sort=id, which the Brightcove
// CMS video search rejects (returns 0 items), blanking the table. The
// original fix (client-side id sort) only reordered the current page, never
// all pages of a multi-page account.
//
// BCON-190 (commit 9608e11, 2026-06-10) replaced that client-side sort with
// removing ID-column sorting entirely: the CMS API's rejection of sort=id is
// a hard constraint, so a sort that silently only covers the current page was
// judged more misleading than no sort at all. The ID header is now a plain,
// non-clickable `<th>` with no `data-sortBy`/`onclick`. Name, Last Updated,
// and Reference ID remain sortable via the server (`getAllVideosURLOrdered`).
//
// This spec verifies both halves: the ID column is gone as a sort control,
// and another supported column still sorts without ever clearing the list.
const { test, expect, openAdmin } = require('../fixtures');

test('the ID column is not sortable', async ({ page }) => {
  await openAdmin(page);

  // No header carries data-sortBy="id" anywhere in the table.
  await expect(page.locator('#trHeader th[data-sortBy="id"]')).toHaveCount(0);

  // If an ID header is still rendered (label only), it must not be wired as
  // a sort control: no onclick handler, no sortable/ASC/DESC/NONE classes.
  // Exact-text match: "Reference ID" also contains the substring "ID".
  const idHeader = page.locator('#trHeader th').filter({ hasText: /^ID$/ });
  await expect(idHeader).toHaveCount(1);
  const idHeaderState = await idHeader.evaluate((el) => ({
    hasOnclick: !!el.getAttribute('onclick'),
    classList: Array.from(el.classList),
  }));
  expect(idHeaderState.hasOnclick).toBe(false);
  expect(idHeaderState.classList).not.toContain('sortable');
});

test('sorting by another supported column (Name) reorders without clearing the list', async ({ page }) => {
  await openAdmin(page);

  const before = await page.locator('#tbData tr').count();
  expect(before).toBeGreaterThan(1);

  const nameColumnValues = async () =>
    page.$$eval('#tbData tr', (rows) =>
      rows.map((r) => {
        const cells = r.querySelectorAll('td');
        // checkbox, Name, Last Updated, Reference ID, ID
        return cells.length >= 2 ? cells[1].textContent.trim() : '';
      }).filter(Boolean));

  // Server default (Load(getAllVideosURL()) -> sort=name, ascending).
  const initialAsc = await nameColumnValues();

  // Name (#nameCol) starts ASC; click toggles to DESC via a fresh server
  // request (getAllVideosURLOrdered("name", "-")). The account has more
  // videos (54) than one page (30), so a DESC page-1 is NOT simply the
  // reverse of an ASC page-1 (they're different 30-item windows over the
  // same 54-item order) — assert a real reorder happened and nothing was
  // cleared, rather than assuming a specific string-collation algorithm.
  // The row count stays at 30 through the whole round trip (it's an
  // AJAX-swapped page, not a client-side reorder), so polling the count
  // alone can't detect the swap. Poll the actual row content instead.
  await page.locator('#nameCol').click();
  await expect.poll(async () => JSON.stringify(await nameColumnValues()), { timeout: 15_000 })
    .not.toBe(JSON.stringify(initialAsc));
  expect(await page.locator('#tbData tr').count()).toBe(before);
  const desc = await nameColumnValues();
  expect(desc.length).toBeGreaterThan(0);

  // Click again -> back to ASC. The sort is a deterministic server request,
  // so it must round-trip to the exact original order (and, again, not clear
  // the list — the historical BCON-183 bug for the ID column).
  await page.locator('#nameCol').click();
  await expect.poll(async () => JSON.stringify(await nameColumnValues()), { timeout: 15_000 })
    .toBe(JSON.stringify(initialAsc));
  expect(await page.locator('#tbData tr').count()).toBe(before);
});
