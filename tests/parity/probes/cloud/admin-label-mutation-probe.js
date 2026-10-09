// Row 13 — labels: list, apply an EXISTING label to one video, remove it.
// (Creating a NEW label is out of scope per the parity task's hard rule; the
// connector has no delete-label endpoint, so any created label would be
// permanent.) Cheap + reversible per the allowed-mutation list.
//
// Re-read strategy: a=get_videos_with_label searches Brightcove's label
// index, which the connector's own bcon-182 spec documents as laggy
// ("Brightcove's GET /labels index lags") — confirmed here too: it returned
// `{}` (zero matches) immediately after a save the UI toasted as successful.
// So the persisted-state check instead re-fetches the VIDEO'S OWN record via
// the same endpoint the admin tool itself uses on a fresh panel open
// (getVideoAPIURL -> a=search_videos&isID=true&query=<id>), which is a
// direct video lookup, not a search-index read.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

async function fetchVideoById(page, accountId, videoId) {
  const url = `/bin/brightcove/api.json?isID=true&account_id=${accountId}&a=search_videos&query=${videoId}`;
  return page.evaluate(async (u) => {
    const res = await fetch(u, { credentials: 'include' });
    const raw = await res.text();
    let json = null;
    try { json = JSON.parse(raw); } catch (e) { /* leave null */ }
    return { status: res.status, json, raw: raw.slice(0, 2000) };
  }, url);
}

(async () => {
  const AEM_BASE = process.argv[2] || 'http://localhost:4502';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));

  const result = {};
  try {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    const accountId = await page.locator('#selAccount').inputValue();
    await page.locator('#tbData tr').first().click();
    await page.waitForSelector('#tdMeta', { state: 'visible' });
    await page.waitForSelector('#labelInput', { state: 'visible' });
    await page.waitForFunction(() => document.querySelectorAll('#label_list option[value^="/"]').length > 0, null, { timeout: 15000 });

    const videoId = await page.locator('.brc-panel-info-table').evaluate(() => {
      const rows = document.querySelectorAll('.brc-panel-info-table tr');
      for (const r of rows) {
        const tds = r.querySelectorAll('td');
        if (tds.length >= 2 && /video id/i.test(tds[0].textContent)) return tds[1].textContent.trim();
      }
      return null;
    });
    result.videoId = videoId;
    result.accountId = accountId;

    // Baseline: the video's own record, fetched fresh, before any mutation.
    const before = await fetchVideoById(page, accountId, videoId);
    const beforeLabels = before.json && before.json.items && before.json.items[0] ? (before.json.items[0].labels || []) : null;
    result.beforeLabels = beforeLabels;

    const currentPillTexts = await page.locator('#divMeta\\.labels .brc-label-pill').allInnerTexts();
    const allKnown = await page.locator('#label_list option[value^="/"]').evaluateAll((els) => els.map((e) => e.value));
    const unappliedLabel = allKnown.find((l) => !currentPillTexts.some((t) => t.includes(l)));
    result.currentPillTexts = currentPillTexts;
    result.chosenLabel = unappliedLabel;

    if (!unappliedLabel) {
      result.skipped = 'no unapplied existing label found to test with';
    } else {
      const bareName = unappliedLabel.replace(/^\/+|\/+$/g, '');

      // ---- Apply ----
      await page.locator('#labelInput').fill(bareName);
      await page.locator('#labelInput').press('Enter');
      await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-pill-added.png'), fullPage: true });
      const saveReq1 = page.waitForRequest((r) => r.url().includes('a=update_labels'), { timeout: 10000 });
      await page.locator('#saveLabelsBtn').click();
      const req1 = await saveReq1;
      result.updateLabelsRequest1 = decodeURIComponent(req1.url());
      await page.waitForTimeout(1500);
      result.toastAfterApply = await page.locator('#brcToast .brc-toast-msg').textContent().catch(() => null);

      const afterApply = await fetchVideoById(page, accountId, videoId);
      fs.writeFileSync(path.join(OUT_DIR, 'row13-api-after-apply.json'), JSON.stringify(afterApply, null, 2));
      const afterApplyLabels = afterApply.json && afterApply.json.items && afterApply.json.items[0] ? (afterApply.json.items[0].labels || []) : null;
      result.afterApplyLabels = afterApplyLabels;
      result.appliedConfirmedByApi = Array.isArray(afterApplyLabels) && afterApplyLabels.includes(unappliedLabel);

      // ---- Remove ----
      await page.locator('#divMeta\\.labels .brc-label-pill', { hasText: unappliedLabel }).locator('.brc-label-pill-remove').click();
      await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-pill-removed.png'), fullPage: true });
      const saveReq2 = page.waitForRequest((r) => r.url().includes('a=update_labels'), { timeout: 10000 });
      await page.locator('#saveLabelsBtn').click();
      const req2 = await saveReq2;
      result.updateLabelsRequest2 = decodeURIComponent(req2.url());
      await page.waitForTimeout(1500);
      result.toastAfterRemove = await page.locator('#brcToast .brc-toast-msg').textContent().catch(() => null);

      const afterRemove = await fetchVideoById(page, accountId, videoId);
      fs.writeFileSync(path.join(OUT_DIR, 'row13-api-after-remove.json'), JSON.stringify(afterRemove, null, 2));
      const afterRemoveLabels = afterRemove.json && afterRemove.json.items && afterRemove.json.items[0] ? (afterRemove.json.items[0].labels || []) : null;
      result.afterRemoveLabels = afterRemoveLabels;
      result.removedConfirmedByApi = Array.isArray(afterRemoveLabels) ? !afterRemoveLabels.includes(unappliedLabel) : true;
    }
  } catch (err) {
    result.error = String(err && err.stack || err);
  }

  result.consoleErrors = consoleErrors;
  result.pageErrors = pageErrors;
  fs.writeFileSync(path.join(OUT_DIR, 'row13-label-mutation-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((err) => { console.error(err); process.exit(1); });
