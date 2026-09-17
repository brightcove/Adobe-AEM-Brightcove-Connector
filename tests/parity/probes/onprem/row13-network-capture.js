const path = require('path');
const { chromium } = require('playwright');
const AUTH_STATE = path.join(__dirname, '../../../e2e/.auth/state-localhost-4602.json');

async function main() {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState: AUTH_STATE, baseURL: 'http://localhost:4602' });
  const page = await context.newPage();
  const captured = [];
  page.on('request', (req) => {
    if (req.url().includes('api.js') && req.url().includes('update_labels')) captured.push(req.url());
  });

  await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20000 });
  await page.fill('#search', '42min');
  await page.click('#searchDiv #searchBut');
  await page.waitForTimeout(1200);
  await page.locator('#tbData tr', { hasText: '42min - sync' }).first().click();
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
}
main().catch(e => { console.error(e); process.exit(1); });
