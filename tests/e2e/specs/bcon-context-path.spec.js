// Context-path handling — ONPREM-PARITY-PLAN.md §3 Phase 3 item 1
// (ported from the on-prem line: c100a16 + cf674b7, upstream issue #64).
//
// The defect this discriminates: every `/bin/brightcove/...` URL used to be a
// bare absolute literal. AEM as a Cloud Service always serves from the root, so
// on cloud a bare literal is indistinguishable from a correct one and the whole
// class of bug is invisible. An on-prem AEM deployed under a servlet context
// path (a WAR in a container, or Felix `org.apache.felix.http.context_path`)
// serves the same servlets under `<ctx>/bin/brightcove/...`, so every bare
// literal 404s and the admin tool comes up empty.
//
// Neither local instance is deployed under a context path, so this spec makes
// the client believe it is:
//   - test 1 rewrites the one value the admin page publishes
//     (window.brc.contextPath) and then stands in for the container, serving
//     CTX-prefixed requests from the real path. Unprefixed traffic to
//     /bin/brightcove is the failure, and the page still has to work end to end
//     through the prefixed path, so a fix that prefixes but breaks the parse
//     does not pass either.
//   - test 2 checks the Touch UI half, where the context path comes from
//     Granite rather than the page: that the helper is actually loaded on that
//     surface AND that a real source answered (source === 'granite'), because
//     a helper that fell through to the '' default would look identical on this
//     root-served instance.
//   - test 3 is a source guard for the surfaces the two runtime tests cannot
//     reach from here.
//
// NOT measured by this spec (recorded rather than defaulted to pass): the
// Coral/Classic dialog XML attributes that name a servlet path directly
// (`storePath`, `options`, autocomplete `src` in components/**/*.xml). Whether
// Granite externalizes those server-side is an open question; see
// current/docs/clientlibs-context-path.md.
const fs = require('fs');
const path = require('path');
const { test, expect, openAdmin, firstBrightcoveAsset } = require('../fixtures');

const CTX = '/aemctx';
const ADMIN = '/brightcove/admin.html';
const METADATA_EDITOR = '/mnt/overlay/dam/gui/content/assets/metadataeditor.external.html';

// The literal the admin page's HTL renders on a root-served instance. Asserted
// rather than assumed: if the page stops publishing it, test 1 must error out
// instead of quietly passing with nothing rewritten.
const ROOT_MARKER = 'window.brc.contextPath = "";';

test('admin tool: every connector request goes out under the served context path', async ({ page }) => {
  const prefixed = [];
  const bare = [];

  // 1. Make the server look like an AEM deployed under CTX.
  await page.route((url) => url.pathname === ADMIN, async (route) => {
    const res = await route.fetch();
    const body = await res.text();
    if (!body.includes(ROOT_MARKER)) {
      throw new Error(
        `admin page did not publish a context path (${ROOT_MARKER} absent). ` +
        'The context-path source changed shape; this spec cannot measure anything.');
    }
    await route.fulfill({
      response: res,
      body: body.replace(ROOT_MARKER, `window.brc.contextPath = "${CTX}";`),
    });
  });

  // 2. Stand in for the servlet container: serve CTX-prefixed requests from the
  //    real path underneath it, so a correctly prefixed client still works.
  await page.route(`**${CTX}/**`, async (route) => {
    const url = new URL(route.request().url());
    prefixed.push(url.pathname);
    url.pathname = url.pathname.slice(CTX.length);
    await route.fulfill({ response: await route.fetch({ url: url.toString() }) });
  });

  // 3. Any connector request that skipped the helper.
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname;
    if (p.startsWith('/bin/brightcove')) bare.push(p);
  });

  await openAdmin(page);

  // Exercise more of the URL-building surface than the initial load: search
  // (brcAdmin.js apiLocation), the labels dropdown and the playlists tab
  // (brcUI.js api.js calls).
  await page.locator('#search').fill('a');
  await page.locator('#searchBut').click();
  await page.waitForTimeout(1500);
  await page.locator('#allPlaylists').click();
  await expect(page.locator('#searchDiv_pl')).toBeVisible();
  await page.waitForTimeout(1500);

  expect(bare, 'connector requests issued without the context path').toEqual([]);
  const connector = prefixed.filter((p) => p.startsWith(`${CTX}/bin/brightcove`));
  expect(connector.length,
    'no connector request was seen at all, so nothing was measured').toBeGreaterThan(0);
  // The prefixed path has to have actually worked, not just been requested.
  expect(await page.locator('#tbData tr').count()).toBeGreaterThan(0);
});

