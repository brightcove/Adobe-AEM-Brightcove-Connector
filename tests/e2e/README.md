# Brightcove Admin tool — Playwright e2e

End-to-end tests that drive the connector's **Brightcove Admin** tool in a running
local AEM author, against a real configured Brightcove account.

## Prerequisites

- Local AEM author at `http://localhost:4502` (admin/admin), connector deployed
  (`cd current && mvn clean install -PautoInstallPackage`).
- For the Sites editor specs: the `test-site` scaffold from
  `current/scripts/setup-local-dev.sh` (run once per fresh instance).
- A Brightcove account configured in AEM. Verify with:
  ```bash
  curl -s -u admin:admin http://localhost:4502/bin/brightcove/accounts
  ```
  Expect a non-empty `accounts` array (each entry `{ id, text, value }`, where
  `value` is the 13-digit account id). The suite reads the first entry at run
  time; set `BRC_ACCOUNT_ID` to pin a specific one.
- The tool itself is served at `http://localhost:4502/brightcove/admin.html`.

See the maintainers' internal notes ("Admin tool e2e testing") for the full
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
- `fixtures.js` also carries the Sites editor kit: `firstVideos(request)` (live
  videos through the connector's api servlet), `slingPost(request, url, form)`
  (a POST from the cookie session, with the Referer and CSRF-Token headers the
  Sling and Granite filters demand), `createSitesTestPage(request, name)` /
  `deletePage(request, path)` (a per-run page with all three Brightcove
  components, copied from `current/scripts/setup-local-dev.sh`) and
  `editorScrollTop(page)` (the Touch UI editor scrolls `#ContentScrollView`, not
  the window, on both the AEMaaCS SDK and AEM 6.5 shells).
- `specs/sites-editor-components.spec.js` drives the Sites editor: component
  placement, the Video Player / Playlist Player / Experiences dialogs against the
  live account, the BGS-1690 in-place refresh, and the Assets rail's "Brightcove
  Videos" source. It needs the site scaffold from `setup-local-dev.sh` (template
  plus a responsivegrid policy allowing `group:Brightcove`) and fails with a
  named error when that is missing, rather than skipping.
- `cms.js` is a minimal Video Cloud CMS client (create / get / delete a video) for
  specs that need a throwaway video of their own. It reads `BRIGHTCOVE_ACCOUNT_ID`,
  `BRIGHTCOVE_CLIENT_ID` and `BRIGHTCOVE_CLIENT_SECRET` from the environment (source
  your account env file; never commit them). `specs/bgs-1600-metadata-persistence.spec.js`
  uses it and skips, saying why, when they are unset.
- Each `specs/*.spec.js` drives a real user flow and asserts on the rendered UI
  and/or the outgoing `/bin/brightcove/api.js` request.

## Conventions

- One spec per bug/ticket (e.g. `bcon-172-playlist-search.spec.js`).
- Prefer read-only flows. Mutating flows (create label, rename playlist) write to
  the live account — clean up after, and gate them clearly.
- Pin known, unfixed defects with `test.fixme` in the spec for their surface,
  named for the plan item (`§3b-1`, …) or ticket. A pin must be RED on current
  code when written (run it once as `test` to prove it), so the count of
  skipped tests in a run is the count of known open defects, and flipping a
  pin to `test` is part of the fix. `bcon-admin-regressions.spec.js` carries
  the Phase 0 pins; `bcon-186-text-track-upload-feedback.spec.js` the BCON-186
  double-submit one.

## Publish tier and DAM specs (opt-in)

`specs/dam-publish-tier.spec.js` (matrix rows 31-33, DAM ingest, DAM move) and
`specs/dam-workflows.spec.js` (rows 29-30) write DAM assets, activate them and run
the connector's workflows, so they only run when `AEM_PUBLISH_URL` is set, plus the
Video Cloud creds above. Otherwise every test skips as NOT MEASURED, which keeps a
routine gate run from writing to the DAM.

```bash
set -a; . <your test-account env file>; set +a
AEM_BASE=<author> AEM_PUBLISH_URL=<its publish> node_modules/.bin/playwright test specs/dam-*.spec.js
```

Preconditions the publish-tier spec checks and names when missing: an author whose
run modes include `author`, an enabled author replication agent whose transport URI
targets the publish host, and exactly one live Brightcove publish path: the
`brightcove://` replication agent (the on-prem package ships one, enabled) or
`BrightcovePublishListener` with `isEnabled=true` (the cloud opt-in). The listener is
**off by default** (metatype default false and no config ships), so a fresh cloud
instance has no live path until it is enabled; the specs that need one say so and
skip as NOT MEASURED when neither exists.
Each spec's header records the trigger it drives, read from the code.

- `dam.js` is the kit: DAM folder/asset create (`<folder>.createasset.html`),
  metadata writes, JCR move, activation (`/bin/replicate.json`), publish reads,
  workflow start + wait (`/var/workflow/instances`), OSGi component props and the
  Sling log tailer (read-only), and `tracker()`, which records every throwaway and
  removes it in `afterAll` even after a failure.
- `cms.js` gained `tryGet`, `getByRef` (reference_id, which the connector sets to
  the asset's `jcr:uuid`), `sources`, `count`, `folders`, and guarded deletes
  (`delIfThrowaway`, `delFolderIfThrowaway`) that re-read the object and refuse
  anything not named `e2e-throwaway-*`.
- `fixtures/tiny-2s.mp4` is the 17 KB source video for the ingest cases (ffmpeg
  testsrc + sine, metadata stripped).

## Pre-QA gate

`pre-qa-gate.sh` runs version-bump, build+install, deployed-bundle, and
content-package checks, then this suite, before a ticket goes to Ready for QA.
It gates cloud (`:4502`), on-prem (`:4602`), or both, and defaults to probing
both instances. The JSON report is `tests/parity/runs/<date>/gate-<platform>-<port>-<version>.json`,
so runs against different instances do not overwrite each other. See `./pre-qa-gate.sh --help`; details in
`ONPREM-PARITY-PLAN.md` §3 Phase 1.

**Live specs.** The specs that create throwaway videos need the Video Cloud credentials
(`BRIGHTCOVE_ACCOUNT_ID`, `BRIGHTCOVE_CLIENT_ID`, `BRIGHTCOVE_CLIENT_SECRET`) in the
environment; without them they skip, and a run with every live spec skipped looks green
(measured: 59 passed / 25 skipped without credentials, 74 / 10 with). So the gate **fails**
when any of the three is unset, naming the cause. `--allow-no-live` accepts that
explicitly: live coverage is then reported as NOT MEASURED and the last line reads
`PRE-QA GATE PASSED (live specs NOT MEASURED)`, which is not enough for Ready for QA on
its own. `--dry-run` reports the same status and contacts no AEM.

## Write guards

The harness writes to the AEM under test (connector OSGi config via `connector-config.js`,
DAM and CMS throwaways). Config writes refuse a non-loopback `AEM_BASE` unless
`E2E_ALLOW_REMOTE=1` is set on purpose. The mutating parity probes
(`tests/parity/probes/lib/guards.js`) refuse a non-loopback AEM without `--allow-remote`,
only mutate objects named `e2e-throwaway-*` (or created in the same run), and end with a
PASS / FAIL / NOT MEASURED verdict and a matching exit code. Self-test:
`node tests/parity/probes/lib/guards.test.js`.

