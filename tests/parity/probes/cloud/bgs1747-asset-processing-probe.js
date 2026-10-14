// BGS-1747 — is a DAM asset created by the connector's Brightcove -> DAM import visible in the
// Assets side panel at once, and sent for processing, instead of sitting `unProcessed` until the
// AEMaaCS stuck-asset watchdog (~60 min)?
//
// Questions, each a PASS / FAIL / NOT MEASURED line:
//   1. the import created a DAM asset for a throwaway video this run made
//   2. the side panel's own query (contentfinder, video mime filter) lists it
//   3. processing requests, judged by the BED: AEMaaCS (Asset Compute client present) = the requester
//      is deployed, the connector logged exactly one request for THIS asset and AEM accepted a PROCESS
//      command from the connector's service user; AEM 6.5 = no requester, no request, no command
//      (DAM Update Asset already processes it, a request would run it twice)
//   4. the asset reaches dam:assetState=processed within --timeout seconds.
//      NOT MEASURED on a bed with no processing behind the request: the AEMaaCS SDK with its
//      SDK-only processing launchers disabled (the local stand-in for AEMaaCS, see the doc).
//   5. connector renditions (brc_*) present before processing are still there after it.
//      NOT MEASURED when the import made none (a video without poster/thumbnail images).
//
// Usage (credentials from the environment, never the command line):
//   set -a; . <test-account env>; set +a
//   node tests/parity/probes/cloud/bgs1747-asset-processing-probe.js [AEM_BASE] [--timeout=180] [--keep] [--allow-remote]
//
// Real AEM as a Cloud Service (e.g. an RDE): add --aemaacs --allow-remote, export AEM_TOKEN (a
// Developer Console local development token; never on the command line), and capture the author
// log for the run into a file passed as --log-file=<path> (e.g. `aio aem rde logs -s author -i
// com.coresecure -i com.adobe.granite.jobs.async > <path>`, INFO only: DEBUG logs the connector's
// OAuth token). /system/console is not reachable there, so --dam-root=<damIntegrationPath> replaces
// the config lookup. On AEMaaCS the connector's lines go to error.log, i.e. the same captured stream.
//
// Mutates: one Video Cloud video named e2e-throwaway-* that this run creates (deleted at the end),
// and the DAM asset the import makes for it (deleted at the end unless --keep). A dataload pass
// imports every video of every configured account that is missing from DAM, so run it on a bed
// whose accounts you are happy to have fully imported.
// Context: current/docs/asset-processing.md
const { cli, assertLocalTarget, assertMutable, markCreated, finish } = require('../lib/guards');
const { cmsClient, creds } = require('../../../e2e/cms');

const argv = process.argv.slice(2);
const { positional, allowRemote } = cli(argv);
const AEM = (positional[0] || process.env.AEM_BASE || 'http://localhost:4502').replace(/\/$/, '');
const TIMEOUT_S = Number((argv.find((a) => a.startsWith('--timeout=')) || '--timeout=180').split('=')[1]);
const KEEP = argv.includes('--keep');
const AEMAACS = argv.includes('--aemaacs');
const argVal = (name) => { const a = argv.find((x) => x.startsWith(`--${name}=`)); return a ? a.slice(name.length + 3) : null; };
const LOG_FILE = argVal('log-file');
const DAM_ROOT = argVal('dam-root');
const USER = process.env.AEM_USER || 'admin';
const PASS = process.env.AEM_PASS || 'admin';
const AUTH = process.env.AEM_TOKEN
  ? { Authorization: `Bearer ${process.env.AEM_TOKEN}` }
  : { Authorization: 'Basic ' + Buffer.from(`${USER}:${PASS}`).toString('base64') };
