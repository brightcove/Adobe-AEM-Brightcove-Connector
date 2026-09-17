# AEM Brightcove Connector: on-prem / cloud feature parity from one branch

Handoff plan for the agent overseeing this work. Written 2026-09-17 from a measured
survey of the repo and both local AEM runtimes. Every fact below carries the command
that produced it so it can be re-verified rather than trusted.

Public repo. Ticket IDs only in commits, branch names, comments and this file. No
customer names, account IDs or internal URLs. See the wiki page
`aem-connector-release` "Public repo" gotcha and the 2026-06-02 leak decision record.

---

## 0. Goal and the one architectural decision

**Goal.** Both AEM runtimes ship the same connector features from the same commit:

| Line | Runtime | Today | Target |
|---|---|---|---|
| Cloud | AEM as a Cloud Service (AEMaaCS SDK) | `cloud-master`, pom `7.2.3`, tag `*-cloud` | same commit as on-prem |
| On-prem | AEM 6.5 (uber-jar) | `onprem-master`, pom `6.0.12`, tag `*-prem`, last code 2026-04 | same commit as cloud |

**Decision: one mainline, one source tree, two build profiles, two artifacts.**
No second copy of `core` or `ui.apps`. Runtime differences are handled by Maven profile
(dependency + module selection + artifact classifier) and by small on-prem-only overlay
modules, never by forking Java or JCR content.

**Why this is feasible, measured not assumed.** In a detached worktree of `cloud-master`
the `core` bundle was compiled with its single `aem-sdk-api` dependency swapped for the
AEM 6.5 uber-jar. Result:

| uber-jar | Result | What failed |
|---|---|---|
| `6.5.0` (GA, `apis` classifier) | 30 errors in ~8 files | `JsonNode.toPrettyString()` (needs Jackson ≥ 2.10; 6.5.0 GA ships 2.9.5) and `org.apache.sling.api.request.builder.Builders` in `utils/SlingUtils.java` (needs Sling API ≥ 2.24; 6.5.0 GA ships 2.18.4) |
| `6.5.22` (6.5 LTS line, no classifier) | **BUILD SUCCESS, zero source changes** | nothing; that jar carries Jackson 2.16.2 and has `Builders` |

So the whole Java surface of the cloud connector already compiles for AEM 6.5 LTS. The
on-prem line is behind by ~180 commits of features, not by any platform incompatibility.
Reproduce:

```bash
git worktree add --detach /tmp/bcon-probe origin/cloud-master
cd /tmp/bcon-probe/current
# in core/pom.xml replace the aem-sdk-api dependency with
#   com.adobe.aem:uber-jar:6.5.22 (scope provided, no classifier)
JAVA_HOME=$(/usr/libexec/java_home -v 11) mvn -pl core -am -DskipTests -Dbnd.baseline.skip=true compile
git -C ~/Documents/brightcove/internal/connectors/Adobe-AEM-Brightcove-Connector worktree remove /tmp/bcon-probe --force
```

**AEM 6.4, measured 2026-09-17:** the same probe against uber-jar `6.4.8.4` (last 6.4 SP),
with the JavaEE/JCR API jars the 6.4 uber does not bundle supplied as provided deps, leaves
exactly one real gap: the same Sling `Builders` usage, plus one unused Oak import. `6.4.0`
GA adds the `toPrettyString` gap (Jackson 2.8.4). So 6.4 is not a different platform for
this code; it is an unsupported one (absent from Adobe's release roadmap, which lists
classic 6.5 on-prem core support ending February 2027 and steers to 6.5 LTS), and there is
no 6.4 runtime here to verify against. Compile-for, do not claim-support.

⚠️ Measured on the built cloud jar: `Import-Package` carries `javax.servlet;version="[4.0,5)"`
and `jackson.databind;version="[2.19,3)"`. The 6.5.0 runtime exports Servlet 3.1 and
Jackson 2.9.5. The current cloud artifact cannot resolve on 6.5.0 regardless of source
compatibility; the on-prem artifact must be compiled against the floor uber-jar.

**Support floor: DECIDED by Laurence 2026-09-17: AEM 6.5 LTS** (the line customers are
migrating to per BGS-1671).
6.5.0 GA without a service pack is *not* a target, but the local instance IS 6.5.0 GA, so
Phase 2 shims the two API usages above anyway. That costs about ten lines, makes the local
instance a valid test bed, and loosens the bnd `Import-Package` ranges so the bundle
resolves on older 6.5 service packs too. Confirm the exact 6.5 service-pack ↔ Jackson/Sling
version mapping against Adobe's release notes before publishing a compatibility matrix
(BGS-1671 asked for one; nobody has published it).

---

## 1. Facts established (re-verify with the commands shown)

### 1.1 Branch topology

```bash
cd ~/Documents/brightcove/internal/connectors/Adobe-AEM-Brightcove-Connector && git fetch origin --prune
git merge-base origin/cloud-master origin/onprem-master        # d8f8132, 2021-05-03, "release/5.6"
git rev-list --left-right --count origin/onprem-master...origin/cloud-master   # 75 / 182
git rev-list --left-right --count origin/master...origin/onprem-master         # 10 / 25
```

- `cloud-master` and `onprem-master` forked in **May 2021** and have not merged since.
- `git cherry origin/cloud-master origin/onprem-master`: **60 on-prem commits have no
  patch-equivalent on cloud**, 3 do. Many were re-implemented on cloud differently
  (labels, variants, subfolder sync), so parity must be judged per *feature*, not per commit.
