---
name: connector-verify
description: Verify a change to the AEM Brightcove Connector with the gate and report PASS / FAIL / NOT_MEASURED verdicts. Use when asked to "verify this change", "run the gate", "gate this", "is this ready for QA", "run the unit tests", or "check for regressions against <release>" (e.g. 7.2.3-cloud, 6.0.12-prem).
---

# Verify a connector change

Read `docs/testing.md` first for the stages, the three verdict states and the baseline loop. Do not restate it here; follow these steps and report in its terms.

1. **Unit gate (no AEM, ~20s).** Always first.
   `tests/e2e/pre-qa-gate.sh --unit-only --platform both --json /tmp/gate-unit.json`
   Add `--java21` when the change touches JVM-dependent code (HTTP, image IO). Stop and fix on FAIL. A new red test is a regression to fix in the code, not a test to rewrite.
2. **Pick the next stage from the kind of change** (table in `docs/testing.md`): a Java-only change with no e2e-visible effect can stop at step 1 and say so. Clientlib/JS, packaging, servlet or UI changes need the full gate.
3. **Build and deploy.** Bump the version if `current/**` changed (AEM will not refresh a same-version bundle). Let the gate build and install: omit `--skip-build`. Use `--skip-build` only when the running instance already has the build under test; the gate's deployed-bundle stage still proves which version runs.
4. **Full gate with JSON.** Source your credentials env file in the same shell command (never print it), then
   `tests/e2e/pre-qa-gate.sh --platform cloud --json /tmp/gate-cloud.json` (or `both`).
   Live specs go through the gate (it takes the per-bed lock and checks the connector proxy config), never a raw `playwright test` next to another run on the same AEM: some live specs swap the connector's proxy for their duration and silently break any concurrent run. If the gate says it is waiting on a bed lock, wait; do not delete the lock unless its owner pid is dead.
   Without the three `BRIGHTCOVE_*` variables every live spec skips: the gate fails unless `--allow-no-live`, and then reports live coverage as NOT_MEASURED.
5. **Regression check, when asked or when the change is risky.** Two runs per side, in the background (a full cloud run takes 9 to 17 minutes; never edit the gate script while one runs). Gate the candidate twice, `tests/gate/deploy-release.sh <tag> <aem-url>` the baseline, gate it twice with `--baseline <version>`, restore the candidate, stop and start the Core Components bundle (`com.adobe.cq.core.wcm.components.core`), prove with a green `--skip-build` gate, then
   `node tests/gate/compare.js --baseline base-1.json base-2.json --candidate cand-1.json cand-2.json`. Only 7.x releases are supported baselines (pre-7.x on-prem is refused; use `tests/parity/matrix.md`). The deploy step replaces the connector on that instance: local instances only. Exact steps: `docs/testing.md`.
6. **Report in verdict terms.** For each platform and stage: PASS, FAIL (with the reason line), or NOT_MEASURED (with why). List skipped e2e tests that self-report NOT MEASURED, coverage % against the previous run, and DEFECT pin counts. Never write "passed" for a run whose JSON says NOT_MEASURED. FLAKY tests are reported as flaky, never as regressions or fixes. A compare result of NOT_MEASURED means nothing was measured on both sides, not that there is no regression.

If the gate's environment cannot be reproduced (no AEM, no credentials, no Java 21), say which stage that leaves NOT_MEASURED and name the risk.
