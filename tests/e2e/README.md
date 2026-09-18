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
  Expect a non-empty `accounts` array (each entry `{ id, text, value }`, where
  `value` is the 13-digit account id). The suite reads the first entry at run
  time; set `BRC_ACCOUNT_ID` to pin a specific one.
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

## Running against more than one instance

Everything target-specific is derived from `AEM_BASE` in `target.js`: the saved
login state is `.auth/state-<host>-<port>.json` and results go to
`test-results/<host>-<port>/`. Concurrent runs against different instances
(e.g. cloud `:4502` and on-prem `:4602`) are therefore safe. ⚠️ Before this
existed both runs shared one `state.json` and, because `localhost` cookies
ignore the port, one run silently drove the other's instance.

```bash
AEM_BASE=http://localhost:4602 npm test          # on-prem
AEM_BASE=http://localhost:4502 npm test          # cloud
```

## How it works

- `global-setup.js` logs into AEM via `j_security_check` and saves the session
  cookie to `.auth/state.json` (HTTP basic auth alone gets redirected to the
  Granite sign-in page).
- `fixtures.js` exposes `openAdmin(page)` and `waitForVideoRows(page)` helpers, plus
  `resolveAccountId(request)`, `brightcoveAssetsRoot(request)` and
  `firstBrightcoveAsset(request)`, which read the account and a fixture asset from
  the running instance. Never hard-code an account, video or playlist id in a
  spec: this is a public repo.
- Each `specs/*.spec.js` drives a real user flow and asserts on the rendered UI
  and/or the outgoing `/bin/brightcove/api.js` request.

## Conventions

- One spec per bug/ticket (e.g. `bcon-172-playlist-search.spec.js`).
- Prefer read-only flows. Mutating flows (create label, rename playlist) write to
  the live account — clean up after, and gate them clearly.

## Pre-QA gate

`pre-qa-gate.sh` runs version-bump, build+install, deployed-bundle, and
content-package checks, then this suite, before a ticket goes to Ready for QA.
It gates cloud (`:4502`), on-prem (`:4602`), or both, and defaults to probing
both instances. See `./pre-qa-gate.sh --help`; details in
`wiki/api/aem-connector-local-dev.md` → "Pre-QA gate" and
`ONPREM-PARITY-PLAN.md` §3 Phase 1.
