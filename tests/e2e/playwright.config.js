// Playwright config for the Brightcove AEM Admin tool e2e tests.
//
// Targets a LOCAL AEM author at http://localhost:4502 (admin/admin) with a
// Brightcove account already configured (verify with `GET /bin/brightcove/accounts`).
// The admin tool itself is served at /brightcove/admin.html.
//
// See wiki/api/aem-connector-local-dev.md ("Admin tool e2e testing") for setup.
const { defineConfig, devices } = require('@playwright/test');

// Target-derived paths (state file, output dir) live in target.js so that
// concurrent runs against different instances do not collide.
const { AEM_BASE, STATE_PATH, OUTPUT_DIR } = require('./target');

module.exports = defineConfig({
  testDir: './specs',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  globalSetup: require.resolve('./global-setup'),
  outputDir: OUTPUT_DIR,
  use: {
    baseURL: AEM_BASE,
    // Reuse the AEM login-token cookie captured by global-setup, per target.
    storageState: STATE_PATH,
    ignoreHTTPSErrors: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
