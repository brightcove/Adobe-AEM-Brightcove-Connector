// BGS-1750: the advanced-embed Video Player clientlib (brc.html5-player,
// BrightcoveExperiences.js) injects each player loader once per page and does not
// tear down a live player when createPlayers() runs again.
//
// Page under test: two Video Player components (advanced embed, the account's
// default player, no width/height), so both containers share ONE loader src.
// Rendered with wcmmode=disabled, and in the Sites editor for the real
// REFRESH_SELF path (which re-evaluates the clientlib). Asserted:
//   - exactly one index.min.js <script> per distinct loader src, including after
//     createPlayers() re-runs while the loader is in flight and after it loaded;
//   - a re-run, and an observer pass caused by inserting a sibling container (the
//     BGS-1690 MutationObserver), leave the live player alone: same player object,
//     not disposed, built once, no "Ignoring already initialized player";
//   - replacing a container (what REFRESH_SELF does) still rebuilds exactly that
//     one player, without injecting the loader again, and nothing rebuilds in a loop;
//   - the generated <video> never carries width="" / width="null" (height alike).
// The generated markup is recorded by wrapping the innerHTML setter on
// .brightcove-container before any page script runs, because video.js rewrites
// the tag's attributes once it initialises.
const {
  test, expect, resolveAccountId, playableVideos, createSitesTestPage, deletePage, mediaHostReachable,
} = require('../fixtures');

// Media and player surfaces need real Chrome: bundled chromium under-initialises the media stack.
// Not serial: the two tests are independent page loads, so each reports its own verdict.
test.use({ channel: 'chrome' });

const RUN = `e2e-bgs1750-${Date.now().toString(36)}`;
const PLAYER_RT = 'brightcove/components/content/brightcoveplayer';
let PAGE_PATH;

test.beforeAll(async ({ request }) => {
  test.skip(!(await mediaHostReachable('https://players.brightcove.net/')), 'NOT MEASURED: players.brightcove.net is unreachable, so no player loader can run');
  const account = await resolveAccountId(request);
  const videos = await playableVideos(request, 2);
  const second = videos[1] || videos[0];
  PAGE_PATH = await createSitesTestPage(request, RUN, [
    ['brightcove_player_a', PLAYER_RT, { account, videoPlayer: videos[0].id }],
    ['brightcove_player_b', PLAYER_RT, { account, videoPlayer: second.id }],
  ]);
});

test.afterAll(async ({ request }) => {
  if (PAGE_PATH) await deletePage(request, PAGE_PATH);
});

// Runs in the page before any script: records every HTML string the connector
// writes into a .brightcove-container, keyed by data-playerid.
function recordBuilds() {
  window.__brcBuilds = {};
  const desc = Object.getOwnPropertyDescriptor(Element.prototype, 'innerHTML');
  Object.defineProperty(Element.prototype, 'innerHTML', {
    configurable: true,
    get() { return desc.get.call(this); },
    set(v) {
      if (this.classList && this.classList.contains('brightcove-container')) {
        const id = this.getAttribute('data-playerid');
        (window.__brcBuilds[id] = window.__brcBuilds[id] || []).push(String(v));
      }
      desc.set.call(this, v);
    },
  });
}

function armConsole(page) {
  const lines = [];
  page.on('console', (m) => lines.push(`[${m.type()}] ${m.text()}`));
  page.on('pageerror', (e) => lines.push(`[pageerror] ${e.message}`));
  return lines;
}

// Loader <script> srcs on the page, grouped: { src: count }.
const loaderCounts = (page) => page.evaluate(() => {
  const out = {};
  document.querySelectorAll('script[src*="players.brightcove.net"]').forEach((s) => {
    const src = s.getAttribute('src');
    if (/\/index\.min\.js$/.test(src)) out[src] = (out[src] || 0) + 1;
  });
  return out;
});

const containerIds = (page) => page.evaluate(() => Array.from(document.querySelectorAll('.brightcove-container[data-playerid]')).map((c) => c.getAttribute('data-playerid')));

// Wait until every id has a live, ready player attached to the document.
async function waitForPlayers(page, ids) {
  await expect.poll(() => page.evaluate((list) => list.map((id) => {
    if (typeof videojs === 'undefined' || !videojs.getPlayers) return `${id}: videojs undefined`;
    const p = videojs.getPlayers()[id];
    if (!p) return `${id}: no player`;
    if (p.isDisposed()) return `${id}: disposed`;
    if (!document.documentElement.contains(p.el())) return `${id}: detached`;
    if (p.error()) return `${id}: player error ${p.error().code}`;
    return p.isReady_ ? 'ok' : `${id}: not ready`;
  }), ids), { timeout: 45_000, message: 'players did not all become live and ready' }).toEqual(ids.map(() => 'ok'));
}

