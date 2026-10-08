// Parity row 20: a REAL cross-account switch, driven from a cold start.
// BCON-178's own spec (bcon-178-account-switch-confirm.spec.js) fakes the switch
// on a one-account instance; this one needs a second configured account.
//
// READ-ONLY by construction: it only clicks the account popover, the confirm
// dialog and reads the video list. It never syncs, saves, uploads or edits, and
// the CMS comparison uses only `bc videos list|get`. Do NOT run it alongside
// mutating specs while the second account is configured (run it alone).
//
// Inputs (NOT MEASURED, loudly, when either is missing or the second account is
// not configured on the instance under test; nothing identifying lives in git):
//   BRC_E2E_SECOND_ACCOUNT_ID  the second account's id, as AEM lists it
//   BC_BIN                     path to the Brightcove `bc` CLI (not /usr/bin/bc)
//   BC_FIRST_ACCOUNT_LABEL / BC_SECOND_ACCOUNT_LABEL
//                              the `bc` account LABELS (`bc --account <label>`, registered
//                              with bc-account-bootstrap) of the first and second account.
//                              `bc` resolves a label, not a numeric id.
// The default (first) account must be the one the other specs use; the spec
// asserts it stays first and that the switch round-trips back to it.
const { execFileSync } = require('child_process');
const { test, expect, openAdmin, resolveAccountId } = require('../fixtures');

const SECOND = process.env.BRC_E2E_SECOND_ACCOUNT_ID;
const BC_BIN = process.env.BC_BIN;
const LABEL_FIRST = process.env.BC_FIRST_ACCOUNT_LABEL;
const LABEL_SECOND = process.env.BC_SECOND_ACCOUNT_LABEL;

function bc(label, args) {
  let out;
  try {
    out = execFileSync(BC_BIN, ['--account', label, ...args], { encoding: 'utf8', timeout: 60_000 });
  } catch (e) {
    out = `${e.stdout || ''}${e.stderr || ''}`; // bc prints error JSON on a non-zero exit
    if (!out.includes('{')) throw new Error(`bc ${args.join(' ')} failed: ${e.message}`);
  }
  return JSON.parse(out.slice(out.indexOf('{')));
}
// true / false for a CMS 200 / NOT_FOUND; anything else is an error, not a "no".
function bcHasVideo(label, id) {
  const r = bc(label, ['videos', 'get', id]);
  if (r.error) {
    if (r.code === 'NOT_FOUND') return false;
    throw new Error(`bc videos get ${id} on ${label}: ${r.code}`);
  }
  return String(r.data.video.id) === id;
}

async function rowIds(page) {
  await page.waitForSelector('#tbData tr', { timeout: 30_000 });
  return page.evaluate(() => oCurrentVideoList.map((v) => String(v.id)));
}

async function switchTo(page, accountId) {
  await page.locator('#accountTrigger').click();
  await expect(page.locator('#accountPopover')).toBeVisible();
  const row = page.locator(`.brc-account-row[data-account-id="${accountId}"]`);
  const alias = await row.getAttribute('data-account-alias');
  await row.click(); // a real click on the real row: no is-active bypass
  const dialog = page.locator('.pml-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.pml-dialog_content')).toContainText(`Switch to "${alias}"`);
  await Promise.all([
    page.waitForLoadState('load'),
    dialog.locator('.pml-dialog_footer .btn-primary').click(),
  ]);
  await expect(page.locator('#accountTrigger .brc-account-name')).toHaveText(alias, { timeout: 20_000 });
  return alias;
}

test('row 20: switch to a second account, confirm, list comes from it, switch back', async ({ page, request }) => {
  test.skip(!SECOND || !BC_BIN || !LABEL_FIRST || !LABEL_SECOND, 'NOT MEASURED: BRC_E2E_SECOND_ACCOUNT_ID, BC_BIN, BC_FIRST_ACCOUNT_LABEL and BC_SECOND_ACCOUNT_LABEL are not all set');
  const accounts = (await (await request.get('/bin/brightcove/accounts')).json()).accounts || [];
  test.skip(!accounts.some((a) => String(a.value) === SECOND), 'NOT MEASURED: the second account is not configured on this AEM instance');

  const first = await resolveAccountId(request);
  expect(first, 'the default (first) account must not be the second account').not.toBe(SECOND);

  // Cold start: no brc_act cookie, so the admin tool opens on the first account.
  await openAdmin(page);
  const startAlias = await page.locator('#accountTrigger .brc-account-name').textContent();
  expect(await page.locator('#selAccount').inputValue()).toBe(first);
  const firstIds = await rowIds(page);
  expect(firstIds.length).toBeGreaterThan(0);

  // Switch to the second account through the confirm dialog.
  await switchTo(page, SECOND);
  expect(await page.locator('#selAccount').inputValue()).toBe(SECOND);
  const secondIds = await rowIds(page);
  expect(secondIds.length).toBeGreaterThan(0);
  expect(secondIds.filter((id) => firstIds.includes(id)), 'the list did not change after switching').toEqual([]);
  // Independent read-only check against CMS: every listed video belongs to the second account.
  for (const id of secondIds.slice(0, 5)) {
    expect(bcHasVideo(LABEL_SECOND, id), `video ${id} shown after the switch is not in the second account`).toBe(true);
  }
  expect(bcHasVideo(LABEL_FIRST, secondIds[0]), 'the second account\'s video must not exist in the first').toBe(false);

  // Switch back: the list is the first account's again.
  await switchTo(page, first);
  expect(await page.locator('#accountTrigger .brc-account-name').textContent()).toBe(startAlias);
  expect(await page.locator('#selAccount').inputValue()).toBe(first);
  expect(await rowIds(page), 'the first account list did not come back').toEqual(firstIds);
});