- `master` is a **third** diverged line: it has the BGS-1706 ajile *deletion* (`963c066`,
  2026-07-11) but **not** the `brcTransport.js` `Load()` restore. 🔴 A build off `master`
  has a broken Brightcove Admin tool (25 `Load(` call sites, no definition). Same failure
  shape as `7.2.2-cloud`. `master` is also the only protected branch and nothing releases
  from it (wiki: `aem-connector-repo-governance`).
- 🔴 **The live working tree at that path is Laurence's.** It has untracked planning files
  and often a feature branch checked out. Never `checkout`/`pull` there. All work in this
  plan happens in `git worktree add` directories.

### 1.2 Module layout

| Module | cloud-master | onprem-master | Shared in target? |
|---|---|---|---|
| `core` (Java bundle) | DS annotations (`org.osgi.service.component.annotations`, 20 files), bnd-maven-plugin | Felix SCR annotations (16 files), maven-scr-plugin, Java target 1.8 | **Yes**: cloud `core` as-is. 6.5 runs Felix SCR 2.1.16 = DS 1.4, fine. |
| `ui.apps` | `/apps/brightcove/{clientlibs,components,extensions,templates,workflow,i18n}` | same plus `/etc/clientlibs/brightcove`, `/etc/designs/cs/brightcove/*`, `/apps/brightcove/runmodes/*`, `/home/users/system/...`, `/etc/replication/agents.author/brightcove` | **Yes** (cloud layout). 52 files were pure renames `/etc/designs/...` → `/apps/brightcove/clientlibs/clientlib-*`. On-prem legacy paths become a compat overlay (§3 Phase 4). |
| `ui.content` | `/conf/brightcove`, `/content/brightcovetools`, workflow models `bc-sync-new-asset`, `brightcove-delete-asset` | (inside ui.apps) models named `brightcove-sync-asset-workflow`, `brightcove-delete-asset-workflow` | **Yes**, plus legacy model names in the overlay. |
| `ui.config` | `.cfg.json` under `/apps/brightcove/osgiconfig/config{,.prod,.stage}`; repoinit creates `brightcove_admin` service user | `.config` under `/apps/brightcove/runmodes/config{,.author,.publish,.author.dev,.publish.dev}` incl. `LoginAdminWhitelist` fragment | **Yes**. 6.5 supports `.cfg.json` and repoinit (`org.apache.sling.jcr.repoinit 1.1.8` active on the local 6.5.0). `loginAdministrative` is used by **neither** branch, so the whitelist fragment is dead weight: drop it. |
| `ui.apps.structure`, `it.tests`, `ui.tests` | archetype scaffolds | absent | Yes (harmless on 6.5). |
| `all` | embeds core, ui.apps, ui.content, ui.config **and WCM Core Components** into `/apps/brightcove-packages/*/install` | absent (two separate packages) | Yes. Verify the embedded Core Components version installs on 6.5 LTS, or make that embed cloud-only. |
| `analyse` | `aemanalyser-maven-plugin` | absent | **Cloud profile only.** |

### 1.3 The OSGi configuration key break (silent, customer-facing)

`ConfigurationServiceImpl` exists on both branches under the same class name, but the
**factory PID and the property keys both differ**, and on-prem customers' existing factory
configs use the old ones. Measured live 2026-09-17: on 6.0.12 the DS component is declared
`@Component(name = "com.coresecure.brightcove.wrapper.sling.BrcServiceImpl", configurationFactory = true)`,
so the on-prem factory PID is **`...sling.BrcServiceImpl`**; a config posted under the cloud
PID `...sling.ConfigurationServiceImpl` is silently ignored (no component listens). Cloud's
factory PID is `...sling.ConfigurationServiceImpl`. Phase 4's legacy fallback therefore has to
cover **both** the PID (a second `@Designate`/`configurationPid`, or a one-time config
migration on activate) **and** the keys:

| on-prem 6.0.x key | cloud 7.x key |
|---|---|
| `key` | `accountId` |
| `client_id` / `client_secret` | `clientId` / `clientSecret` |
| `allowed_groups` | `allowedGroups` |
| `playersstore` | `playerStorePath` |
| `defVideoPlayerID`, `defPlaylistPlayerID` (+ `*Key`, deprecated) | `defaultVideoPlayerId`, `defaultPlaylistPlayerId` |
| `proxy` | `proxyServer` |
| `asset_integration_path` | `damIntegrationPath` |
| (none) | `defaultIngestProfile` |

Also measured: the on-prem accounts servlet lists an account only if its `allowed_groups`
intersects the caller's groups, and on a stock 6.5.0 `admin` is a member of `everyone` only,
not `administrators`. An account with `allowed_groups=administrators` is invisible to admin.
Cloud filters identically (`BrcAccounts.java` lines 123-144 on `cloud-master`) and the working
:4502 config uses `allowedGroups=[everyone]`, so this is shared behaviour, not a parity gap;
it belongs in the on-prem upgrade doc as a setup note.

Per the README note added under BGS-1699, an unrecognised key **silently falls back to the
Meta Type default**. An in-place upgrade from 6.0.12 with camelCase-only reading would
therefore point every customer at the default DAM path and lose their credentials with no
error. Phase 4 must add legacy-key fallback in the shared `core` (read camelCase, else
snake_case, log a deprecation warning once).

### 1.4 What each line has that the other lacks (feature level, to be confirmed live in Phase 0)

