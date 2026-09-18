// Shared fixtures/helpers for the Brightcove Admin tool e2e tests.
const base = require('@playwright/test');
const { AEM_BASE } = require('./target');

const ADMIN_PATH = '/brightcove/admin.html';

const test = base.test;
const expect = base.expect;

// Open the admin tool and wait until it is interactive: the initial video
// list is loaded via JSONP after jQuery's ready handlers bind, so waiting for
// the first rows guarantees tab/search click handlers are wired up.
async function openAdmin(page) {
  await page.goto(ADMIN_PATH, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#tbData tr', { timeout: 20_000 });
}

// Wait until the videos table has at least one data row rendered.
async function waitForVideoRows(page, timeout = 20_000) {
  await page.waitForSelector('#tbData tr', { timeout });
}

// The Brightcove account configured in the AEM under test. Resolved at run time
// from the connector's own accounts servlet, so no account id lives in this
// public repo and the same suite runs against instances configured with
// different accounts. Set BRC_ACCOUNT_ID to pin one when an instance has several.
// `request` is Playwright's APIRequestContext (the `request` fixture or `page.request`);
// it carries the saved login state from playwright.config.js.
async function resolveAccountId(request) {
  if (process.env.BRC_ACCOUNT_ID) return process.env.BRC_ACCOUNT_ID;
  const res = await request.get('/bin/brightcove/accounts');
  if (!res.ok()) {
    throw new Error(`cannot list accounts (HTTP ${res.status()}): no account to test against`);
  }
  const body = await res.json();
  const accounts = Array.isArray(body.accounts) ? body.accounts : [];
  if (!accounts.length || !accounts[0].value) {
    throw new Error('the accounts servlet returned no account: configure one in AEM first');
  }
  return String(accounts[0].value);
}

// Path of the account's DAM folder, e.g. /content/dam/brightcove_assets/<account id>.
async function brightcoveAssetsRoot(request) {
  return `/content/dam/brightcove_assets/${await resolveAccountId(request)}`;
}

// First Brightcove-linked dam:Asset under that folder. Resolved at run time rather
// than hard-coded: no video ids in a public repo, and the fixture asset differs
// between instances. Throws (never returns a guess) when there is nothing to
// measure on, so a spec reports "could not decide" instead of a false pass.
async function firstBrightcoveAsset(request) {
  const root = await brightcoveAssetsRoot(request);
  const res = await request.get(`${root}.1.json`);
  if (!res.ok()) {
    throw new Error(`cannot list ${root} (HTTP ${res.status()}): no asset to measure on`);
  }
  const body = await res.json();
  const name = Object.keys(body).find(
    (k) => body[k] && body[k]['jcr:primaryType'] === 'dam:Asset');
  if (!name) {
    throw new Error(`no dam:Asset under ${root}: run a Brightcove sync first`);
  }
  return `${root}/${name}`;
}

// ---- Live-account lookups (read-only) -------------------------------------

// Parse the connector's JSONP envelope: cb({...});
function parseJsonp(text) {
  const m = /^\s*[\w$.]+\((.*)\)\s*;?\s*$/s.exec(text);
  if (!m) throw new Error(`not a JSONP body: ${text.slice(0, 120)}`);
  return JSON.parse(m[1]);
}

// First `limit` videos of the configured account through the connector's own
// api servlet, as { id, name } objects. Throws when the account has none.
async function firstVideos(request, limit = 3) {
  const acct = await resolveAccountId(request);
  const res = await request.get(
    `/bin/brightcove/api.js?account_id=${acct}&a=search_videos&query=&limit=${limit}&start=0&sort=&callback=cb`);
  if (!res.ok()) throw new Error(`search_videos failed (HTTP ${res.status()})`);
  const body = parseJsonp(await res.text());
  const items = (body.items || []).map((v) => ({ id: String(v.id), name: v.name }));
  if (!items.length) throw new Error('the account returned no videos: nothing to pick in a dialog');
  return items;
}


// Sling POST from the API request context. Two filters sit in front of a
// cookie-authenticated POST that a basic-auth curl never meets (measured
// 2026-09-18, both 403 with an empty body): Sling's ReferrerFilter rejects an
// empty Referer ("Rejected empty referrer header for POST request" in
// error.log), and Granite's CSRF filter wants the token header.
async function slingPost(request, url, form) {
  const tok = await request.get('/libs/granite/csrf/token.json');
  if (!tok.ok()) throw new Error(`cannot fetch a CSRF token (HTTP ${tok.status()})`);
  const { token } = await tok.json();
  return request.post(url, { form, headers: { 'CSRF-Token': token, Referer: `${AEM_BASE}/` } });
}

// ---- Sites editor fixture page ---------------------------------------------
//
// The connector's three Sites components (Video Player, Playlist Player,
// Experiences) are exercised on a page the spec creates itself, copying
// current/scripts/setup-local-dev.sh: filler text between the components so the
// page is tall enough for the BGS-1690 scroll check, two players (one above and
// one below the fold), the inner parsys with an explicit sling:resourceType (a
// raw Sling POST leaves it unset and the children silently do not render). The
// site scaffold (template + a responsivegrid policy allowing group:Brightcove)
// must already exist: run setup-local-dev.sh once per fresh instance.
const SITES_SITE = process.env.BRC_TEST_SITE || 'test-site';
const SITES_TEMPLATE = `/conf/${SITES_SITE}/settings/wcm/templates/content-page`;
const SITES_POLICY = `/conf/${SITES_SITE}/settings/wcm/policies/wcm/foundation/components/responsivegrid/default-policy`;
// Long enough that the mid player sits below the fold at a 1400x900 viewport
// even when every component is an unconfigured 50px placeholder.
const FILLER = 'Filler so the page is taller than the viewport and the second player sits below the fold. '.repeat(60);

async function createSitesTestPage(request, name) {
  for (const p of [SITES_TEMPLATE, SITES_POLICY]) {
    const r = await request.get(`${p}.json`);
    if (!r.ok()) {
      throw new Error(`${p} is missing (HTTP ${r.status()}): run current/scripts/setup-local-dev.sh first; the Sites specs cannot measure anything without the site scaffold`);
    }
  }
  const policy = await (await request.get(`${SITES_POLICY}.json`)).json();
  const groups = [].concat(policy.components || []);
  if (!groups.includes('group:Brightcove')) {
    throw new Error(`${SITES_POLICY} does not allow group:Brightcove (${groups.join(', ')}): the components would not render`);
  }
  const pagePath = `/content/${SITES_SITE}/${name}`;
  const rg = 'jcr:content/root/responsivegrid';
  const comp = (node, rt, extra = {}) => {
    const out = { [`${rg}/${node}/jcr:primaryType`]: 'nt:unstructured', [`${rg}/${node}/sling:resourceType`]: rt };
    for (const [k, v] of Object.entries(extra)) out[`${rg}/${node}/${k}`] = v;
    return out;
  };
  const form = {
    'jcr:primaryType': 'cq:Page',
    'jcr:content/jcr:primaryType': 'cq:PageContent',
    'jcr:content/jcr:title': `e2e Brightcove components (${name})`,
    'jcr:content/cq:template': SITES_TEMPLATE,
    'jcr:content/sling:resourceType': 'wcm/foundation/components/page',
    'jcr:content/root/jcr:primaryType': 'nt:unstructured',
    'jcr:content/root/sling:resourceType': 'wcm/foundation/components/responsivegrid',
    [`${rg}/jcr:primaryType`]: 'nt:unstructured',
    [`${rg}/sling:resourceType`]: 'wcm/foundation/components/responsivegrid',
    ...comp('filler_a', 'wcm/foundation/components/text', { text: FILLER }),
    ...comp('brightcove_player_top', 'brightcove/components/content/brightcoveplayer'),
    ...comp('filler_b', 'wcm/foundation/components/text', { text: FILLER }),
    ...comp('brightcove_playlist', 'brightcove/components/content/brightcoveplayer-playlist'),
    ...comp('filler_c', 'wcm/foundation/components/text', { text: FILLER }),
    ...comp('brightcove_player_mid', 'brightcove/components/content/brightcoveplayer'),
    ...comp('filler_d', 'wcm/foundation/components/text', { text: FILLER }),
    ...comp('brightcove_experiences', 'brightcove/components/content/brightcoveexperiences'),
    ...comp('filler_e', 'wcm/foundation/components/text', { text: FILLER }),
  };
  const res = await slingPost(request, pagePath, form);
  if (res.status() !== 201 && res.status() !== 200) {
    throw new Error(`creating ${pagePath} failed (HTTP ${res.status()})`);
  }
  // Re-assert the inner parsys type: see setup-local-dev.sh step 5.
  await slingPost(request, `${pagePath}/${rg}`, { 'sling:resourceType': 'wcm/foundation/components/responsivegrid' });
  const check = await request.get(`${pagePath}/${rg}.json`);
  if (!check.ok()) throw new Error(`${pagePath} was created but its parsys is unreadable (HTTP ${check.status()})`);
  return pagePath;
}

async function deletePage(request, pagePath) {
  const res = await slingPost(request, pagePath, { ':operation': 'delete' });
  if (!res.ok() && res.status() !== 404) {
    throw new Error(`deleting ${pagePath} failed (HTTP ${res.status()})`);
  }
}

// The Touch UI editor scrolls #ContentScrollView on both the AEMaaCS SDK and
// AEM 6.5 shells (measured 2026-09-18); window.scrollY stays 0 there. Fall
// back to the window for any shell that lacks the container.
async function editorScrollTop(page) {
  return page.evaluate(() => {
    const el = document.getElementById('ContentScrollView');
    return el ? el.scrollTop : window.scrollY;
  });
}

module.exports = {
  test, expect, ADMIN_PATH, openAdmin, waitForVideoRows,
  resolveAccountId, brightcoveAssetsRoot, firstBrightcoveAsset,
  parseJsonp, firstVideos, slingPost, createSitesTestPage, deletePage, editorScrollTop,
};
