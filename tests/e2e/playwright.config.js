// Playwright config for the Brightcove AEM Admin tool e2e tests.
//
// Targets a LOCAL AEM author at http://localhost:4502 (admin/admin) with a
// Brightcove account already configured (verify with `GET /bin/brightcove/accounts`).
// The admin tool itself is served at /brightcove/admin.html.
//
// See wiki/api/aem-connector-local-dev.md ("Admin tool e2e testing") for setup.
const { defineConfig, devices } = require('@playwright/test');

const AEM_BASE = process.env.AEM_BASE || 'http://localhost:4502';
const AEM_USER = process.env.AEM_USER || 'admin';
const AEM_PASS = process.env.AEM_PASS || 'admin';

module.exports = defineConfig({
  testDir: './specs',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  globalSetup: require.resolve('./global-setup'),
  use: {
    baseURL: AEM_BASE,
    // Reuse the AEM login-token cookie captured by global-setup.
    storageState: require('path').join(__dirname, '.auth', 'state.json'),
    ignoreHTTPSErrors: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