**Cloud has, on-prem lacks** (everything since 2021; highlights that matter to customers):
- Java 17/21 `PATCH` fix in `HttpServices` (BGS-1600 commit `79d0e54`). On-prem 6.0.x
  metadata sync-back is **broken on Java 21** today; BGS-1671 and BGS-1677 are open asks
  for exactly this. The shared core delivers it for free.
- Duplicate-video guard on concurrent/redelivered publish (BGS-1705).
- Publish-path folder sync (BGS-1600), editor `REFRESH_SELF` scroll fix (BGS-1690).
- `Load()` JSONP transport without the ajile vendor bundle (BGS-1706): `brcTransport.js` +
  regression spec `tests/e2e/specs/bgs-1706-load-transport.spec.js`.
- The whole 7.x Brightcove Admin revamp (BCON-121…195): header/search/filter, bulk actions,
  playlists table, video detail panel, labels create/apply, variants dialog, text tracks,
  ingest outcome toasts, account-switch confirm.
- `ui.config` repoinit service user; `defaultIngestProfile`; camelCase config.
- Playwright e2e harness (`tests/e2e`, 15 specs) and `pre-qa-gate.sh`.

**On-prem has, cloud may lack** (60 unported commits; candidates to check, with the commit
that introduced each):
- `c100a16`, `cf674b7` (2024-02/03): **context-path prefixing** in `brcUI.js`, `brcAdmin.js`
  and the three `extensions/contentfinder/*.js`. Cloud JS has **zero** `contextPath`
  references; on AEMaaCS this never matters, on-prem behind a dispatcher context root it does.
- `3b27f2f`, `48b94cc` (2024-11): list **all playlists** (pagination removed) in
  `GetLocalAssetList.java`.
- `d3743ea` (2025-02): **proxy** honoured on rendition creation, `ServiceUtil.java`.
- `cdf5d26` (2024-10): multi-file sync fix.
- `ccdaeb2` (2023-12): `AEM_NO_DAM` tag filter respected on full-scroll import.
- `04a2e70` (2025-08): `BrightcoveExperiences.js` update.
- `f90d3b0` (2023-04): lodash bump for 6.5.10+.
- `748bc76` (2024-03): "new way to sync from AEM to Brightcove" (adds the two workflow
  models under their on-prem names; cloud has equivalents under different names).

For each: `git show <sha>` on the on-prem branch, find the equivalent code on cloud, and
record one of: *already present*, *port needed*, *obsolete*. Do not port by cherry-pick
(paths moved); port by hand into the cloud layout with the on-prem commit referenced in
the message.

### 1.5 Local runtimes

| | Cloud SDK | On-prem 6.5.0 GA |
|---|---|---|
| Jar | `~/opt/aem/aem-author-p4502.jar` (AEMaaCS SDK `2026.2.24678`) | `~/Downloads/cq-author-p4602.jar` (`cq-quickstart-6.5.0`, no SP) |
| Port | 4502 | **4602** |
| Java | 11 | Corretto **11** (pid via `pgrep -f cq-author-p4602`) |
| State 2026-09-17 | not running | **running, licensed** (`productinfo` = `Adobe Experience Manager (6.5.0)`; `/crx/packmgr/service.jsp` and `/sites.html` resolve to themselves, no license redirect), 581 bundles active, `we-retail` + `wknd-events` sample content, **no connector installed**, no Brightcove account configured |
| Libraries | current | Jackson **2.9.5**, Sling API **2.18.4**, httpclient 4.5.4, repoinit 1.1.8 |

Boot recipe, readiness check, and the license gotcha (`curl -L` shows 200 for the license
page, check `%{url_effective}`) are on wiki page `aem-connector-local-dev`. A Temurin 21
JDK is also installed for a Java 21 run of the on-prem instance later (6.5 LTS supports
Java 21; 6.5.0 GA does not officially).

### 1.6 Verification assets that already exist

- `tests/e2e` Playwright suite. Already parameterised: `AEM_BASE`, `AEM_USER`, `AEM_PASS`.
  Logs in via `j_security_check` (global-setup), drives `/brightcove/admin.html`, asserts on
  rendered rows and the `/bin/brightcove/api.js` calls. Needs a configured account
  (`GET /bin/brightcove/accounts` non-empty).
- `tests/e2e/pre-qa-gate.sh`: version-bump check, build+install, deployed-bundle-version
  check, e2e. 🔴 Hard-codes `origin/cloud-master` as the diff base and assumes one instance.
- `current/scripts/setup-local-dev.sh` (untracked in the live tree): scaffolds a test site
  and page with all three components. Honors `AEM_PORT`.
- Unit tests: 2 files on each branch. Effectively none.
- No CI on any branch (`.github/` absent).

---

## 2. Target end state

```
main  (renamed from cloud-master once Phase 3 passes; protect it, see governance page)
 └─ current/
     ├─ pom.xml            profiles: cloud (activeByDefault)  |  onprem (-Ponprem)
     ├─ core/              ONE bundle. dependency = aem-sdk-api  |  uber-jar ${aem.uber.version}
     ├─ ui.apps/           shared JCR content, cloud layout
     ├─ ui.content/        shared
     ├─ ui.config/         shared runmode configs (config, config.author, config.publish)
     ├─ ui.config.onprem/  onprem profile only: dispatcher/replication-agent bits if any survive review
     ├─ ui.compat.onprem/  onprem profile only: legacy /etc/designs + /etc/clientlibs proxy shims,
     │                     legacy workflow-model names, anything a 6.0.x customer's content points at
     ├─ all/               embeds the above; classifier -prem in onprem profile
     ├─ analyse/           cloud profile only
     └─ it.tests, ui.tests, ui.apps.structure
tests/e2e                  one suite, run twice (AEM_BASE=…4502 and …4602), results diffed
tests/parity/              matrix runner + report (new, small)
legacy/onprem-6.0.x        = old onprem-master, frozen, hotfix-only for customers who cannot move
legacy/master-2021         = old master, frozen (it is broken, see 1.1)
```

