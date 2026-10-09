const path = require('path');
const { chromium } = require('playwright');
const guards = require('../lib/guards');
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');

async function main() {
  // Guards (../lib/guards.js): loopback AEM only, and the target video must be named
  // e2e-throwaway-*. This probe REMOVES a label and clicks Update, so it mutates.
  const BASE = 'http://localhost:4602';
  guards.assertLocalTarget(BASE, { allowRemote: guards.cli().allowRemote });
  const TARGET_VIDEO_ID = process.env.PARITY_VIDEO_ID_LABELS || (() => { throw new guards.GuardError('set PARITY_VIDEO_ID_LABELS to a throwaway video that has a label'); })();
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE });
  const page = await context.newPage();
  const captured = [];
  page.on('request', (req) => {
    if (req.url().includes('api.js') && req.url().includes('update_labels')) captured.push(req.url());
  });

  const acct = (await (await page.request.get('/bin/brightcove/accounts')).json()).accounts[0].value;
  const res = await page.request.get(`/bin/brightcove/api.js?account_id=${acct}&a=search_videos&callback=cb&start=0&limit=100&sort=&query=`);
  const items = JSON.parse((await res.text()).replace(/^cb\(/, '').replace(/\);?$/, '')).items || [];
  const targetVideo = items.find((v) => v.id === TARGET_VIDEO_ID);
  guards.assertMutable('video', { id: TARGET_VIDEO_ID, name: targetVideo && targetVideo.name });

  await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20000 });
  await page.fill('#search', targetVideo.name);
  await page.click('#searchDiv #searchBut');
  await page.waitForTimeout(1200);
  await page.locator('#tbData tr', { hasText: targetVideo.name }).first().click();
  await page.waitForTimeout(500);
  await page.click('a:has-text("Edit")');
  await page.waitForTimeout(400);
  await page.locator('.label-listing li a').first().click();
  await page.waitForTimeout(300);
  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary:has-text("Update")').click();
  await page.waitForTimeout(1500);
  console.log('captured update_labels requests:', JSON.stringify(captured, null, 2));
  await context.close();
  await browser.close();
  // A capture, not a state check: it passes only if the update_labels request was actually seen.
  guards.finish([{ name: 'update_labels request captured', ok: captured.length > 0 }]);
}
main().catch(guards.fatal);