// Tag every current player so a later check can tell "same object" from "rebuilt".
const tagPlayers = (page, ids) => page.evaluate((list) => {
  window.__brcTagged = {};
  list.forEach((id) => { window.__brcTagged[id] = videojs.getPlayers()[id]; });
}, ids);

const playerState = (page, ids) => page.evaluate((list) => list.map((id) => {
  const tagged = window.__brcTagged[id];
  const now = videojs.getPlayers()[id];
  return { id, same: !!tagged && tagged === now, disposed: !tagged || tagged.isDisposed(), builds: (window.__brcBuilds[id] || []).length };
}), ids);

// Soft, so a duplicate loader does not hide whether the players survived the re-runs.
function expectOneLoaderPerSrc(counts, when) {
  expect(Object.keys(counts).length, `${when}: no player loader <script> on the page`).toBeGreaterThan(0);
  for (const [src, n] of Object.entries(counts)) expect.soft(n, `${when}: ${src} injected ${n} times`).toBe(1);
}

const sizeProblems = (builds) => {
  const out = [];
  for (const [id, list] of Object.entries(builds)) {
    for (const html of list) {
      const m = html.match(/<video[^>]*>/i);
      if (!m) continue;
      for (const a of ['width', 'height']) {
        const v = new RegExp(`\\s${a}="([^"]*)"`, 'i').exec(m[0]);
        if (v && (v[1] === '' || v[1] === 'null' || v[1] === 'undefined')) out.push(`${id}: ${a}="${v[1]}"`);
      }
    }
  }
  return out;
};

test('re-running createPlayers while the loader is in flight injects it once and builds each player once', async ({ page }) => {
  const consoleLines = armConsole(page);
  await page.addInitScript(recordBuilds);
  // Two extra passes right after the DOMContentLoaded one: the loader is still in flight.
  await page.addInitScript(() => {
    document.addEventListener('DOMContentLoaded', () => { createPlayers(); createPlayers(); });
  });
  await page.goto(`${PAGE_PATH}.html?wcmmode=disabled`, { waitUntil: 'load' });
  const ids = await containerIds(page);
  expect(ids.length, 'the page should render two advanced-embed containers').toBe(2);
  await waitForPlayers(page, ids);
  await page.waitForTimeout(1500);

  expectOneLoaderPerSrc(await loaderCounts(page), 'after in-flight re-runs');
  const builds = await page.evaluate(() => window.__brcBuilds);
  for (const id of ids) expect.soft((builds[id] || []).length, `${id} built ${(builds[id] || []).length} times`).toBe(1);
  expect.soft(sizeProblems(builds), 'empty or null size attributes on the generated <video>').toEqual([]);
  expect(consoleLines.filter((l) => /already initiali[sz]ed/i.test(l)), 'duplicate player initialisation').toEqual([]);
});