Release = one commit, one pom version, two tags (`X.Y.Z-cloud`, `X.Y.Z-prem`), assets
`brightcove.all-X.Y.Z.zip` and `brightcove.all-X.Y.Z-prem.zip` (plus `ui.apps` zips if the
convention on the release page is kept). Unify the on-prem version line onto **7.x**; OSGi
sees `6.0.12 → 7.3.0` as an upgrade, and the `-prem`/`-cloud` suffix already disambiguates.

Rules the shared code follows:
- No `if (isCloud)` forks in Java. If a platform difference is unavoidable, isolate it
  behind an interface with two DS components selected by a config or by optional
  `Import-Package` resolution, and document why in `current/docs/`.
- The **on-prem artifact is compiled against the floor uber-jar**, never a newer one, so
  bnd computes `Import-Package` ranges the floor runtime can satisfy. Compiling against
  `6.5.22` and deploying to `6.5.0` will fail to resolve on Jackson even after the
  `toPrettyString` shim; the range is the thing.
- Version bump on every deploy (`pre-qa-gate` step 1) or AEM serves the stale bundle.

---

## 3. Phases, each with an exit gate

Work in a worktree: `git worktree add ../bcon-parity -b feat/unified-build origin/cloud-master`.
**Created 2026-09-17** at `~/Documents/brightcove/internal/connectors/bcon-parity`; Phase 0 started the
same day (matrix skeleton + `tests/parity/` in place, baselines dispatched to two Sonnet agents).
Open PRs against `cloud-master` per phase (merge-commit, the repo convention). Keep the
`onprem-master` and `master` branches untouched until Phase 5.

### Phase 0. Baselines (no code changes)

1. Build `onprem-master` at `6.0.12` in a detached worktree and deploy to :4602
   (`mvn clean install -PautoInstallPackage -Daem.port=4602`, JDK 11). Confirm bundle
   `brightcove_connector.core` Active and `/apps/brightcove` present.
2. Configure one Brightcove account on :4602 (factory config, on-prem keys). Use the same
   test account the cloud e2e suite uses (its ID is in the e2e README, not here).
3. Run `setup-local-dev.sh` with `AEM_PORT=4602`. Record what fails: the script was written
   for the cloud layout.
4. **Write the feature matrix** `tests/parity/matrix.md`: one row per user-visible
   capability (admin tool areas, components, DAM tab, workflows, publish listener, folder
   sync, labels, variants, text tracks, ingest profile, proxy, context path, multi-account),
   columns *cloud 7.2.3 on :4502* and *on-prem 6.0.12 on :4602*, values from actually
   exercising each, with a screenshot path. Every cell is `works` / `broken` / `absent` /
   `not measured`. `not measured` is a valid value; never default it to `works`.
5. Run `tests/e2e` against :4602 with the 6.0.12 deploy. Expect most specs to fail on
   selectors (the admin UI is the old one). Record pass/fail per spec as the *before*.
6. Start :4502 and re-run the cloud suite there as the cloud *before* (must be green).
   **Measured 2026-09-17: 28/34 green on a clean instance; the other 6 are spec rot** (each
   asserts behaviour a later June-2026 commit deliberately changed: left-only panel border
   `1239c07`, account-switch toast removed `e180507`, label path trailing slash `5a9cfc8`,
   ID sort column removed `9608e11`, poster save moved from POST to JSONP GET `764e5f2`).
   Triage evidence: `tests/parity/runs/2026-09-17/cloud-7.2.3/triage/NOTES.md`. The six specs
   were updated to the current intended behaviour the same day (test-only edits, each proven
   red on a flipped assertion): **35/35 green** on clean 7.2.3, the cloud gate for Phase 1.
   `bcon-183-sort-by-id` became `bcon-183-190-sort-columns` (two tests). ⚠️ Environment lesson: the instance had an orphaned
   `brightcove.core 7.2.2` co-Active with 7.2.3; uninstalling it without `refreshPackages`
   left the accounts servlet returning 500 `bundleContext is null`. Always refresh after an
   uninstall, and check for duplicate bundles before trusting any run.