// What the side panel's "Brightcove Videos" finder sends (clientlib-assetfinder/asset_content_finder.js).
const PANEL_MIME = 'video,application/x-shockwave-flash,application/vnd.rn-realmedia,application/mxf';
const IMAGE_URL = process.env.BGS1747_IMAGE_URL || 'https://raw.githubusercontent.com/brightcove/Adobe-AEM-Brightcove-Connector/cloud-master/current/ui.apps/src/main/content/jcr_root/apps/brightcove/clientlibs/clientlib-tools/img/shared/img/noThumbnail.jpg';
const SDK_LAUNCHERS = ['asset_processing_on_sdk_create', 'asset_processing_on_sdk_mod'];
// The connector's write service user (ServiceUserMapperImpl.amended-brightcove_admin.cfg.json).
const SERVICE_USER = process.env.BRC_SERVICE_USER || 'brightcove_admin';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function get(p) { return fetch(AEM + p, { headers: AUTH }); }
async function getJson(p) { const r = await get(p); return r.ok ? r.json() : null; }
async function post(p, form) {
  return fetch(AEM + p, { method: 'POST', headers: { ...AUTH, 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(form || {}) });
}

async function componentActive(name) {
  const c = await getJson('/system/console/components.json');
  return !!(c && c.data.some((x) => x.name === name && x.state === 'active'));
}

// true / false, or null when the launcher config could not be read.
async function sdkLaunchersEnabled() {
  const states = [];
  for (const n of SDK_LAUNCHERS) {
    const libs = await getJson(`/libs/settings/workflow/launcher/config/${n}.json`);
    if (!libs) { states.push(false); continue; } // not an SDK
    const conf = await getJson(`/conf/global/settings/workflow/launcher/config/${n}.json`);
    const eff = conf && conf.enabled !== undefined ? conf.enabled : libs.enabled;
    states.push(eff === true || eff === 'true');
  }
  return states.some(Boolean);
}

async function damIntegrationPath(accountId) {
  const cfg = await getJson('/system/console/configMgr/(service.factoryPid=com.coresecure.brightcove.wrapper.sling.ConfigurationServiceImpl).json');
  for (const c of Array.isArray(cfg) ? cfg : []) {
    const p = c.properties || {};
    if (p.accountId && String(p.accountId.value) === String(accountId) && p.damIntegrationPath) return p.damIntegrationPath.value;
  }
  return '/content/dam/brightcove_assets';
}

async function connectorRenditions(assetPath) {
  const r = await getJson(`${assetPath}/jcr:content/renditions.1.json`);
  return r ? Object.keys(r).filter((k) => k.startsWith('brc_')).sort() : null;
}

async function findAsset(root, videoId) {
  const r = await getJson(`/bin/querybuilder.json?path=${encodeURIComponent(root)}&type=dam:Asset&property=jcr:content/metadata/brc_id&property.value=${videoId}&p.limit=5`);
  return r && r.hits && r.hits.length ? r.hits[0].path : null;
}

async function assetState(assetPath) {
  const r = await getJson(`${assetPath}/jcr:content.json`);
  return r ? (r['dam:assetState'] === undefined ? '(absent)' : r['dam:assetState']) : null;
}

// true / false, or null when the finder could not be queried.
async function listedInSidePanel(assetPath) {
  const folder = assetPath.slice(0, assetPath.lastIndexOf('/'));
  const q = new URLSearchParams({ query: 'order:"-jcr:content/jcr:lastModified" ', mimeType: PANEL_MIME, itemResourceType: 'cq/gui/components/authoring/assetfinder/asset', limit: '0..2000', _charset_: 'utf-8' });
  const r = await get(`/bin/wcm/contentfinder/asset/view.html${folder}?${q}`);
  if (!r.ok) return null;
  return (await r.text()).includes(assetPath);
}

// error.log line time ("13.10.2026 13:18:44.071 *INFO* ...") as epoch ms in the AEM host's local zone.
function logTime(line) {
  const m = /^(\d\d)\.(\d\d)\.(\d{4}) (\d\d):(\d\d):(\d\d)\.(\d{3}) /.exec(line);
  return m ? new Date(+m[3], +m[2] - 1, +m[1], +m[4], +m[5], +m[6], +m[7]).getTime() : null;
}

async function logLines(appender) {
  // ⚠️ The name is the appender's, with the leading slash. A wrong name still answers HTTP 200 with
  // "No appender with name [...] found", which would read as zero lines: treat it as unreadable.
  const r = await get('/system/console/slinglog/tailer.txt?tail=20000&name=' + encodeURIComponent(appender));
  if (!r.ok) return null;
  const text = await r.text();
  return /^No appender with name/.test(text) ? null : text.split('\n'); // null: NOT MEASURED, never "zero lines"
}

(async () => {
  const checks = [];
  const evidence = { aem: AEM, timeoutS: TIMEOUT_S, states: [] };
  let cms = null; let videoId = null; let assetPath = null;
  const runStart = Date.now(); // ⚠️ assumes the AEM host shares this machine's clock and zone (local beds)
  try {
    assertLocalTarget(AEM, { allowRemote });
    if (!creds()) { checks.push({ name: 'test-account credentials in the environment', ok: null }); return; }
    // On real AEMaaCS /system/console is not reachable: the bed is known, the requester is judged by its log line.
    const requesterDeployed = AEMAACS ? null : await componentActive('com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl.AssetProcessingRequesterImpl');
    const assetCompute = AEMAACS ? true : await componentActive('com.adobe.cq.assetcompute.impl.assetprocessor.AssetProcessorImpl');
    const sdkLaunchers = AEMAACS ? false : (assetCompute ? await sdkLaunchersEnabled() : false);
    Object.assign(evidence, { requesterDeployed, assetComputeClient: assetCompute, sdkLaunchersEnabled: sdkLaunchers });

    cms = await cmsClient();
    const root = DAM_ROOT || (AEMAACS ? '/content/dam/brightcove_assets' : await damIntegrationPath(cms.accountId));
    const name = `e2e-throwaway-bgs1747-${Date.now()}`;
    videoId = await cms.create(name);
    markCreated(videoId);
    // Give it images, so the import adds the connector's brc_* renditions (check 5). This public repo's
    // own placeholder, fetched by AEM from GitHub, so a remote AEMaaCS can reach it too.
    await cms.addRemoteImages(videoId, IMAGE_URL);
    evidence.video = { name };

    // The CMS search the import uses is eventually consistent: re-run dataload until the asset lands.
    const t0 = Date.now();
    while (!assetPath && Date.now() - t0 < 180_000) {
      evidence.dataloadHttp = (await post('/bin/brightcove/dataload.html')).status;
      for (let i = 0; i < 10 && !assetPath; i++) { await sleep(3000); assetPath = await findAsset(root, videoId); }
    }
    // A missing asset after a 200 dataload is a broken import (FAIL); only a failed dataload is NOT MEASURED.
    checks.push({ name: `connector import created a DAM asset for the throwaway video (dataload HTTP ${evidence.dataloadHttp})`, ok: assetPath ? true : (evidence.dataloadHttp === 200 ? false : null) });
    if (!assetPath) return;
    evidence.assetSeenAfterMs = Date.now() - t0;
    evidence.assetPath = assetPath; // e.g. to reprocess a --keep asset by hand

    const renditionsBefore = await connectorRenditions(assetPath);
    evidence.connectorRenditionsBefore = renditionsBefore;
    const listed = await listedInSidePanel(assetPath);
    checks.push({ name: 'side panel (Brightcove Videos finder, video mime filter) lists the new asset', ok: listed });

    const seen = Date.now(); let state = null;
    while (Date.now() - seen < TIMEOUT_S * 1000) {
      state = await assetState(assetPath);
      const last = evidence.states[evidence.states.length - 1];
      if (!last || last.state !== state) evidence.states.push({ atS: Math.round((Date.now() - seen) / 1000), state });
      if (state === 'processed') break;
      await sleep(2000);
    }

    // Connector side, per asset: its own INFO/WARN lines go to /logs/brightcove.log (ui.config logger,
    // Debug level on default run modes). AEM side: AsyncJobInitializer in /logs/error.log logs every
    // accepted PROCESS command with the submitting user but not the asset, so it is counted per window.
    let brc; let err;
    if (LOG_FILE) {
      // ⚠️ Read it now: the capture must cover the import and the request, i.e. be running before the dataload.
      let lines = null;
      try { lines = require('fs').readFileSync(LOG_FILE, 'utf8').split('\n'); } catch (e) { lines = null; }
      brc = lines; err = lines;
    } else if (AEMAACS) {
      brc = null; err = null;
    } else {
      brc = await logLines('/logs/brightcove.log');
      err = await logLines('/logs/error.log');
    }
    if (brc === null || err === null) {
      checks.push({ name: `processing requests for the asset (unreadable log: ${brc === null ? 'brightcove.log ' : ''}${err === null ? 'error.log' : ''})`, ok: null });
    } else {
      const requested = brc.filter((l) => l.includes(`Requested processing for ${assetPath} `)).length;
      const failed = brc.filter((l) => l.includes(`processing for ${assetPath}`) && /refused|Could not request/.test(l)).length;
      // A captured log file covers only this run; the local tailers are windowed by time.
      const inWindow = LOG_FILE ? () => true : (l) => { const t = logTime(l); return t !== null && t >= runStart - 1000; };
      const accepted = err.filter((l) => /AsyncJobInitializer Request completed to process PROCESS submitted by /.test(l)
        && l.includes(`submitted by ${SERVICE_USER} `) && inWindow(l)).length;
      Object.assign(evidence, { connectorRequestsForAsset: requested, connectorFailuresForAsset: failed, acceptedProcessCommandsInWindow: accepted });
      if (assetCompute && sdkLaunchers) {
        // Stock SDK: its own launcher races the request; whichever marks the asset first wins, so 0 or 1
        // requests are both correct. Only a second request or a failure is a defect here.
        checks.push({ name: `AEMaaCS SDK (launchers on): at most one request for this asset, none failed (requests=${requested}, failures=${failed})`,
          ok: requested <= 1 && failed === 0 });
      } else if (assetCompute) {
        checks.push({ name: `AEMaaCS: requester deployed, one request for this asset, accepted by AEM (deployed=${requesterDeployed}, requests=${requested}, failures=${failed}, accepted in window=${accepted})`,
          ok: requesterDeployed !== false && requested === 1 && failed === 0 && accepted >= 1 });
      } else {
        checks.push({ name: `AEM 6.5: no requester, no request for this asset, no PROCESS command (deployed=${requesterDeployed}, requests=${requested}, accepted in window=${accepted})`,
          ok: !requesterDeployed && requested === 0 && accepted === 0 });
      }
    }

    const noProcessingBehindRequest = assetCompute && !sdkLaunchers && /localhost|127\.0\.0\.1/.test(AEM);
    checks.push({
      name: `asset reached dam:assetState=processed within ${TIMEOUT_S}s (watchdog would take ~60 min); last=${state}`
        + (noProcessingBehindRequest && state !== 'processed' ? ' [SDK with launchers off has no Asset Compute]' : ''),
      ok: state === 'processed' ? true : (noProcessingBehindRequest ? null : false),
    });

    const renditionsAfter = state === 'processed' ? await connectorRenditions(assetPath) : null;
    evidence.connectorRenditionsAfter = renditionsAfter;
    const lost = renditionsBefore && renditionsAfter ? renditionsBefore.filter((r) => !renditionsAfter.includes(r)) : null;
    checks.push({
      name: `connector renditions survive processing (before=${JSON.stringify(renditionsBefore)}, after=${JSON.stringify(renditionsAfter)})`,
      ok: !renditionsBefore || !renditionsBefore.length || !renditionsAfter ? null : lost.length === 0,
    });
  } catch (e) {
    console.log(`ERROR ${e.message}`);
    checks.push({ name: 'probe ran to completion', ok: e.constructor.name === 'GuardError' ? false : null });
  } finally {
    console.log(JSON.stringify(evidence, null, 2));
    if (assetPath && !KEEP) { const r = await post(assetPath, { ':operation': 'delete' }); console.log(`cleanup DAM asset: HTTP ${r.status}`); }
    if (cms && videoId) {
      try { assertMutable('video', { id: videoId }); await cms.del(videoId); console.log('cleanup video: deleted'); } catch (e) { console.log(`cleanup video: ${e.message}`); }
    }
    finish(checks);
  }
})();
