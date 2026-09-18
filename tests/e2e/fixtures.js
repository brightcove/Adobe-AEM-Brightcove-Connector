// Shared fixtures/helpers for the Brightcove Admin tool e2e tests.
const base = require('@playwright/test');

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

module.exports = {
  test, expect, ADMIN_PATH, openAdmin, waitForVideoRows,
  resolveAccountId, brightcoveAssetsRoot, firstBrightcoveAsset,
};