// Negative control for the test above. A harness that reports "no unprefixed
// requests" is only worth anything if it can still SEE an unprefixed request,
// and on a root-served instance a broken build and a fixed one produce
// identical traffic unless the context path is simulated. This bypasses the
// helper the way the unported code behaved (brcUrl.js short-circuits when
// brc.url already exists) and requires the same assertions to go red.
test('negative control: the same check fails when the helper is bypassed', async ({ page }) => {
  await page.addInitScript(() => {
    window.brc = {
      url: function (path) { return path; },                       // pre-port behaviour
      contextPathInfo: function () { return { path: '', source: 'none' }; },
    };
  });

  const bare = [];
  await page.route((url) => url.pathname === ADMIN, async (route) => {
    const res = await route.fetch();
    const body = await res.text();
    await route.fulfill({
      response: res,
      body: body.replace(ROOT_MARKER, `window.brc.contextPath = "${CTX}";`),
    });
  });
  await page.route(`**${CTX}/**`, async (route) => {
    const url = new URL(route.request().url());
    url.pathname = url.pathname.slice(CTX.length);
    await route.fulfill({ response: await route.fetch({ url: url.toString() }) });
  });
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname;
    if (p.startsWith('/bin/brightcove')) bare.push(p);
  });

  await openAdmin(page);

  expect(bare.length,
    'the helper was bypassed and yet no unprefixed connector request was seen: ' +
    'this harness can no longer detect the defect it exists to catch').toBeGreaterThan(0);
});

test('Touch UI: the helper is loaded and a real context-path source answers', async ({ page, request }) => {
  const asset = await firstBrightcoveAsset(request);
  await page.goto(METADATA_EDITOR + asset, { waitUntil: 'networkidle' });

  const info = await page.evaluate(
    () => (window.brc && window.brc.contextPathInfo) ? window.brc.contextPathInfo() : null);
  expect(info, 'the brc.url clientlib did not load on the DAM asset editor').not.toBeNull();
  // 'none' would mean the helper silently defaulted to the root: on this
  // root-served instance that produces the same URLs as a working source, which
  // is exactly the state that must not be reported as a pass.
  expect(info.source, 'no context-path source available on this surface').toBe('granite');
  expect(info.path).toBe('');

  const url = await page.evaluate(() => {
    const original = Granite.HTTP.getContextPath;
    Granite.HTTP.getContextPath = () => '/aemctx';
    try {
      return window.brc.url('/bin/brightcove/api.json');
    } finally {
      Granite.HTTP.getContextPath = original;
    }
  });
  expect(url).toBe('/aemctx/bin/brightcove/api.json');
});

test('no bare /bin/brightcove URL literal is left in the shipped clientlib JS', () => {
  const ROOT = path.resolve(__dirname, '../../../current/ui.apps/src/main/content/jcr_root/apps/brightcove');
  // Each exemption needs a reason; a file that stops needing one should lose it.
  const EXEMPT = {
    'clientlibs/clientlib-url/js/brcUrl.js': 'the helper itself: header comment only',
    'clientlibs/clientlib-tools/js/brcTransport.js': 'comment describing the servlet',
  };

  const files = [];
  (function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(p);
      else if (entry.name.endsWith('.js') && !p.includes('/vendor/') &&
               !/jquery|bootstrap|lodash|BrightcoveExperiences/.test(entry.name)) files.push(p);
    }
  })(ROOT);
  expect(files.length, 'found no clientlib JS to scan').toBeGreaterThan(5);

  const offenders = [];
  for (const file of files) {
    const rel = path.relative(ROOT, file);
    const text = fs.readFileSync(file, 'utf8');
    const lines = text.split('\n');
    lines.forEach((line, i) => {
      if (!line.includes('/bin/brightcove')) return;
      if (EXEMPT[rel]) return;
      // Accepted form: the literal is the argument of brc.url(...).
      if (/brc\.url\(\s*["']\/bin\/brightcove/.test(line)) return;
      offenders.push(`${rel}:${i + 1}: ${line.trim()}`);
    });
  }
  expect(offenders, 'bare connector URL literals (wrap them in brc.url())').toEqual([]);
});
