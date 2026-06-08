// Shared fixtures/helpers for the Brightcove Admin tool e2e tests.
const base = require('@playwright/test');

const ADMIN_PATH = '/brightcove/admin.html';

// Account configured in local AEM (see GET /bin/brightcove/accounts).
const ACCOUNT_ID = process.env.BRC_ACCOUNT_ID || '5822937471001';

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

module.exports = { test, expect, ADMIN_PATH, ACCOUNT_ID, openAdmin, waitForVideoRows };