**Phase 0 COMPLETE 2026-09-17.** All six steps done; matrix filled on both columns
(`tests/parity/matrix.md`, three-state values, evidence per cell). Feature-level headlines:
- Cloud-only (on-prem `absent`): playlist search by name and playlist rename (row 17/18),
  language variants (14), bulk action bar (19), confirm-on-account-switch (20), metadata
  edit dialog (10: on-prem's is dead code even when force-invoked), Content Finder
  Brightcove tab (27: on-prem targets the ExtJS sidekick the Touch UI never loads).
- On-prem-only (cloud lacks): a UI path to move a video OUT of a folder (15).
- Shared defects found on both builds: removing the last label is a silent no-op with a
  success toast (13); no reachable upload/ingest UI at all (21, `extFormUpload` dead on
  both). See section 3b.
- BGS-1690 (26): on-prem still ships `REFRESH_PAGE` listeners and the ContentFrame does
  reload, but the 6.5.0 Touch UI shell preserves the outer scroll, so the visible jump does
  not reproduce there. The listener port is still part of parity.
- On-prem 6.0.12: built from `cdf0707` with zero source changes, installed to :4602
  (`Package installed in 1539ms`), bundle Active, admin tool at the SAME `/brightcove/admin.html`
  vanity path as cloud, setup script ran clean on the on-prem layout. Account config took
  four attempts (wrong PID, group gate, blanked credentials from a partial `propertylist` save
  with mis-named env vars); all recorded in `runs/2026-09-17/onprem-6.0.12/NOTES.md` §3.
- e2e on 6.0.12 (run 4, the baseline): **6 pass / 1 skip / 28 fail**; zero login or
  no-rows failures; 22 fails are 7.x-only admin features (variants, bulk bar, label create,
  inline image URL, text-track upload, redesigned panel, confirm-on-switch), 6 are old-vs-new
  markup on shared features (playlist search/edit).
- e2e on cloud 7.2.3: **35/35** after the spec repair (28/34 before, 6 spec rot).

Exit gate: matrix filled, both *before* e2e runs archived under `tests/parity/runs/<date>/`.

### Phase 1. Unified build, cloud artifact unchanged

1. Add profiles to `current/pom.xml`: `cloud` (modules incl. `analyse`, `aem-sdk-api`) and
   `onprem` (`uber-jar` `${aem.uber.version}` = 6.5.22, no `analyse`, `-prem` artifact name).
   **Landed 2026-09-17.** Design notes, both learned the hard way:
   - Profiles are selected by the **property** `-Daem.platform=onprem` (cloud = property
     absent), not by `-P`. An explicit `-P` deactivates every `activeByDefault` profile in the
     same POM, which here is `adobe-public` and its repositories. Same two profile ids and
     activation are repeated in `core/pom.xml` (platform dependency) and `all/pom.xml`.
   - The on-prem name is set with `<finalName>…-prem</finalName>`, not filevault's
     `<classifier>`: with plugin 1.1.6 the classifier makes `generate-metadata` write
     `target/vault-work-prem` while `package` still reads `target/vault-work` and fails with
     `basedir … vault-work does not exist`. `finalName` also keeps `autoInstallPackage`
     pointed at the right file.
   - Measured: cloud reactor = 10 modules incl. the analyser; on-prem = 9. Cloud package
     listing identical to the Phase 0 baseline. On-prem `core` imports
     `jackson.databind;[2.16,3)` (from the 6.5.22 floor) and `javax.servlet;[2.6,3)`, both
     satisfiable on the local 6.5.0 (it exports servlet 2.6/3.0/3.1; Jackson needs the Phase 2
     shim or a service pack).
2. Wire `aem.host`/`aem.port` defaults per profile (4502 / 4602) so
   `mvn -Ponprem -PautoInstallPackage` targets the right instance without `-D`.
3. Generalise `pre-qa-gate.sh`: diff base and instance from args/env; run the e2e twice
   when both instances are up. **Partly done 2026-09-17 during Phase 0:** `tests/e2e/target.js`
   now derives the login-state file and results dir from `AEM_BASE`, because two concurrent
   runs (cloud :4502, on-prem :4602) shared one `.auth/state.json` and, since `localhost`
   cookies ignore the port, one run silently drove the other's instance. Both first-pass
   baselines were re-run after the fix. **Completed 2026-09-17:** `pre-qa-gate.sh` now takes
   `--platform cloud|onprem|both` (default: probe both instances' `bundles.json`, gate on
   whichever answer, error if neither), `--base`/`PARITY_BASE_REF`, `--aem-cloud`/`--aem-onprem`,
   `--skip-build`, and `--dry-run`. It runs the right Maven invocation per platform, fails on
   more than one `brightcove.core` bundle (the Phase 0 duplicate-bundle trap, printing each
   bundle's id), checks `clientlib-tools/js.txt` for `brcTransport.js`/absence of `com.iskitz`,
   and writes each platform's e2e JSON report to `tests/parity/runs/<date>/gate-<platform>-<pomversion>.json`.
4. Add `.github/workflows/build.yml`: matrix `{cloud, onprem}` × `mvn -DskipTests package`,
   upload both `all` zips as artifacts. Compile-level parity guard from day one.
   **Done 2026-09-17:** `.github/workflows/build.yml` builds both platforms on push/PR and
   uploads both zips; an `e2e-static` job runs `playwright test --list`. It does NOT deploy or
   run e2e (no AEM in CI); that remains `tests/e2e/pre-qa-gate.sh`, now platform-aware
   (`--platform cloud|onprem|both`, duplicate-bundle and content checks, per-platform JSON
   reports, `PARITY_MVN_FLAGS_ONPREM` for the local GA floor).

   **Completed 2026-09-17:** the workflow builds both platforms on push (`cloud-master`,
   `main`, `release/**`, `feat/**`) and on pull request, using `actions/setup-java@v4`
   (temurin 11, Maven cache) and the same `mvn clean package` / `mvn -Daem.platform=onprem
   clean package` invocations documented above, then `unzip -l`s and uploads each `all` zip
   as `brightcove-all-cloud` / `brightcove-all-onprem`. A third job, `e2e-static`, runs
   `npm ci` and `npx playwright test --list` in `tests/e2e` to catch spec syntax errors. It
   does **not** cover: no AEM instance runs in CI, so there is no deploy, no bundle/content
   check, and no actual e2e execution against a live instance. That remains
   `pre-qa-gate.sh`'s job, run locally before Ready for QA.

Exit gate: `mvn clean package` (cloud, default) produces a `brightcove.all-7.2.3.zip`
whose **content listing is identical** to the pre-change build (`unzip -l | sort | diff`).
Cloud e2e still green on :4502. `mvn -Daem.platform=onprem clean package` succeeds and yields
`brightcove.all-7.2.3-prem.zip`. Bump pom to `7.3.0` at the end of this phase.

**Gate log 2026-09-17.** Listing identical: yes. On-prem package: yes (9-module reactor,
core against uber 6.5.22). Cloud deploy of the profile-built artifact to :4502: single
`brightcove.core 7.2.3` Active, content check clean. e2e: the first full run landed on a
laptop DNS outage (`UnknownHostException: oauth.brightcove.com` ×12 in `brightcove.log`,
13 specs timed out at 60s) and is recorded as an environment finding, not a regression
(`runs/2026-09-17/phase1/cloud-e2e-after-profiles.txt`). Re-runs of the 13 after the
network returned: 14/15 green; the one holdout, `bcon-186` "loading state", was a spec race
(250ms mocked delay vs. assertion polling under load) and was widened to 1.5s and reordered,
then 3/3 green in isolation but not inside the suite (a real product race, §3b item 7), so
that one assertion moved to a `test.fixme`. **Final full-suite gate: 35 passed / 1 skipped
(`cloud-e2e-gate-full2.*`).** Pom bumped to 7.3.0 in all 10 module poms.
⚠️ Playwright 1.60 quirk: passing seven spec-name filters at once returned "No tests found"
while up to four worked; run large re-run sets in batches.

### Phase 2. On-prem artifact runs on 6.5

1. Shim the two API gaps in shared code (pure Java, benefits cloud too):
   `toPrettyString()` → `mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)`
   (30 sites; a one-line helper in `utils/`), and replace the `Builders` usage in
   `SlingUtils` with what 6.5 offers (or `SlingHttpServletRequestWrapper`). Re-run the
   compile against **`6.5.0` `apis`** to prove the floor compiles.
2. Deploy `-prem` to :4602. Check `bundles.json`: `brightcove.core` Active, and read the
   **unresolved imports** if not (`/system/console/bundles/<id>.json` → `Imported Packages`).
   Every unresolved import is either a shim or a floor decision, recorded in
   `current/docs/onprem-runtime-gaps.md`.
3. Confirm repoinit ran (`brightcove_admin` exists, `/content/dam/brightcove_assets`
   exists), the service-user mapping bound, and the `.cfg.json` runmode configs applied on
   the `author` runmode. Remove the `LoginAdminWhitelist` fragment.
4. Confirm the embedded WCM Core Components version installs on 6.5, or gate the embed to
   the cloud profile and document the on-prem prerequisite.
5. Run the **full** cloud e2e suite against :4602. Fix what is genuinely platform-specific
   (Coral UI / Granite differences between 6.5 and AEMaaCS are the likely category) in the
   shared clientlibs, keeping :4502 green.
6. Boot :4602 on **Temurin 21** once and re-run the sync-back specs: this is the BGS-1671 /
   BGS-1677 claim, measured.

Exit gate: same e2e suite green on both instances from the same commit. Matrix column
*on-prem 7.3.0* filled with no `absent` where cloud says `works`.

**Phase 2 log 2026-09-17.**
- Step 1 done: `utils/JsonUtil.pretty()` replaces all 36 `toPrettyString()` sites; `webservices/AccountsList`
  is the single accounts-listing implementation for `/bin/brightcove/accounts` and the Granite
  datasource, so `utils/SlingUtils` and its `request.builder` dependency are gone; the unused Oak
  import is gone. `core` compiles against the cloud SDK, uber-jar 6.5.22 and uber-jar 6.5.0 `apis`.
  ⚠️ The platform dependency must stay FIRST in a module's `<dependencies>`: moved into a profile
  it landed last on the classpath and an older commons-collections4 from aem-mock shadowed the
  SDK's (`NoSuchMethodError SetUtils.unmodifiableSet`). Coordinates now come from `aem.api.*`
  properties the `onprem` profile overrides (`aem.uber.classifier=apis` + `aem.uber.version=6.5.0`
  compile the GA floor).
- Step 2 first deploy: `brightcove.all-7.3.0-prem.zip` (GA-compiled) installed on :4602 →
  **`brightcove.core 7.3.0 Active` on 6.5.0 GA.** Import ranges: jackson `[2.9,3)`, servlet `[2.6,3)`,
  sling.api ≤ 2.18. Found on the same deploy, all predicted:
  1. The legacy `brightcove-services 6.0.12` bundle stayed Active next to 7.3.0 (its jar lives at
     `/apps/brightcove/install`, outside the new package's filters), plus `/apps/brightcove/runmodes`
     configs remain. Removed by hand on the test bed; **Phase 4 must make the package own and
     clear `/apps/brightcove/install` and `/apps/brightcove/runmodes`.**
  2. With only 7.3.0 running, `/bin/brightcove/accounts` returned `[]`: the instance's account
     config is under the `BrcServiceImpl` PID with snake_case keys (§1.3), invisible to 7.x. A second
     config under `ConfigurationServiceImpl` with camelCase keys was added for Phase 2 testing; the
     legacy one stays for the Phase 4 fallback test.
  3. 🔴 Embedded `core.wcm.components.core 2.27.0` was taken by the OSGi installer as an **upgrade
     of the instance's stock Core Components 2.3.2** (same symbolic name, higher version) and then
     could not resolve on 6.5.0 GA (state Installed; imports on `granite.ui.components 1.20`,
     `cq.dam.cfm 1.12`, `wcm.spi` … unsatisfied). A connector package must never replace a
     site-wide library. Decision: **the on-prem artifact embeds no Core Components** (the three
     embeds moved into the `cloud` profile of `all/pom.xml`; cloud listing unchanged), and
     `core.wcm.components.core` is removed from `core/pom.xml` altogether: no main-code class
     imports it, and its presence on the test classpath was what made aem-mock initialise
     `LinkManagerImpl` and fail with `NoSuchMethodError SetUtils.unmodifiableSet` under the
     on-prem profile. Test bed repaired by deleting the three embedded CC nodes under
     `/apps/brightcove-packages/application/install` + `refreshPackages`; stock 2.3.2 came back Active.
     Follow-up for the on-prem docs: the connector's `ui.content` uses `core/wcm/components/container/v1`
     and `page/v3` resource types, so Core Components must already be present on the target
     (they are on every 6.5; check the `page/v3` minimum when writing the compatibility matrix).

- Step 2 second deploy (CC-free `-prem`, GA-compiled): **single `brightcove.core 7.3.0 Active`,
  stock `core.wcm.components.core 2.3.2` untouched, account visible, `api.js` search returns items.**
  Cloud package listing still identical to the Phase 0 baseline.
- Step 3 measured on :4602: (a) 🔴 the shipped repoinit script **fails to parse on 6.5.0 GA**
  (`repoinit.parser 1.2.2`: `Encountered "set"` at the `set properties` block), and a parse error
  aborts the whole script, so a fresh 6.5.0 would get no `brightcove_admin` and no ACLs. Fixed
  by keeping the script to `create path` / `create service user` / `set ACL` and moving the
  folder title + `cq:conf` into `ui.content` (`/content/dam/brightcove_assets`, filter mode
  `merge`). `README-repoinit.md` sits next to the config. (b) The legacy `/apps/brightcove/runmodes`
  config folder from 6.0.12 kept its `ServiceUserMapperImpl.amended~brightcove_admin` value active
  (`com.coresecure.brightcove.cq5.brightcove-services:brightcoveWrite=…`), which names the OLD
  bundle, so 7.x's `getServiceResourceResolver("brightcoveWrite")` would have failed on the
  first replication or scheduler run. Deleting `/apps/brightcove/runmodes` and `/apps/brightcove/install`
  flipped the active mapping to the shipped `brightcove.core:brightcoveWrite=[brightcove_admin]`.
  **Phase 4 must make the package own and clear both paths.** (c) `LoginAdminWhitelist` fragment
  gone with the legacy folder; nothing in 7.x needs it. Step 2.5 first result: the full suite via `pre-qa-gate.sh --platform onprem` against
  7.3.0-prem on 6.5.0 GA = **32 passed / 3 failed / 1 skipped** (was 6/28/1 on 6.0.12 this
  morning). The 3 failures are all `bcon-176-177-variant-dialog`, which opens the DAM metadata
  editor for one specific synced asset that exists only on :4502 ("There is no content to
  display" on :4602): a fixture gap, not a code gap. Resolved by importing the account's
  videos into the on-prem DAM through the connector's own `dataload` and re-running.
  ⚠️ Hygiene: that spec hardcodes the test account id and a video id in a committed file in a
  public repo (pre-existing since June); Phase 5 should read them from the accounts servlet /
  the asset folder at runtime instead. Step 2.6 (Java 21 boot) still open.

### Phase 3. Port the on-prem-only fixes into shared code

For each item in §1.4 "On-prem has": classify, port by hand into the cloud layout, add a
spec (or extend the matrix row) that discriminates, run both instances. Context-path
handling is the one most likely to need real design: make URL construction go through one
helper that reads the request context path, and prove it with :4602 fronted by a context
root (a dispatcher is not needed; `sling.run.modes` plus a `-Dsling.context.path`-style
config, or simply a reverse proxy on a sub-path, will do).

Exit gate: `git cherry origin/cloud-master origin/onprem-master` items are each recorded
as *present / ported (commit) / obsolete (reason)* in `tests/parity/onprem-commit-ledger.md`.
Both instances green.

### Phase 4. Upgrade path for existing on-prem customers

The realistic customer path is **in-place upgrade from 6.0.x**, not a fresh install.

1. Legacy config keys: fallback reading in `ConfigurationServiceImpl` (§1.3) with a
   one-time WARN naming the old key and the new one. Spec: configure an account with the
   6.0.12 keys only, deploy 7.3.0-prem, `GET /bin/brightcove/accounts` still lists it and
   `damIntegrationPath` is the configured value, not the default.
2. `ui.compat.onprem`: proxy clientlib categories under the old `/etc/designs/cs/brightcove`
   and `/etc/clientlibs/brightcove` paths, and the old workflow model names, so customer
   pages and launchers keep resolving. Decide per item whether to keep permanently or
   deprecate with a release-note date.
3. Test the actual upgrade: :4602 with 6.0.12 installed and configured → install
   7.3.0-prem over it → matrix + e2e. Note that the cloud `ui.apps` filter replaces
   `/apps/brightcove/clientlibs` outright (no `mode`), which is what made stale JCR content
   look like a pass in BGS-1706; check the *content*, not just the bundle version.
4. Write `current/docs/onprem-upgrade-6.0-to-7.md` (repo, public-safe) and update the
   README supports line (currently claims 6.2–6.5; the new floor is 6.5 LTS).

Exit gate: in-place upgrade on :4602 passes matrix + e2e; the fresh-install path still passes.

### Phase 5. Release, branches, governance

1. Cut `7.3.0-cloud` and `7.3.0-prem` from the same commit per `aem-connector-release`
   (detached worktree, `gh release create --target <full SHA>`, both assets each).
2. Rename `cloud-master` → `main`; `onprem-master` → `legacy/onprem-6.0.x`;
   `master` → `legacy/master-2021` with a README note that it is broken (§1.1). Update the
   `pre-qa-gate` base and any docs that name `cloud-master`.
3. Request branch protection on `main` and `release/**` through the central tooling the
   governance page identifies (the disabled `gandalf-managed-branch-protection` ruleset
   suggests hand-edited rulesets get overwritten). Nothing there has been changed yet.
4. Support-site docs: Downloads point at both artifacts; publish the compatibility matrix
   (connector × AEM × Java) that BGS-1671 asked for.
5. Wiki: update `aem-connector-release` (two lines → one line, two tags),
   `aem-connector-local-dev` (two instances, one suite), close the loop on BGS-1671 and
   BGS-1677 with the measured Java 21 result.

---

## 3b. Defects surfaced during Phase 0 (cloud 7.2.3; NOT parity work, file as their own tickets)

Found by the cloud matrix pass on 2026-09-17, evidence under
`tests/parity/runs/2026-09-17/cloud-7.2.3/matrix/` (row files named). None of these block
the parity phases; they are listed so they are not silently folded into "parity" commits.

1. **Removing the last label from a video is a silent no-op with a success toast.** jQuery
   drops the empty `labels` param, `BrcApi#updateLabels` only acts when the param is non-null,
   so the CMS never sees the removal while the UI toasts "Labels saved". Phantom success.
   (`row13-defect-summary.json`.) Server should treat a present-but-empty list as "clear".
2. **Sync throws an uncaught JS exception every run.** `/bin/brightcove/dataload` returns
   HTTP 200 with an EMPTY body and `syncDB()` unconditionally `$.parseJSON`s it. The
   overlay recovers, the console does not. Also an instance of the "200 with empty body"
   anti-pattern the repo rules forbid. (`row22-sync-overlay.png`, `dataload-response.txt`.)
3. **No UI path to remove a video from a folder.** `#moveToFolderModal` lists only real
   folders; the `data-folder-id="none"` markup and its handler still ship but nothing renders
   them. The API action `remove_video_from_folder` works. (`row15-*`.)
4. **Refreshing a player whose video failed to initialise throws two uncaught exceptions**
   (`reading 'options'`, `Invalid target for null#trigger`). BGS-1690 itself is fine on a
   healthy player; the refresh path lacks a guard. (`row26-*`.) Trigger was fixture drift:
   the setup script's mid-page player references a video id that now 403s
   `VIDEO_CLOUD_ERR_VIDEO_NOT_PLAYABLE`; refresh the fixture too.
5. **`get_videos_with_label` without `start` → server `NumberFormatException` → HTTP 500 with
   empty body.** Same anti-pattern as (2), in param parsing.
6. **404 on `/bin/brightcove/author/users/current-user-info`** from the DAM asset editor page.
   Non-fatal; check whether that servlet is meant to exist.
7. **Text-track upload button is re-enabled mid-upload.** The click handler disables
   `#uttUpload` and sets "Uploading…", but the language field's async BCP-47 validation
   (`brcUI.js`, `prop('disabled', !(langOk && sourceOk))`) can resolve after the click and
   re-enable it while the request is in flight, allowing a double submit. Reproduces inside
   the full e2e suite (validation lands late), not in isolation. Covered by a `test.fixme`
   in `bcon-186-text-track-upload-feedback.spec.js`; flip it to `test` when fixed.

Also learned: `get_videos_with_label` / `get_videos_in_folder` are index-backed and lag; to
verify a label/folder write, re-fetch the video record (`a=search_videos&isID=true&query=<id>`).

## 4. Standing rules for the overseeing agent

- **Verified running, not compiled.** Every phase gate is a deploy plus the e2e suite on
  the affected instance(s), plus a re-read of persisted state. A green build is not a gate.
- **Bump the pom before every deploy** or AEM keeps the old bundle and the run measures
  stale code. `pre-qa-gate` step 1 exists for this.
- **Three states.** Matrix cells and spec results are `works` / `broken` / `not measured`.
  A spec that cannot reach its precondition reports *not measured*, never pass.
- **Public repo hygiene** on every commit: ticket IDs only; scan the diff for account IDs,
  customer names, internal hostnames. The e2e README already names a test account ID; do
  not add more.
- **Subagent routing.** Mechanical work (matrix filling, commit classification, the
  `toPrettyString` sweep, spec porting) → Sonnet. Design calls (context-path helper, compat
  overlay scope, unresolved-import triage) → Opus. Do not set a global subagent model.
- **Do not touch the live working tree**; do not merge to `cloud-master` without a PR;
  do not rename branches or touch protection until Phase 5 and Laurence's go-ahead.
- **Ask Laurence** only for: whether any 6.0.x customer needs the `legacy/` branch kept
  hotfix-able, and the go-ahead for Phase 5. (Floor decided: 6.5 LTS.)
- **Subagent routing decided by Laurence 2026-09-17:** straightforward tasks go to lower
  models (Sonnet); keep Opus for design calls and triage.
- Log substantive findings to the wiki (`aem-connector-*` pages) as they land, not at the
  end. The `aem-connector-unified-build` page holds the probe results this plan is built on.