test('a re-run and an observer pass leave live players alone; a replaced container is rebuilt once', async ({ page }) => {
  const consoleLines = armConsole(page);
  await page.addInitScript(recordBuilds);
  await page.goto(`${PAGE_PATH}.html?wcmmode=disabled`, { waitUntil: 'load' });
  const ids = await containerIds(page);
  expect(ids.length, 'the page should render two advanced-embed containers').toBe(2);
  await waitForPlayers(page, ids);
  const [a, b] = ids;
  expectOneLoaderPerSrc(await loaderCounts(page), 'first load');

  // 1. Re-run after the loader has loaded.
  await tagPlayers(page, ids);
  await page.evaluate(() => createPlayers());
  await page.waitForTimeout(3000);
  for (const s of await playerState(page, ids)) {
    expect(s, `createPlayers() re-run tore down ${s.id}`).toEqual({ id: s.id, same: true, disposed: false, builds: 1 });
  }
  expectOneLoaderPerSrc(await loaderCounts(page), 'after a createPlayers() re-run');

  // 2. Observer pass: insert a new sibling container next to A (shallow clone, fresh
  //    id). The observer re-runs createPlayers over A's parent, which includes A.
  const cloneId = await page.evaluate((id) => {
    const src = document.querySelector(`.brightcove-container[data-playerid="${id}"]`);
    const clone = src.cloneNode(false);
    const newId = `e2eclone${Date.now()}`;
    clone.id = `container-${newId}`;
    clone.setAttribute('data-playerid', newId);
    src.parentNode.appendChild(clone);
    return newId;
  }, a);
  await waitForPlayers(page, [cloneId]);
  await page.waitForTimeout(3000);
  for (const s of await playerState(page, ids)) {
    expect(s, `the observer pass over an inserted sibling tore down ${s.id}`).toEqual({ id: s.id, same: true, disposed: false, builds: 1 });
  }
  expectOneLoaderPerSrc(await loaderCounts(page), 'after an observer pass');

  // 3. REFRESH_SELF stand-in: replace B's container with fresh server-shaped markup
  //    (an empty container, same id). B must be rebuilt once, A left alone.
  await page.evaluate((id) => {
    const old = document.querySelector(`.brightcove-container[data-playerid="${id}"]`);
    old.parentNode.replaceChild(old.cloneNode(false), old);
  }, b);
  await expect.poll(() => page.evaluate((id) => (window.__brcBuilds[id] || []).length, b), { timeout: 20_000, message: 'the replaced container was never rebuilt' }).toBe(2);
  await waitForPlayers(page, ids);
  await page.waitForTimeout(3000);
  const [sa, sb] = await playerState(page, ids);
  expect(sa, 'replacing B rebuilt A').toEqual({ id: a, same: true, disposed: false, builds: 1 });
  expect(sb.same, 'B should be a new player after its container was replaced').toBe(false);
  expect(sb.builds, 'B rebuilt more than once (observer loop?)').toBe(2);
  expectOneLoaderPerSrc(await loaderCounts(page), 'after a container replace');

  const builds = await page.evaluate(() => window.__brcBuilds);
  expect((builds[cloneId] || []).length, 'the inserted container built more than once').toBe(1);
  expect(sizeProblems(builds), 'empty or null size attributes on the generated <video>').toEqual([]);
  expect(consoleLines.filter((l) => /already initiali[sz]ed/i.test(l)), 'duplicate player initialisation').toEqual([]);
});

// The real REFRESH_SELF path: the Sites editor re-renders one component through
// its own editable.refresh(). The fragment it fetches carries the clientlib's
// <script> again, which is why the loader registry lives on window.
test('a Sites editor REFRESH_SELF of one player rebuilds it once and leaves the other player and the loader alone', async ({ page }) => {
  const consoleLines = armConsole(page);
  await page.addInitScript(recordBuilds);
  await page.goto(`/editor.html${PAGE_PATH}.html`, { waitUntil: 'load', timeout: 60_000 });
  await expect(page.locator('.cq-Overlay[data-path$="brightcove_player_b"]')).toBeAttached({ timeout: 30_000 });
  const frame = page.frames().find((f) => f !== page.mainFrame() && f.url().includes(PAGE_PATH));
  expect(frame, 'content frame not found').toBeTruthy();
  const ids = await containerIds(frame);
  expect(ids.length, 'the page should render two advanced-embed containers').toBe(2);
  await waitForPlayers(frame, ids);
  const [a, b] = ids;
  await tagPlayers(frame, ids);
  expectOneLoaderPerSrc(await loaderCounts(frame), 'editor first load');
  await frame.evaluate(() => { window.__brcFirstCreatePlayers = createPlayers; });

  const refreshed = await page.evaluate(async (p) => {
    const ed = Granite.author.editables.find(p)[0];
    if (!ed) return 'no editable';
    await ed.refresh();
    return 'ok';
  }, `${PAGE_PATH}/jcr:content/root/responsivegrid/brightcove_player_b`);
  expect(refreshed, 'could not drive the editor refresh').toBe('ok');

  await expect.poll(() => frame.evaluate((id) => (window.__brcBuilds[id] || []).length, b), { timeout: 20_000, message: 'the refreshed player was never rebuilt' }).toBe(2);
  await waitForPlayers(frame, ids);
  await frame.waitForTimeout(3000);
  const [sa, sb] = await playerState(frame, ids);
  expect(sa, 'refreshing B rebuilt A').toEqual({ id: a, same: true, disposed: false, builds: 1 });
  expect(sb.same, 'B should be a new player after REFRESH_SELF').toBe(false);
  expect(sb.builds, 'B rebuilt more than once after REFRESH_SELF').toBe(2);
  expectOneLoaderPerSrc(await loaderCounts(frame), 'after REFRESH_SELF');
  const reEvaluated = await frame.evaluate(() => window.__brcFirstCreatePlayers !== createPlayers);
  console.log(`INFO: the REFRESH_SELF fragment ${reEvaluated ? 're-evaluated' : 'did not re-evaluate'} the player clientlib`);
  expect(consoleLines.filter((l) => /already initiali[sz]ed/i.test(l)), 'duplicate player initialisation').toEqual([]);
});
