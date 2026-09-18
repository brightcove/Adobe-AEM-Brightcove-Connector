// Sites editor: the connector's three page components and the Content Finder
// source, driven the way an author uses them. Parity matrix rows 23-27
// (tests/parity/matrix.md) were measured with one-off probes until 2026-09-18;
// this spec makes them a standing check on both platforms.
//
// Covered here:
//   - rows 23-25: Video Player, Playlist Player and Experiences place and render
//     their authoring placeholders; the Configure dialogs open with the account
//     pre-populated and their pickers are fed by the live account.
//   - row 26 / BGS-1690: saving a component dialog refreshes that component in
//     place (REFRESH_SELF): the content iframe is not torn down, the editor's
//     scroll position survives, and the new embed markup bootstraps a player
//     (the MutationObserver in BrightcoveExperiences.js) without a page reload.
//   - row 27: the Assets rail's "Brightcove Videos" source lists the account's
//     synced DAM assets through the connector's asset-path servlet.
//   - a source guard on every component's edit config, so a REFRESH_PAGE
//     regression fails by name rather than as a mysterious scroll jump.
//
// The page under test is created per run (fixtures.js createSitesTestPage) and
// deleted afterwards, so the shared bgs-test-page fixture, whose mid player
// references a video that no longer plays, is never what this spec measures.
const fs = require('fs');
const path = require('path');
const {
  test, expect, resolveAccountId, firstVideos, createSitesTestPage, deletePage, editorScrollTop,
} = require('../fixtures');

test.describe.configure({ mode: 'serial' });

const RUN = `e2e-sites-${Date.now().toString(36)}`;
let PAGE_PATH;
let ACCOUNT_ID;
const EDITOR = () => `/editor.html${PAGE_PATH}.html`;

test.beforeAll(async ({ request }) => {
  ACCOUNT_ID = await resolveAccountId(request);
  PAGE_PATH = await createSitesTestPage(request, RUN);
});

test.afterAll(async ({ request }) => {
  if (PAGE_PATH) await deletePage(request, PAGE_PATH);
});

// The page iframe inside the editor shell.
function contentFrame(page) {
  const f = page.frames().find((fr) => fr !== page.mainFrame() && fr.url().includes(PAGE_PATH));
  if (!f) throw new Error(`content frame for ${PAGE_PATH} not found; frames: ${page.frames().map((x) => x.url()).join(', ')}`);
  return f;
}

async function openEditor(page) {
  await page.goto(EDITOR(), { waitUntil: 'load', timeout: 60_000 });
  // The overlays are laid out after the content frame has rendered.
  await expect(page.locator('.cq-Overlay[data-path$="brightcove_player_top"]')).toBeAttached({ timeout: 30_000 });
  await page.waitForTimeout(1500);
  return contentFrame(page);
}

// Select a component overlay and open its Configure dialog.
async function openConfigure(page, node) {
  const overlay = page.locator(`.cq-Overlay[data-path$="${node}"]`).first();
  await overlay.scrollIntoViewIfNeeded();
  await overlay.click();
  const configure = page.locator('button[data-action="CONFIGURE" i]').first();
  await expect(configure, `no Configure action for ${node}`).toBeVisible({ timeout: 10_000 });
  await configure.click();
  const dialog = page.locator('coral-dialog[open]').first();
  await expect(dialog).toBeVisible({ timeout: 15_000 });
  await page.waitForTimeout(1200); // dialog scripts populate the account/playlist selects
  return dialog;
}

// Coral select: open it and click the item, the way an author does.
async function chooseSelectItem(page, select, matcher) {
  await select.locator('button').first().click();
  const item = page.locator('coral-selectlist-item:visible', matcher).first();
  await expect(item, 'select item not offered').toBeVisible({ timeout: 10_000 });
  const value = await item.getAttribute('value');
  await item.click();
  return value;
}

