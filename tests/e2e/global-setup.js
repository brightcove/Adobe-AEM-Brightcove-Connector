// Authenticate to AEM once and persist the session cookie for all specs.
// AEM's Granite UI uses a form login (j_security_check) + login-token cookie;
// HTTP basic auth alone redirects /brightcove/admin.html to the sign-in page.
const { request } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const { AEM_BASE, AEM_USER, AEM_PASS, STATE_PATH, TARGET_KEY } = require('./target');

module.exports = async () => {
  fs.mkdirSync(path.dirname(STATE_PATH), { recursive: true });
  const ctx = await request.newContext({ baseURL: AEM_BASE });

  const resp = await ctx.post('/libs/granite/core/content/login.html/j_security_check', {
    form: {
      j_username: AEM_USER,
      j_password: AEM_PASS,
      j_validate: 'true',
      _charset_: 'utf-8',
    },
  });
  if (resp.status() >= 400) {
    throw new Error(`AEM login failed: HTTP ${resp.status()}`);
  }

  await ctx.storageState({ path: STATE_PATH });
  console.log(`[global-setup] ${AEM_BASE} -> ${path.relative(__dirname, STATE_PATH)} (target ${TARGET_KEY})`);
  await ctx.dispose();
};
