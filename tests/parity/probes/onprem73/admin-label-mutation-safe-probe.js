// Row 13 (on-prem 7.3.0 pass) — labels: list, apply an EXISTING label to a
// video, remove it. Same UI flow and same verification method as
// ../cloud/admin-label-mutation-probe.js (this on-prem instance runs the
// same unified 7.3.0 connector code, so the selectors are identical), but
// that cloud probe always targets the FIRST row in the table, and the first
// row on this account happens to have zero labels. Removing a video's LAST
// label is a known silent no-op defect (matrix row 13, §3b#1 in the parity
// plan) — applying then removing on a zero-label video would hit that bug
// and not actually exercise a clean remove.
//
// So this probe takes its target video via PARITY_VIDEO_ID_LABELS (must be a
// video that ALREADY carries at least one label) and only applies+removes an
// ADDITIONAL label, leaving the video's pre-existing label(s) untouched —
// avoiding the last-label no-op entirely rather than triggering it again.
//
// Usage:
//   PARITY_VIDEO_ID_LABELS=<id> NODE_PATH=<bcon-parity>/tests/e2e/node_modules \
//     node admin-label-mutation-safe-probe.js <AEM_BASE> <state.json path> <output dir>
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
  const AEM_BASE = process.argv[2] || 'http://localhost:4602';
  const STATE_PATH = process.argv[3];
  const OUT_DIR = process.argv[4];
  const TARGET_VIDEO_ID = process.env.PARITY_VIDEO_ID_LABELS
    || (() => { throw new Error('set PARITY_VIDEO_ID_LABELS to a video that already has >=1 label'); })();

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: AEM_BASE, storageState: STATE_PATH });
  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e.message || e)));

  const result = { targetVideoId: TARGET_VIDEO_ID };
  try {
    await page.goto('/brightcove/admin.html', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#tbData tr', { timeout: 20000 });
    const accountId = await page.locator('#selAccount').inputValue();
    result.accountId = accountId;

    // Baseline via a fresh, direct video-record read (not the laggy label
    // search index — see cloud probe header comment for why).
    const before = await fetchVideoById(page, accountId, TARGET_VIDEO_ID);
    const preExistingLabels = before.json && before.json.items && before.json.items[0] ? (before.json.items[0].labels || []) : null;
    result.preExistingLabels = preExistingLabels;
    if (!preExistingLabels || preExistingLabels.length === 0) {
      throw new Error(`target video ${TARGET_VIDEO_ID} has zero pre-existing labels — would hit the last-label no-op bug; pick a different PARITY_VIDEO_ID_LABELS`);
    }

    // Find the row for this video by searching for it, then click it.
    await page.fill('#search', TARGET_VIDEO_ID);
    const searchReq = page.waitForRequest((r) => r.url().includes('a=search_videos'), { timeout: 15000 });
    await page.locator('#searchBut').click();
    await searchReq;
    await page.waitForTimeout(1000);
    await page.locator('#tbData tr').first().click();
    await page.waitForSelector('#tdMeta', { state: 'visible' });
    await page.waitForSelector('#labelInput', { state: 'visible' });
    await page.waitForFunction(() => document.querySelectorAll('#label_list option[value^="/"]').length > 0, null, { timeout: 15000 });

    const confirmedId = await page.locator('.brc-panel-info-table').evaluate(() => {
      const rows = document.querySelectorAll('.brc-panel-info-table tr');
      for (const r of rows) {
        const tds = r.querySelectorAll('td');
        if (tds.length >= 2 && /video id/i.test(tds[0].textContent)) return tds[1].textContent.trim();
      }
      return null;
    });
    result.confirmedRowMatchesTarget = confirmedId === TARGET_VIDEO_ID;

    const currentPillTexts = await page.locator('#divMeta\\.labels .brc-label-pill').allInnerTexts();
    const allKnown = await page.locator('#label_list option[value^="/"]').evaluateAll((els) => els.map((e) => e.value));
    const unappliedLabel = allKnown.find((l) => !currentPillTexts.some((t) => t.includes(l)));
    result.currentPillTexts = currentPillTexts;
    result.chosenAdditionalLabel = unappliedLabel;

    if (!unappliedLabel) {
      result.skipped = 'no unapplied existing label found to test with';
    } else {
      const bareName = unappliedLabel.replace(/^\/+|\/+$/g, '');

      // ---- Apply the additional label ----
      await page.locator('#labelInput').fill(bareName);
      await page.locator('#labelInput').press('Enter');
      await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-pill-added.png'), fullPage: true });
      const saveReq1 = page.waitForRequest((r) => r.url().includes('a=update_labels'), { timeout: 10000 });
      await page.locator('#saveLabelsBtn').click();
      const req1 = await saveReq1;
      result.updateLabelsRequest1 = decodeURIComponent(req1.url());
      await page.waitForTimeout(1500);
      result.toastAfterApply = await page.locator('#brcToast .brc-toast-msg').textContent().catch(() => null);

      const afterApply = await fetchVideoById(page, accountId, TARGET_VIDEO_ID);
      fs.writeFileSync(path.join(OUT_DIR, 'row13-api-after-apply.json'), JSON.stringify(afterApply, null, 2));
      const afterApplyLabels = afterApply.json && afterApply.json.items && afterApply.json.items[0] ? (afterApply.json.items[0].labels || []) : null;
      result.afterApplyLabels = afterApplyLabels;
      result.appliedConfirmedByApi = Array.isArray(afterApplyLabels) && afterApplyLabels.includes(unappliedLabel) && afterApplyLabels.length === preExistingLabels.length + 1;

      // ---- Remove ONLY the additional label; pre-existing label(s) stay ----
      await page.locator('#divMeta\\.labels .brc-label-pill', { hasText: unappliedLabel }).locator('.brc-label-pill-remove').click();
      await page.screenshot({ path: path.join(OUT_DIR, 'row13-label-pill-removed.png'), fullPage: true });
      const saveReq2 = page.waitForRequest((r) => r.url().includes('a=update_labels'), { timeout: 10000 });
      await page.locator('#saveLabelsBtn').click();
      const req2 = await saveReq2;
      result.updateLabelsRequest2 = decodeURIComponent(req2.url());
      await page.waitForTimeout(1500);
      result.toastAfterRemove = await page.locator('#brcToast .brc-toast-msg').textContent().catch(() => null);

      const afterRemove = await fetchVideoById(page, accountId, TARGET_VIDEO_ID);
      fs.writeFileSync(path.join(OUT_DIR, 'row13-api-after-remove.json'), JSON.stringify(afterRemove, null, 2));
      const afterRemoveLabels = afterRemove.json && afterRemove.json.items && afterRemove.json.items[0] ? (afterRemove.json.items[0].labels || []) : null;
      result.afterRemoveLabels = afterRemoveLabels;
      result.removedConfirmedByApi = Array.isArray(afterRemoveLabels) && !afterRemoveLabels.includes(unappliedLabel);
      result.preExistingLabelsIntact = Array.isArray(afterRemoveLabels) && JSON.stringify(afterRemoveLabels.slice().sort()) === JSON.stringify(preExistingLabels.slice().sort());
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
