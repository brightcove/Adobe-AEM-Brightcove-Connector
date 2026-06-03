// BCON-183 — "Sorting by ID removes all Videos from the list".
// Clicking the ID column used to issue a server sort=id, which the Brightcove
// CMS video search rejects (returns 0 items), blanking the table. The fix
// sorts the loaded videos client-side by numeric id. This spec asserts the
// list does NOT empty and is actually ordered by id.
const { test, expect, openAdmin } = require('../fixtures');

// Compare Brightcove ids as numbers, but safely (ids can exceed 2^53):
// shorter numeric string = smaller, then lexicographic.
function idCmp(a, b) {
  return a.length !== b.length ? a.length - b.length : (a < b ? -1 : a > b ? 1 : 0);
}

const ID_COL = '#trHeader th[data-sortBy="id"]';

async function idColumnValues(page) {
  // ID is the 5th cell (checkbox, Name, Last Updated, Reference ID, ID).
  return page.$$eval('#tbData tr', (rows) =>
    rows.map((r) => {
      const cells = r.querySelectorAll('td');
      return cells.length >= 5 ? cells[4].textContent.trim() : '';
    }).filter(Boolean));
}

test('sorting by ID orders the videos instead of clearing the list', async ({ page }) => {
  await openAdmin(page);

  const before = await page.locator('#tbData tr').count();
  expect(before).toBeGreaterThan(1);

  // First click on a fresh column sorts DESCENDING (data-sortType "" ->
  // newSortType "ASC" -> switchSort "-").
  await page.locator(ID_COL).click();

  // The list must NOT blank out (the bug) and must keep every row.
  await expect.poll(async () => page.locator('#tbData tr').count(), { timeout: 15_000 })
    .toBeGreaterThan(0);
  expect(await page.locator('#tbData tr').count()).toBe(before);

  // Verify descending numeric-id order.
  const desc = await idColumnValues(page);
  expect(desc).toEqual([...desc].sort((a, b) => idCmp(b, a)));

  // Click again -> ascending; still fully populated and ascending-ordered.
  await page.locator(ID_COL).click();
  await expect.poll(async () => page.locator('#tbData tr').count(), { timeout: 15_000 })
    .toBeGreaterThan(0);
  expect(await page.locator('#tbData tr').count()).toBe(before);
  const asc = await idColumnValues(page);
  expect(asc).toEqual([...asc].sort(idCmp));
});