function armErrorCollectors(page) {
  const errors = [];
  page.on('pageerror', (e) => errors.push(`[page] ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(`[console] ${m.text()}`); });
  return errors;
}

const connectorErrors = (errors) => errors.filter((e) => /brightcove|brc\.|videojs|video\.js|reading 'options'|null#trigger|Load is not defined/i.test(e));

test('rows 23-25: all three components place and render their authoring placeholders', async ({ page }) => {
  const errors = armErrorCollectors(page);
  const frame = await openEditor(page);

  for (const node of ['brightcove_player_top', 'brightcove_playlist', 'brightcove_player_mid', 'brightcove_experiences']) {
    expect(await page.locator(`.cq-Overlay[data-path$="${node}"]`).count(), `${node} has no editor overlay`).toBe(1);
  }
  // Unconfigured components render the "Configure ..." placeholder, one per component.
  const placeholders = await frame.locator('.cq-Overlay--placeholder').allInnerTexts();
  expect(placeholders.filter((t) => /Brightcove Video Player/.test(t)).length, 'two Video Player placeholders').toBe(2);
  expect(placeholders.filter((t) => /Playlist/.test(t)).length, 'one Playlist Player placeholder').toBe(1);
  expect(placeholders.filter((t) => /Experience/.test(t)).length, 'one Experiences placeholder').toBe(1);

  expect(connectorErrors(errors), 'connector JS errors on an unconfigured page').toEqual([]);
});

test('row 23 + BGS-1690: Video Player dialog picks a live video and the component refreshes in place', async ({ page, request }) => {
  const [video] = await firstVideos(request, 1);
  const errors = armErrorCollectors(page);
  const frame = await openEditor(page);

  // A frame-scoped marker: it survives an in-place refresh and dies with a reload.
  await frame.evaluate(() => { window.__brcE2eMarker = 'kept'; });

  // Put the mid player in view by scrolling the editor's own container, so the
  // save below has a non-zero position to preserve.
  const midOverlay = page.locator('.cq-Overlay[data-path$="brightcove_player_mid"]').first();
  await midOverlay.scrollIntoViewIfNeeded();
  const scrollBefore = await editorScrollTop(page);
  expect(scrollBefore, 'the mid player should sit below the fold; the fixture page is not tall enough').toBeGreaterThan(0);
  const dialog = await openConfigure(page, 'brightcove_player_mid');

  // Account pre-populated with the configured account.
  const account = dialog.locator('coral-select[name="./account"]');
  await expect.poll(() => account.evaluate((e) => e.value), { timeout: 10_000 }).toBe(ACCOUNT_ID);

  // Type a word from a real video's name into the autocomplete and pick the
  // first suggestion the connector offers. The CMS search decides which videos
  // match the term, so the picked video is whatever it returned, and its id is
  // read back from the suggestion rather than assumed.
  const input = dialog.locator('input[name="./videoPlayer"]');
  const term = (video.name.match(/[A-Za-z0-9]{3,}/) || ['video'])[0];
  const suggestions = page.waitForResponse((r) => r.url().includes('/bin/brightcove/api.jsx') && r.url().includes('a=search_videos'), { timeout: 20_000 });
  await input.click();
  await input.fill('');
  await input.pressSequentially(term, { delay: 40 });
  expect((await suggestions).ok(), 'video suggestions request failed').toBeTruthy();
  const option = dialog.locator('.coral-SelectList-item--option[data-value*="["]').first();
  await expect(option, `no video suggestion offered for "${term}"`).toBeVisible({ timeout: 10_000 });
  const pickedValue = await option.getAttribute('data-value');
  const picked = { id: /\[(\d+)\]\s*$/.exec(pickedValue)[1], value: pickedValue };
  await option.click();
  await expect.poll(() => input.inputValue()).toContain(`[${picked.id}]`);

  // Granite dialogs POST to the component path with jcr:content encoded as
  // _jcr_content, so match on the node name rather than the literal path.
  const saved = page.waitForResponse((r) => r.url().includes('brightcove_player_mid') && r.request().method() === 'POST', { timeout: 20_000 });
  await dialog.locator('button.cq-dialog-submit').first().click();
  expect((await saved).ok(), 'dialog POST failed').toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 10_000 });

  // 1. Persisted state, re-read: what the author sees after a refresh.
  const props = await (await request.get(`${PAGE_PATH}/jcr:content/root/responsivegrid/brightcove_player_mid.json`)).json();
  expect(props.videoPlayer, 'videoPlayer not persisted').toContain(`[${picked.id}]`);
  expect(props.account, 'account not persisted').toBe(ACCOUNT_ID);

  // 2. BGS-1690: REFRESH_SELF, not a page reload. The original frame is still
  //    alive and its marker is intact.
  expect(frame.isDetached(), 'content frame was torn down: the dialog save reloaded the page').toBe(false);
  expect(await frame.evaluate(() => window.__brcE2eMarker)).toBe('kept');
  const scrollAfter = await editorScrollTop(page);
  expect(Math.abs(scrollAfter - scrollBefore), `scroll jumped from ${scrollBefore} to ${scrollAfter}`).toBeLessThan(50);

  // 3. The component re-rendered with the new embed, and the player bootstrapped
  //    without DOMContentLoaded (the MutationObserver path BGS-1690 added).
  const container = frame.locator(`.brightcove-container[data-video-id="${picked.id}"]`);
  await expect(container, 'no embed markup for the picked video after save').toBeAttached({ timeout: 20_000 });
  await expect(container.locator('video.vjs-tech').first(), 'video.js did not bootstrap the refreshed container').toBeAttached({ timeout: 30_000 });

  expect(connectorErrors(errors), 'connector JS errors during a healthy configure + refresh').toEqual([]);
});

test('row 24: Playlist Player dialog lists the account playlists and saves the chosen one', async ({ page, request }) => {
  const frame = await openEditor(page);
  const dialog = await openConfigure(page, 'brightcove_playlist');

  const account = dialog.locator('coral-select[name="./account"]');
  await expect.poll(() => account.evaluate((e) => e.value), { timeout: 10_000 }).toBe(ACCOUNT_ID);

  const playlistSelect = dialog.locator('coral-select[name="./videoPlayerPL"]');
  await expect.poll(() => playlistSelect.evaluate((e) => (e.items ? e.items.getAll().length : 0)), { timeout: 15_000 }).toBeGreaterThan(0);
  const chosen = await chooseSelectItem(page, playlistSelect, { hasText: /\S/ });
  expect(chosen, 'the chosen playlist item has no value').toMatch(/\S/);

  const saved = page.waitForResponse((r) => r.url().includes('brightcove_playlist') && r.request().method() === 'POST', { timeout: 20_000 });
  await dialog.locator('button.cq-dialog-submit').first().click();
  expect((await saved).ok()).toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 10_000 });

  const props = await (await request.get(`${PAGE_PATH}/jcr:content/root/responsivegrid/brightcove_playlist.json`)).json();
  expect(props.videoPlayerPL, 'playlist not persisted').toBe(chosen);
  expect(frame.isDetached(), 'playlist save reloaded the page').toBe(false);

  // The playlist embed renders with the chosen playlist id in place.
  const m = /\[(.*?)\]/.exec(chosen);
  const playlistId = m ? m[1] : chosen;
  // The playlist player mounts in place too: video.js's <video> carries the id.
  await expect(frame.locator(`.brightcove-container video.vjs-tech[data-playlist-id="${playlistId}"]`).first(), 'no mounted playlist player for the chosen playlist').toBeAttached({ timeout: 30_000 });
});

test('row 25: Experiences dialog opens with the account and its picker queries the live account', async ({ page }) => {
  await openEditor(page);
  const dialog = await openConfigure(page, 'brightcove_experiences');

  const account = dialog.locator('coral-select[name="./account"]');
  await expect.poll(() => account.evaluate((e) => e.value), { timeout: 10_000 }).toBe(ACCOUNT_ID);

  // Legacy (Coral 2) autocomplete: the named input is hidden and the author
  // types into the widget's text field next to it.
  const widget = dialog.locator('.coral-Autocomplete, coral-autocomplete').filter({ has: page.locator('input[name="./experience"]') }).first();
  const input = widget.locator('input[type="text"]').first();
  await expect(input, 'no visible text field for the experience picker').toBeVisible();
  const query = page.waitForResponse((r) => r.url().includes('a=search_experiences'), { timeout: 20_000 });
  await input.click();
  await input.pressSequentially('a', { delay: 40 });
  const res = await query;
  expect(res.ok(), `experiences search returned HTTP ${res.status()}`).toBeTruthy();
  // The account may legitimately have no experiences; what must hold is that the
  // picker asks the connector and gets a well-formed answer, not an error body.
  const body = await res.text();
  expect(body, 'experiences search returned an error body').not.toMatch(/error_code|Exception/i);

  await dialog.locator('button.cq-dialog-cancel').first().click();
  await expect(dialog).toBeHidden({ timeout: 10_000 });
});

test('row 27: the Assets rail "Brightcove Videos" source lists the account DAM assets', async ({ page }) => {
  // The asset-path lookup fires when the editor loads, before the rail is opened.
  const assetPath = page.waitForResponse((r) => r.url().includes('/bin/brightcove/getBrightcoveAssetPath.json'), { timeout: 30_000 });
  await openEditor(page);
  const apRes = await assetPath;
  expect(apRes.ok(), 'getBrightcoveAssetPath failed').toBeTruthy();
  const root = (await apRes.json()).brightcoveAssetPath;
  expect(root, 'no brightcoveAssetPath in the servlet response').toMatch(/^\/content\/dam\//);

  await page.locator('button[title="Toggle Side Panel" i]:visible, button.toggle-sidepanel:visible').first().click();
  const typeSelect = page.locator('coral-select[name="assetfilter_type_selector"]');
  await expect(typeSelect).toBeVisible({ timeout: 10_000 });

  const finder = page.waitForResponse((r) => r.url().includes(`/bin/wcm/contentfinder/asset/view.html${root}`), { timeout: 20_000 });
  await chooseSelectItem(page, typeSelect, { hasText: 'Brightcove Videos' });
  expect((await finder).ok(), 'content finder query for the Brightcove folder failed').toBeTruthy();

  const cards = page.locator(`coral-card.card-asset[data-path^="${root}/"]`);
  await expect.poll(() => cards.count(), { timeout: 20_000 }).toBeGreaterThan(0);
});

test('source guard: every Brightcove component refreshes itself after an edit, never the page', async () => {
  const root = path.resolve(__dirname, '../../../current/ui.apps/src/main/content/jcr_root/apps/brightcove/components/content');
  const configs = fs.readdirSync(root)
    .map((d) => path.join(root, d, '_cq_editConfig.xml'))
    .filter((f) => fs.existsSync(f));
  expect(configs.length, 'no component edit configs found').toBeGreaterThanOrEqual(3);
  for (const f of configs) {
    const xml = fs.readFileSync(f, 'utf8');
    for (const evt of ['afteredit', 'afterinsert', 'aftercopy', 'afterdelete']) {
      expect(xml, `${path.basename(path.dirname(f))} ${evt}`).toMatch(new RegExp(`${evt}="REFRESH_SELF"`));
    }
    expect(xml, `${path.basename(path.dirname(f))} uses REFRESH_PAGE`).not.toMatch(/REFRESH_PAGE/);
  }
});
