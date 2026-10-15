# Testing and the gate

How the connector is verified, so a change can be called working on evidence. Entry point: `tests/e2e/pre-qa-gate.sh`. Companion pieces: `tests/gate/` (shared shell lib, summary builder, compare, deploy-release helper), `tests/e2e/` (Playwright specs and kit, see `tests/e2e/README.md`), `tests/parity/` (parity evidence and one-off probes). The agent-facing loop is `.claude/skills/connector-verify/SKILL.md`.

## Verdicts

Every stage ends in exactly one of three states. Treat them as different answers, never as pass/fail with a default.

| Verdict | Meaning |
|---|---|
| PASS | Measured, and met. |
| FAIL | Measured, and not met. The reason line says what. |
| NOT_MEASURED | Could not be decided: missing credentials, no AEM, unreadable endpoint, a stage that was not run, a skipped spec. Absent is not false and not true. |

The run's overall verdict is FAIL if any stage fails, else NOT_MEASURED if any stage is unmeasured, else PASS. The gate exits 0 only for PASS, or for NOT_MEASURED where every unmeasured stage was waived by an explicit flag (`--allow-no-live` for the live specs, `--skip-build` for the build; a `--baseline` run waives the source-level stages). The JSON marks those stages `waived: true`.

## Stages

Per platform (`cloud`, `onprem`; unit tests compile against that platform's API), in order. The gate stops at the first FAIL; stages it did not reach are listed NOT_MEASURED ("not run").

| Stage | What it does |
|---|---|
| `unit` | `mvn -pl core clean test` (on-prem adds `-Daem.platform=onprem`). Parses surefire XML (run, failed, errors, skipped) and the JaCoCo report (connector line and branch %). Errors count as failed. |
| `unit_java21` | Only with `--java21`: the same tests forked onto a JDK 21 JVM (`-Djvm=`), as CI does, and verified from the surefire XML to have run on 21. |
| `bed_lock` | Per-bed run lock on each AEM URL, held to the end of the run (see Bed lock). FAIL if another run holds it past `--lock-timeout`. Not taken by `--unit-only`. |
| `version_bump` | `current/**` changed against `--base` but the pom version did not: FAIL. Files under `src/test` and any `pom.xml` are not counted as changed source (so a pom-only or test-only diff needs no bump), because AEM will not refresh a same-version bundle. |
| `build` | `mvn clean install -PautoInstallPackage` to the AEM port. Waived with `--skip-build`. |
| `deployed_bundle` | Exactly one `brightcove.core`, Active, at the expected version. The wait and judge logic lives in `tests/gate/lib.sh`, shared with `deploy-release.sh`. Duplicate-bundle and stale-bundle traps. |
| `failed_components` | No DS component in failed activation (a package install can cascade an OSGi refresh into Sling's scripting bundles and render the admin tool empty with HTTP 200). Unreadable list is NOT_MEASURED. |
| `clientlib` | `clientlib-tools/js.txt` lists `brcTransport.js`, not `com.iskitz`. |
| `proxy_config` | Preflight before e2e: the connector's `proxyServer` is empty (direct) or the expected value, else FAIL with the observed value (see Bed lock). |
| `e2e` | Playwright over every spec in `tests/e2e/specs` (discovered by Playwright, never listed in the gate). Per-test results are read from Playwright's JSON report; nothing is re-run to gather them. |
| `e2e_live` | Derived from the Playwright results, not from the credentials being set. Live (credential-dependent) specs call `markLive(test)` (tests/e2e/fixtures.js), which annotates each of their tests `live`. PASS when at least one live test passed; FAIL when a live test failed; NOT_MEASURED when none passed (all skipped, e.g. no publish tier) or the three `BRIGHTCOVE_*` variables are unset. Without credentials every live spec skips and Playwright calls that green, so by default the gate FAILS up front; `--allow-no-live` accepts the credentials-missing NOT_MEASURED. Specs that skip for another reason carry their own `NOT MEASURED: ...` reason in the JSON. A new live spec must call `markLive(test)` or its passes do not count. Self-test: `node tests/gate/summarize.test.js`. |

## Commands

```bash
tests/e2e/pre-qa-gate.sh --unit-only --platform both --json /tmp/gate-unit.json   # ~20s, no AEM, no credentials
tests/e2e/pre-qa-gate.sh --unit-only --java21 --json /tmp/gate-unit.json          # plus the Java 21 fork
# source your credentials env file in the SAME shell command, never print it, then:
tests/e2e/pre-qa-gate.sh --platform cloud --json /tmp/gate-cloud.json             # builds + installs, then e2e
tests/e2e/pre-qa-gate.sh --platform cloud --skip-build --json /tmp/gate-cloud.json  # instance already runs the build under test
tests/e2e/pre-qa-gate.sh --platform both --json /tmp/gate-both.json
tests/e2e/pre-qa-gate.sh --dry-run --platform cloud                               # print the plan, run nothing
```

`--help` lists every flag (`--aem-cloud`, `--aem-onprem`, `--base`, `--allow-no-live`, `--baseline`, `--lock-timeout`). Without `--platform` the full gate probes both default URLs and gates whichever answer. The defaults are `http://localhost:4502` (cloud) and `http://localhost:4602` (on-prem); if your on-prem bed is elsewhere (for example a 6.5 LTS bed on 4702) pass `--aem-onprem <url>` or set `BRC_GATE_AEM_ONPREM` once (`BRC_GATE_AEM_CLOUD` for cloud), and when the default is down the gate says so and names both options; `--unit-only` defaults to both. The build and the default unit run always use a JDK 11 (`JAVA_HOME_11_X64`, an ambient 11, or macOS `java_home -v 11`) whatever the ambient `JAVA_HOME`, as CI's default job does, so `--java21` is a distinct check; each unit stage's reason line records the JVM it ran on. The test-run needs Java 11 for the AEM build (`JAVA_HOME`); the unit reason line records the JVM the tests actually ran on.

## Which stages for which change

| Change | Run |
|---|---|
| Java only, no servlet/UI/OSGi wiring effect | `--unit-only --platform both` (add `--java21` for HTTP or image IO code). Say that the live stages were not run. |
| Servlet, OSGi component, workflow step, listener | Unit, then the full gate on a local AEM (bumps the version, builds, deploys, runs e2e). |
| Clientlib or JS, HTL, dialogs, CSS | Full gate. Hard-refresh state is not the gate's concern: the `deployed_bundle` and `clientlib` stages prove the new artifact is what runs. Add a UI check by eye or against the design. |
| Packaging (`ui.*`, `all`, filters, repoinit, OSGi configs) | Full gate on both platforms, plus `python3 -I current/scripts/check-dam-sync-packaging.py` on the built zips (CI runs it). |
| Release | Full gate on both platforms with credentials and no waivers, then the baseline comparison below against the previous release of each line. |

## Bed lock

One AEM instance (a "bed") can only be driven by one run at a time: two agents running e2e against the same instance produced a spurious failure in a live playlist spec. The full gate and `deploy-release.sh` therefore take an exclusive per-bed lock, keyed on the AEM URL, held from just before the version-bump stage (build/install, deployed-bundle check, e2e) until the script exits. Two platforms means two locks, taken in a fixed order (cloud, then on-prem) so two gates cannot deadlock.

- Mechanism: `mkdir` of `${TMPDIR:-/tmp}/bcon-gate-locks/<host>-<port>.lock` (atomic, bash 3.2 safe). The directory holds `pid`, `started` (epoch) and `cmd` of the owner.
- Held by a live process: the run prints `waiting on bed lock held by <pid> (<cmd>) since <time>` (repeated each minute) and polls. After `--lock-timeout <secs>` (default 1800, 30 minutes) it fails: FAIL on the first stage, JSON written.
- Stale lock: the owner pid is dead (crashed or killed run), so the lock is reclaimed with a printed notice. A lock dir with no owner file for over 10 seconds is treated the same way.
- `--unit-only` and `--dry-run` take no lock (they never touch an AEM).
- The JSON records `lock: { waited_secs, beds }`; `waited_secs` is null when no lock was taken. A long wait explains a slow run, not a slow build.
- **Run live specs through the gate**, never as a raw `playwright test` alongside another run on the same bed. Some live specs (`bgs-1600-metadata-persistence`) swap the connector's `proxyServer` to a local logging proxy for their duration, which makes any concurrent live spec on that AEM fail silently (HTTP 200, nothing persisted). Only the gate takes the lock. A single raw spec on a bed nobody else is using is fine.
- `proxy_config` stage, just before e2e: reads the connector's `proxyServer` and FAILs with the observed value (environment failure, not a code defect) unless it is empty (direct) or equals `BRC_GATE_EXPECT_PROXY`. A crashed proxy-swapping run leaves the proxy pointing at a dead local port, and every later live spec then fails silently. Fix by restoring the config (the spec's backup is under `tests/parity/runs/config-backup/`, mode 0600, never print it) and re-running. Unreadable config is NOT_MEASURED.
- The lock is advisory and covers only runs that use these scripts. A human or another tool hitting the same instance is not excluded. If a lock looks wrong, read `cmd` and `started` in the lock directory before removing it.

## Comparing against a baseline release

Detects a regression a new build introduced by running the same specs against the previous release. Use a local instance only: the deploy step replaces the connector.

**Supported baselines:** published 7.x releases of either line (`7.2.3-cloud`, `7.4.0-cloud`, `7.x-prem`). Pre-7.x on-prem releases (`6.0.12-prem`) are **not supported**: they ship bundle `com.coresecure.brightcove.cq5.brightcove-services`, not `brightcove.core`, which would run side by side with a 7.x install, and the gate cannot measure it. `deploy-release.sh` and `--baseline` refuse those tags. For the 6.0.12 behaviour use the 6.0.12 column of `tests/parity/matrix.md`, the comparison source for that line.

Run each side twice. One run per side cannot tell a regression from a flake (the live specs time out under load now and then); the 2+2 rule can.

1. Gate the candidate twice and keep both JSONs: `tests/e2e/pre-qa-gate.sh --platform cloud --json /tmp/cand-1.json`, again to `/tmp/cand-2.json` (credentials sourced).
2. Deploy the baseline: `tests/gate/deploy-release.sh 7.4.0-cloud http://localhost:4502`. It downloads the package with `gh release download`, installs it through the package manager, then waits until the **expected version** is the only `brightcove.core`, Active (a same-name swap passes through a window with no bundle or the old one still Active, 20 to 60 seconds), and fails loudly otherwise (duplicate bundle, stale bundle, failed components). `--dry-run` prints the asset and target without touching anything. It holds the bed lock only while it runs, so another run can slip in between steps; on a shared instance, run the steps back to back.
3. Gate the baseline twice with the same specs: `tests/e2e/pre-qa-gate.sh --platform cloud --baseline 7.4.0 --json /tmp/base-1.json`, again to `/tmp/base-2.json`. The exit code is not the verdict on these runs (specs for newer features fail or skip on an old build); the JSON is. The unit, version-bump and clientlib stages are waived because they judge the checkout, not the release.
4. Put the candidate back: `tests/gate/deploy-release.sh 7.4.1-cloud http://localhost:4502` for a published candidate, or the gate's build step for a local build. After any 7.4.x reinstall, stop and start the Core Components bundle (`com.adobe.cq.core.wcm.components.core`, via `/system/console/bundles/<id>` with `action=stop` then `action=start`), then confirm with a green `pre-qa-gate.sh --platform cloud --skip-build --json ...`. Without the restart the instance can come back with failed components or an empty admin tool.
5. Compare: `node tests/gate/compare.js --baseline /tmp/base-1.json /tmp/base-2.json --candidate /tmp/cand-1.json /tmp/cand-2.json`. The two-argument form `compare.js <baseline.json> <candidate.json>` still works for a single run per side.

**Run these in the background.** A full cloud run takes 9 to 17 minutes (more under load), past a 10-minute tool timeout. Start each gate with `run_in_background` (or `nohup ... &` with output to a log), watch for completion rather than polling, and never edit `pre-qa-gate.sh` while a run is going: bash reads the script as it executes, and a mid-run edit breaks the run.

`compare.js` classifies every test present in either side: REGRESSION (passed in every baseline run and failed in every candidate run), FIXED, STILL_FAILING, FLAKY (mixed passed/failed within a side: reported, never counted as a regression or a fix), NEW_TEST, REMOVED, NOT_MEASURED (skipped or fixme in either side, both reasons shown, or absent from a side that did not measure that kind of test), SAME. A stage waived by a flag on one side (`--baseline` waives unit) is reported as one line, `not compared (stage waived: unit): N tests`, with its coverage and pin lines marked the same way, instead of N NOT_MEASURED rows. It also prints the coverage % change and the DEFECT pin counts. Lost coverage: a test that passed in every baseline run but is NOT_MEASURED or REMOVED in the candidate is listed as `lost coverage: N` and turns a clean result into NOT_MEASURED unless `--allow-lost-coverage` acknowledges it (tests absent on the baseline side are not lost; a REGRESSION still wins). Exit codes: 1 REGRESSION, 3 NOT_MEASURED (nothing was measured on both sides, or lost coverage not acknowledged, so "no regression" is unproven), 0 clean, 2 bad input. A test missing from the baseline (feature absent) is never a pass and never a regression. `node tests/gate/compare.test.js` self-tests it against the fixtures in `tests/gate/fixtures/`: every class, the 2+2 regression and flaky cases (`multi-*.json`), the waived-stage summary, a thin fixture that must yield NOT_MEASURED, and the lost-coverage pair (`lost-*.json`). `bash tests/gate/lib.test.sh` self-tests the shell helpers without AEM (exact bundle-version match, stale-lock reclaim race), and `--baseline` records an e2e failure and carries on to the next platform, so the JSON covers both.

## The JSON

`--json <path>` is written on failure too. Top level: `verdict`, `exit_ok`, `mode`, `git` (sha, branch), `connector_version`, `platforms`, `lock` (seconds waited on the bed lock, beds held), `aem` (per platform: url, deployed `bundle_version`, `bundle_state`), `stages` (platform, stage, verdict, reason, optional `waived`), `unit` and `e2e` (counts per platform), `coverage` (line and branch % per platform), `defect_pins`, and `tests`. Each test: `id` (`unit:<platform>:<class>#<method>` or `e2e:<platform>:<spec file> > <full title>`), `kind`, `platform`, `outcome` (`passed`, `failed`, `skipped`, `fixme`), `reason` (skip or failure text), `defect` (pinned defect). Builder: `tests/gate/summarize.js`.

## Pinning a defect

A known defect gets a test that encodes the CORRECT behaviour and is switched off with a labelled reason, so it shows in every run and flips on when fixed.

- Unit: `@Disabled("DEFECT: <what is wrong, one line>")` on a test asserting the correct behaviour. The gate counts these as `defect_pins.unit_disabled_defect`.
- Playwright: `test.fixme(...)` with a `// DEFECT: <what is wrong>` comment on the line above or just inside it. The gate counts fixme tests as `e2e_fixme_total` and those with `DEFECT` nearby as `e2e_fixme_defect`. Convention: `// DEFECT:` marks a defect pin (counted in `e2e_fixme_defect`); `// LIMITATION:` marks a fixme that documents intended or accepted behaviour, not a pending fix (counted in `e2e_fixme_limitation`). The label is the contiguous `//` comment block directly above `test.fixme`, or the same line. An unlabelled fixme counts in the total only, so label every new one.

Fixing the defect means removing the `@Disabled` or `fixme` in the same change; compare then shows the pin count drop. Never delete or loosen a pin to get green.

## Feature to test map

Unit tests are under `current/core/src/test/java/com/coresecure/brightcove/wrapper/`, e2e specs under `tests/e2e/specs/`.

| Feature | Unit | E2E |
|---|---|---|
| Account config | `sling/ConfigurationGrabberImplTest`, `ConfigurationServiceImplTest`, `LegacyConfigurationServiceImplTest`; `utils/CredentialLogRedactionTest` | `smoke` |
| Multi-account | none | `bcon-178-account-switch-confirm`; `bcon-178-account-switch-live` (needs a second account configured, else NOT_MEASURED) |
| Video list and search | `sling/ServiceUtilGetListFirstPageFilterTest`, `ServiceUtilSearchVideoMissingNodeGuardTest`; `objects/VideoNullSafetyTest` | `smoke`, `bcon-183-190-sort-columns`, `bcon-179-181-layout`, `bcon-admin-regressions` (BCON-191 count; search fixme pins) |
| Upload (images, ingest) | `utils/S3UploadUtilTest` (2 DEFECT pins); `webservices/BrcApiUploadImageHttpsGuardTest` | `bcon-184-image-url-inline`, `bgs-1600-metadata-persistence` (poster/thumbnail ingest). Video file upload: not covered |
| Delete | `webservices/BrcApiActionDispatchTest` (delete_video, DEFECT pin); `workflow/BrightcoveDeleteAssetWorkflowStepTest` (DEFECT pin) | `dam-workflows` (delete-asset workflow). Delete video: the admin tool has no delete-video action, so there is no UI path to test |
| Labels | `api/CmsApiUpdateLabelsTest`, `webservices/BrcApiUpdateLabelsTest` | `bcon-182-create-label`, `bcon-label-persistence`, `bgs-1600-metadata-persistence` |
| Posters and thumbnails | `webservices/BrcImageApiProxyTest`, `BrcApiUploadImageHttpsGuardTest`; `utils/FailingImageReader` (fixture) | `bcon-184-image-url-inline`, `bgs-1600-metadata-persistence` (row 11) |
| Text tracks | `webservices/BrcApiActionDispatchTest` (remove_text_track, DEFECT pin) | `bcon-186-text-track-upload-feedback`, `bcon-187-text-tracks-section` |
| Folders | `utils/FolderSyncUtilTest`, `FolderSyncUtilNestedSubfolderTest`, `FolderSyncUtilPoisonedRootTest`; `webservices/BrcReplicationHandlerFolderSyncTest`; `listeners/BrightcovePublishListenerFolderSyncTest`; `workflow/BrightcoveSyncAssetWorkflowStepFolderSyncTest` | `dam-publish-tier` (DAM subfolder to Video Cloud folder; move fixmes), `bcon-admin-regressions` (move-to-folder fixme), `admin-folder-mutation` (live move of a video into a folder, the folder created through the CMS API, no rename; DEFECT fixme: no UI path to remove a video from its folder) |
| Playlists | `objects/PlaylistNullSafetyTest`; `webservices/BrcApiActionDispatchTest` (update_playlist, DEFECT pin) | `bcon-172-playlist-search`, `bcon-174-playlist-edit`, `bcon-admin-regressions` (BCON-147, BCON-172). `admin-playlist-mutation` (live create/edit/delete; DEFECT fixme: removing a video sends two `update_playlist` requests, one racing to a 500) |
| Variants | none | `bcon-176-177-variant-dialog` (dialog validation only). `admin-variant-mutation` (live variant CRUD; DEFECT fixme: deleting the last variant is not persisted to the DAM asset) |
| Player, playlist, experience components | none | `sites-editor-components` (placement, dialogs against the live account, BGS-1690 refresh), `bcon-pml-dialog-layout` (dialog layout) |
| Asset finder | `schedulers/asset_integrator/impl/AssetProcessingRequesterImplTest` | `sites-editor-components` (Assets rail "Brightcove Videos" source) |
| DAM import scheduler | `schedulers/asset_integrator/**` (cron bundle, `VideoImportCallable*`, `AssetPropertyIntegratorRunnableTest`); `webservices/AssetPropertyIntegratorTest`; `sling/ServiceUtilDamOnlyDefaultTest`, `ServiceUtilTagIsolationTest`, `ServiceUtilReferenceIdTest` | `bgs-1600-no-dam-tag`, `bgs-1600-metadata-persistence` |
| Publish and replication listeners | `listeners/BrightcovePublishListener*Test`, `BrightcoveMoveListenerNoOpTest`; `webservices/BrcReplicationHandlerAuthorizationTest`; `utils/HttpServicesPatchFallbackTest` (PATCH, JVM dependent, hence `--java21`) | `dam-publish-tier` (needs `AEM_PUBLISH_URL`, else NOT_MEASURED) |
| Workflows | `workflow/BrightcoveSyncAssetWorkflowStepDuplicateTest`, `...FolderSyncTest`, `BrightcoveDeleteAssetWorkflowStepTest` | `dam-workflows` (needs credentials and `AEM_PUBLISH_URL`) |
| Context path and Load transport | none (clientlib JS) | `bcon-context-path`, `bgs-1706-load-transport`; gate `clientlib` stage |
| Packaging | none | gate `deployed_bundle`, `failed_components`, `clientlib` stages; CI `current/scripts/check-dam-sync-packaging.py` on both built zips |

A feature with "none" in a column is not verified by that layer. Say NOT_MEASURED for it, do not infer coverage from the neighbouring row.

## Keeping the kit honest

- A fresh clone or worktree has no Playwright install: `cd tests/e2e && npm ci && node_modules/.bin/playwright install chromium`. The gate uses `tests/e2e/node_modules/.bin/playwright` in its own tree and never reaches into another checkout.
- Specs are discovered, not listed: add a file under `tests/e2e/specs/` and the gate, the JSON and the compare pick it up.
- A spec that cannot measure something must `test.skip(cond, 'NOT MEASURED: <why>')`, never pass vacuously.
- App-agnostic helpers belong in `tests/e2e/fixtures.js` and friends or `tests/gate/`; extend them rather than writing a one-off probe.
- Public repo: no customer names, real account IDs, personal paths, internal hostnames or secrets in specs, fixtures or docs. Read the account and fixture objects at run time.

## Known flakes

- `bcon-context-path.spec.js` "Touch UI: the helper is loaded and a real context-path source answers": a request timeout in `resolveAccountId` (`fixtures.js:33`), once in three regression runs. A timeout there is the instance being slow, not the helper; re-run before reading it as a regression (the 2+2 compare classes it FLAKY).
- `bcon-183-190-sort-columns.spec.js:37` ("sorting by another supported column (Name) reorders without clearing the list") failed 9 times under heavy concurrent read load against one AEM. Not investigated. Re-run it alone on a quiet bed before treating it as a regression; the bed lock exists so that load does not come from another gate run.
