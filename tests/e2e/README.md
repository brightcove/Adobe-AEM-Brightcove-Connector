# Brightcove Admin tool — Playwright e2e

End-to-end tests that drive the connector's **Brightcove Admin** tool in a running
local AEM author, against a real configured Brightcove account.

## Prerequisites

- Local AEM author at `http://localhost:4502` (admin/admin), connector deployed
  (`cd current && mvn clean install -PautoInstallPackage`).
- A Brightcove account configured in AEM. Verify with:
  ```bash
  curl -s -u admin:admin http://localhost:4502/bin/brightcove/accounts
  ```
  Expect a non-empty `accounts` array (e.g. `My account [5822937471001]`).
- The tool itself is served at `http://localhost:4502/brightcove/admin.html`.

See `wiki/api/aem-connector-local-dev.md` → "Admin tool e2e testing" for the full
context (config mechanism, api.js contract, account data).

## Install (once)

```bash
cd tests/e2e
npm install
npx playwright install chromium
```

## Run

```bash
npm test                 # headless
npm run test:headed      # watch it drive the browser
npx playwright test specs/smoke.spec.js   # a single spec
```

## How it works

- `global-setup.js` logs into AEM via `j_security_check` and saves the session
  cookie to `.auth/state.json` (HTTP basic auth alone gets redirected to the
  Granite sign-in page).
- `fixtures.js` exposes `openAdmin(page)` and `waitForVideoRows(page)` helpers.
- Each `specs/*.spec.js` drives a real user flow and asserts on the rendered UI
  and/or the outgoing `/bin/brightcove/api.js` request.

## Conventions

- One spec per bug/ticket (e.g. `bcon-172-playlist-search.spec.js`).
- Prefer read-only flows. Mutating flows (create label, rename playlist) write to
  the live account — clean up after, and gate them clearly.
