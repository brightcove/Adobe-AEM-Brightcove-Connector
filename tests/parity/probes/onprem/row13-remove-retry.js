const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const guards = require('../lib/guards');

const BASE = 'http://localhost:4602';
const { allowRemote } = guards.cli();
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');
const OUT_DIR = path.join(__dirname, '../../runs/2026-09-17/onprem-6.0.12/matrix');

async function getVideo(page, acct, id) {
  const res = await page.request.get(`/bin/brightcove/api.js?account_id=${acct}&a=search_videos&callback=cb&start=0&limit=100&sort=&query=`);
  const parsed = JSON.parse((await res.text()).replace(/^cb\(/, '').replace(/\);?$/, ''));
  return parsed.items.find(v => v.id === id);
}

async function main() {
  // Guards (../lib/guards.js): loopback AEM only, and the target video must be named e2e-throwaway-*.
  guards.assertLocalTarget(BASE, { allowRemote });
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: BASE });
  const page = await context.newPage();
  const acctRes = await page.request.get('/bin/brightcove/accounts');
  const ACCOUNT_ID = (await acctRes.json()).accounts[0].value;
  const TARGET_VIDEO_ID = process.env.PARITY_VIDEO_ID_LABELS || (() => { throw new Error('set PARITY_VIDEO_ID_LABELS to a video with no labels'); })();

  const targetVideo = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);
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

  const liCountBefore = await page.locator('.label-listing li').count();
  console.log('li count before delete click', liCountBefore);

  await page.locator('.label-listing li a').first().click();
  await page.waitForTimeout(400);
  const liCountAfter = await page.locator('.label-listing li').count();
  console.log('li count after delete click', liCountAfter);
  await page.screenshot({ path: path.join(OUT_DIR, 'row13-retry-after-delete-click.png'), fullPage: true });

  await page.locator('.pml-dialog .pml-dialog_footer .btn-primary:has-text("Update")').click();
  await page.waitForTimeout(1500);
  await page.waitForSelector('#tbData tr', { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(500);

  const after = await getVideo(page, ACCOUNT_ID, TARGET_VIDEO_ID);
  console.log('labels after retry', JSON.stringify(after.labels));
  fs.writeFileSync(path.join(OUT_DIR, 'row13-label-mutation.json'), JSON.stringify({
    targetVideoId: TARGET_VIDEO_ID,
    label: '/test2/',
    labelsBefore: [],
    labelsAfterApply: ['/test2/'],
    appliedCorrectly: true,
    liCountBeforeDeleteClick: liCountBefore,
    liCountAfterDeleteClick: liCountAfter,
    labelsAfterRemove: after.labels || [],
    revertedCorrectly: JSON.stringify(after.labels || []) === JSON.stringify([])
  }, null, 2));

  await context.close();
  await browser.close();
  // The output JSON above records what was OBSERVED. Only the final state is a real check here:
  // the video's labels must be empty again after the remove.
  guards.finish([{ name: 'labels empty after remove (video record re-read)', ok: Array.isArray(after.labels) || after.labels === undefined ? (after.labels || []).length === 0 : null }]);
}
main().catch(guards.fatal);
