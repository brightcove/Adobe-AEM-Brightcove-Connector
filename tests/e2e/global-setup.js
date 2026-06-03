// Authenticate to AEM once and persist the session cookie for all specs.
// AEM's Granite UI uses a form login (j_security_check) + login-token cookie;
// HTTP basic auth alone redirects /brightcove/admin.html to the sign-in page.
const { request } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const AEM_BASE = process.env.AEM_BASE || 'http://localhost:4502';
const AEM_USER = process.env.AEM_USER || 'admin';
const AEM_PASS = process.env.AEM_PASS || 'admin';

const STATE_PATH = path.join(__dirname, '.auth', 'state.json');

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
  await ctx.dispose();
};
