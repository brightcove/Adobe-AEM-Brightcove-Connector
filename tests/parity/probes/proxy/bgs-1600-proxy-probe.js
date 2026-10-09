#!/usr/bin/env node
// Parity row 37 probe: "Proxy server honoured (incl. rendition creation)" (BGS-1600).
//
// Question per call path: does the connector send this call through the proxy it is
// configured with, and does it FAIL when that proxy is dead (so it is not going direct)?
//
// How: the connector's proxy is the factory OSGi config
//   com.coresecure.brightcove.wrapper.sling.ConfigurationServiceImpl.<uuid>  key `proxyServer`
// ("host:port"). HttpServices.setProxy stores it in a STATIC, set each time a BrightcoveAPI
// is built, i.e. per request. The probe flips that one property through the Felix console,
// runs the same actions in four phases, and judges each call path from the proxy's own log
// (tests/e2e/proxy-harness.js):
//   baseline  proxyServer empty (original). Actions succeed; proxy log stays EMPTY.
//   dead      proxyServer -> a closed local port. Every action must FAIL and persist nothing.
//             An action that still succeeds BYPASSES the proxy.
//   routed    proxyServer -> the logging proxy. Actions succeed; the log must show the hosts.
//   deny      proxyServer -> the logging proxy, with image and S3 hosts refused (403 on
//             CONNECT). Image fetch / S3 upload must then fail while CMS still works: proves
//             those two calls use the proxy, which "dead" cannot (it fails the CMS call first).
// The original config is restored (all properties, by value) in a finally block and
// re-verified. Writes only e2e-throwaway-* videos / DAM assets and removes them.
//
// Verdicts are printed per call path as PASS / FAIL / NOT MEASURED; exit 0 only if every
// path is PASS. Context: ./README.md
//
// Env: AEM_BASE (default http://localhost:4502), AEM_USER/AEM_PASS, and the Video Cloud
//   creds of the account AEM is configured with (BRIGHTCOVE_ACCOUNT_ID / _CLIENT_ID /
//   _CLIENT_SECRET, e.g. `set -a; source ~/.brightcove/<label>.env; set +a`).
//   BRC_LOG (optional) path to the instance's brightcove.log, used only to record which
//   PATCH implementation ran. PROBE_OUT (optional) dir for the raw evidence JSON; created
//   (mkdir -p) if missing, and an unwritable one is reported and ignored, never fatal.
//   NODE_PATH must reach tests/e2e/node_modules (dam.js needs @playwright/test), e.g.
//   NODE_PATH=$PWD/tests/e2e/node_modules node tests/parity/probes/proxy/bgs-1600-proxy-probe.js
//   Restore of the connector config and cleanup of throwaways run in a finally, each step in
//   its own try, so one failing step cannot skip the config restore.
const fs = require('fs');
const path = require('path');
const { request: pwRequest } = require('@playwright/test');
const { AEM_BASE, AEM_USER, AEM_PASS } = require('../../../e2e/target');
const { creds, cmsClient } = require('../../../e2e/cms');
const dam = require('../../../e2e/dam');
const { slingPost, brightcoveAssetsRoot, mediaHostReachable } = require('../../../e2e/fixtures');
const { LoggingProxy, deadPort } = require('../../../e2e/proxy-harness');

const PREFIX = 'e2e-throwaway-';
const VIDEO_URL = process.env.BRC_E2E_VIDEO_URL || 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4';
const IMAGE_URL = process.env.BRC_E2E_IMAGE_URL || 'https://httpbin.org/image/jpeg';
const S3_HOST = /amazonaws\.com$/;
const BASIC = 'Basic ' + Buffer.from(`${AEM_USER}:${AEM_PASS}`).toString('base64');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const verdicts = []; // { path, verdict, detail }
function verdict(p, v, detail) {
  verdicts.push({ path: p, verdict: v, detail });
  console.log(`${v.padEnd(12)} ${p}  ${detail}`);
}
const notMeasured = (p, why) => verdict(p, 'NOT MEASURED', why);

// ---- AEM plumbing ------------------------------------------------------------------
async function aemText(method, p, { form, headers } = {}) {
  const init = { method, headers: { Authorization: BASIC, Referer: `${AEM_BASE}/`, ...(headers || {}) } };
  if (form) { init.body = new URLSearchParams(form); }
  const res = await fetch(AEM_BASE + p, init);
  return { status: res.status, text: await res.text() };
}
const jsonp = (t) => { const m = /^[^(]*\(([\s\S]*)\)\s*;?\s*$/.exec(t.trim()); return JSON.parse(m ? m[1] : t); };
async function api(account, params) {
  const q = new URLSearchParams({ account_id: account, callback: 'cb' });
  for (const [k, v] of Object.entries(params)) (Array.isArray(v) ? v : [v]).forEach((x) => q.append(k, x));
  const r = await aemText('GET', `/bin/brightcove/api.js?${q}`);
  try { return { status: r.status, body: jsonp(r.text) }; } catch (e) { return { status: r.status, body: { error_code: 'unparseable', message: r.text.slice(0, 120) } }; }
}
const failed = (b) => !b || b.error_code !== undefined || b.error !== undefined;

// ---- Config (the real mechanism): tests/e2e/connector-config.js ----------------------------
const { readConfig, setProxy, restoreConfig, backupConfig, same } = require('../../../e2e/connector-config');

// ---- PNG/JPEG dimensions (to tell a real fetched thumbnail from the bundled placeholder) ----
function dims(buf) {
  if (buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47) return `${buf.readUInt32BE(16)}x${buf.readUInt32BE(20)}`;
  const av = buf.lastIndexOf('avc1'); // one-frame MP4 the importer encodes from the thumbnail; the first 'avc1' is a brand in ftyp
  if (av > 0 && buf.length > av + 32) return `${buf.readUInt16BE(av + 28)}x${buf.readUInt16BE(av + 30)}`;
  if (buf[0] === 0xff && buf[1] === 0xd8) {
    let i = 2;
    while (i < buf.length - 9) {
      if (buf[i] !== 0xff) { i++; continue; }
      const m = buf[i + 1];
      if (m >= 0xc0 && m <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(m)) return `${buf.readUInt16BE(i + 7)}x${buf.readUInt16BE(i + 5)}`;
      i += 2 + buf.readUInt16BE(i + 2);
    }
  }
  return null;
}

// The importer encodes the thumbnail as one H.264 frame, which needs even dimensions, so an
// odd-sized source comes back cropped by up to one pixel per axis: equal within 1px, not ===.
function dimsMatch(a, b) {
  if (!a || !b) return false;
  const [aw, ah] = a.split('x').map(Number); const [bw, bh] = b.split('x').map(Number);
  return Math.abs(aw - bw) <= 1 && Math.abs(ah - bh) <= 1;
}

// ---- brightcove.log slice (evidence of which PATCH implementation ran) -------------------
const LOG = process.env.BRC_LOG;
const logSize = () => { try { return fs.statSync(LOG).size; } catch (e) { return null; } };
function logSlice(from) {
  if (!LOG || from === null) return '';
  const fd = fs.openSync(LOG, 'r'); const len = fs.statSync(LOG).size - from; const b = Buffer.alloc(Math.max(len, 0));
  fs.readSync(fd, b, 0, b.length, from); fs.closeSync(fd); return b.toString('utf8');
}

async function main() {
  let probeOut = process.env.PROBE_OUT || null;
  if (probeOut) {
    try { fs.mkdirSync(probeOut, { recursive: true }); } catch (e) { console.error(`PROBE_OUT ${probeOut} unusable (${e.message}): evidence files disabled`); probeOut = null; }
  }
  if (!creds()) { notMeasured('ALL', 'BRIGHTCOVE_ACCOUNT_ID/CLIENT_ID/CLIENT_SECRET not set: no throwaway video can be created'); return; }
  const request = await pwRequest.newContext({ baseURL: AEM_BASE, extraHTTPHeaders: { Authorization: BASIC } });
  const cms = await cmsClient();
  const acct = (await (await request.get('/bin/brightcove/accounts')).json()).accounts[0].value;
  if (String(acct) !== String(cms.accountId)) throw new Error('credentials in the environment belong to another account than the one AEM is configured with');
  const root = await brightcoveAssetsRoot(request);
  const jvm = /^\s*java\.version\s*=\s*(\S+)/m.exec((await aemText('GET', '/system/console/status-System%20Properties.txt')).text);
  console.log(`target ${AEM_BASE}  java.version=${jvm ? jvm[1] : 'unknown'}  account=<configured>`);

  const orig = await readConfig();
  const backup = backupConfig(orig, 'bgs-1600-proxy-probe'); // 0600, gitignored run folder, deleted once the restore verifies
  console.log(`original proxyServer=${JSON.stringify(orig.props.proxyServer)} (full config backed up 0600 for crash recovery)`);

  const proxy = await new LoggingProxy({ logFile: probeOut ? path.join(probeOut, `proxy-${new URL(AEM_BASE).port}.jsonl`) : null }).start();
  const dead = `127.0.0.1:${await deadPort()}`;
  const stamp = Date.now();
  const created = { videos: new Set(), assets: new Set() };
  const evidence = {};
  // ⚠️ The config backup (holds client_secret) and the logging proxy exist from here on, so EVERY
  // exit path below, early returns included, must run through this try's finally, which restores
  // the config, deletes the backup and stops the proxy. An early return placed before the `try`
  // left the backup on disk and the proxy server holding the process open.
  try {
  // A dead third-party media host is not a proxy defect: say NOT MEASURED instead of failing.
  for (const [what, url] of [['video ingest source', VIDEO_URL], ['poster image source', IMAGE_URL]]) {
    if (!(await mediaHostReachable(url))) { notMeasured('ALL', `${what} ${new URL(url).host} is unreachable from here (set BRC_E2E_VIDEO_URL / BRC_E2E_IMAGE_URL)`); return; } // the finally below restores the config and removes the backup
  }
    // ---- throwaway video, ACTIVE with images, listed by the CMS index -----------------------
    const name = `${PREFIX}proxy-${stamp}`;
    const vid = await cms.create(name); created.videos.add(vid);
    await cms.ingest(vid, VIDEO_URL);
    if (!(await cms.waitActive(vid))) { notMeasured('ALL', `throwaway ${vid} never became ACTIVE`); return; }
    let thumb = null;
    for (let i = 0; i < 60 && !thumb; i++) { const v = await cms.get(vid); thumb = v.images && v.images.thumbnail && v.images.thumbnail.src; if (!thumb) await sleep(5000); }
    if (!thumb) { notMeasured('rendition/image fetch', 'throwaway never got a thumbnail'); }
    const thumbHost = thumb ? new URL(thumb).hostname : null;
    for (let i = 0; i < 90; i++) { if ((await cms.search(`name:${name}`)).some((v) => String(v.id) === vid)) break; await sleep(5000); }
    const direct = thumb ? Buffer.from(await (await fetch(thumb)).arrayBuffer()) : null;
    const realDims = direct ? dims(direct) : null;

    // ---- actions: each returns { ok, detail }; all judge by re-reading persisted state ----------
    const assetPath = `${root}/${vid}.mp4`;
    const assetStatus = async () => (await request.get(`${assetPath}.json`)).status();
    const delAsset = async () => { if (await assetStatus() === 200) await slingPost(request, assetPath, { ':operation': 'delete' }); };
    let label = null; let label2 = null;
    const A = {
      async search() {
        const r = await api(acct, { a: 'search_videos', query: `name:${name}`, limit: 5, start: 0 });
        const hit = !failed(r.body) && (r.body.items || []).some((v) => String(v.id) === vid);
        return { ok: hit, detail: failed(r.body) ? `error ${r.body.error_code}` : `${(r.body.items || []).length} items` };
      },
      async labels() {
        const before = (await cms.get(vid)).labels || [];
        const r = await api(acct, { a: 'update_labels', labels: [label], videoId: vid });
        const after = (await cms.get(vid)).labels || [];
        return { ok: !failed(r.body) && after.includes(label) && !before.includes(label), detail: `api=${failed(r.body) ? 'error ' + r.body.error_code : 'ok'} cms.labels=${JSON.stringify(after)}` };
      },
      // Deny control for the PATCH itself: OAuth is allowed, the CMS host is refused, so the
      // PATCH can only succeed if it bypasses the proxy. Uses a second label so the routed
      // run's persisted label cannot make this pass.
      async labelsDeny() {
        const before = (await cms.get(vid)).labels || [];
        const r = await api(acct, { a: 'update_labels', labels: [label2], videoId: vid });
        const after = (await cms.get(vid)).labels || [];
        // `reported`: the API said so (error_code present), not an empty {} that the UI reads as saved.
        return { ok: after.includes(label2), reported: failed(r.body) && r.body.error_code !== undefined, detail: `api=${failed(r.body) ? 'error ' + r.body.error_code + ' ' + String(r.body.message || '').slice(0, 60) : 'ok'} cms.labels before=${JSON.stringify(before)} after=${JSON.stringify(after)}` };
      },
      async dataload() {
        await delAsset();
        const pre = await assetStatus();
        if (pre !== 404) return { ok: false, detail: `asset not clean before sync (${pre})`, invalid: true };
        await slingPost(request, '/bin/brightcove/dataload', {});
        let st = 0; for (let i = 0; i < 40 && st !== 200; i++) { st = await assetStatus(); if (st !== 200) await sleep(3000); }
        let rd = null;
        if (st === 200) {
          const r = await request.get(`${assetPath}/jcr:content/renditions/original`);
          if (r.ok()) rd = dims(Buffer.from(await r.body()));
        }
        evidence.lastRenditionDims = rd;
        return { ok: st === 200, detail: `asset=${st} original-rendition=${rd} (real thumbnail ${realDims})`, rd };
      },
      async poster() {
        const before = (await cms.get(vid)).images || {};
        const r = await api(acct, { a: 'upload_image', id: vid, poster_source: IMAGE_URL });
        let src = null;
        if (!failed(r.body)) for (let i = 0; i < 40 && !src; i++) { const im = (await cms.get(vid)).images || {}; src = im.poster && im.poster.src !== (before.poster && before.poster.src) ? im.poster.src : null; if (!src) await sleep(3000); }
        const hasR = (await request.get(`${assetPath}/jcr:content/renditions/brc_poster.png.json`)).status() === 200;
        evidence.posterRendition = hasR;
        return { ok: !failed(r.body) && !!src, detail: `api=${failed(r.body) ? 'error ' + r.body.error_code : 'queued'} cms.poster=${src ? 'persisted' : 'absent'} dam brc_poster.png=${hasR}`, hasR };
      },
      // BrcImageApi: CMS lookup of the poster URL, then the poster fetch (both via HttpServices).
      async imagecache() {
        const res = await fetch(`${AEM_BASE}/bin/brightcove/image?id=${vid}&key=${acct}`, { headers: { Authorization: BASIC } });
        const b = Buffer.from(await res.arrayBuffer());
        return { ok: res.status === 200 && b.length > 100, detail: `HTTP ${res.status} ${b.length} bytes` };
      },
      async workflow() {
        const wname = `${PREFIX}proxywf-${stamp}-${Math.floor(Math.random() * 1e4)}`;
        const { assetPath: ap } = await dam.uploadAsset(request, root, `${wname}.mp4`);
        created.assets.add(ap);
        await dam.setMetadata(request, ap, { 'dc:title': wname });
        const uuid = (await dam.assetNode(request, ap))['jcr:uuid'];
        const wf = await dam.runWorkflow(request, '/var/workflow/models/bc-sync-new-asset', ap);
        const m = (await dam.metadata(request, ap)) || {};
        const v = await cms.getByRef(uuid);
        if (v) created.videos.add(String(v.id));
        if (m.brc_id) created.videos.add(String(m.brc_id));
        return { ok: !!(v && m.brc_id), detail: `workflow=${wf.status} brc_id=${m.brc_id ? 'set' : 'absent'} cms-by-ref=${v ? 'found' : 'absent'}` };
      },
    };

    // ---- baseline: original config, no proxy ------------------------------------------------
    await setProxy(orig, orig.props.proxyServer);
    let m0 = proxy.mark();
    const lbl = await api(acct, { a: 'list_labels' });
    const lab = (x) => (x ? (typeof x === 'string' ? x : (x.value || x.name || null)) : null); label = lab((lbl.body.items || [])[0]); label2 = lab((lbl.body.items || [])[1]);
    const base = await A.search();
    verdict('baseline: no proxy configured, search works and proxy log is empty',
      base.ok && proxy.since(m0).length === 0 ? 'PASS' : 'FAIL', `${base.detail}; proxy log entries=${proxy.since(m0).length}`);

    const PATHS = {};
    const run = async (phase, key, fn, { expectOk, mustHosts = [], mustDenied = [], extra }) => {
      proxy.closeTunnels(); await sleep(300); // force fresh CONNECTs (see closeTunnels)
      const m = proxy.mark(); const l = logSize();
      let r; try { r = await fn(); } catch (e) { r = { ok: false, detail: `threw ${e.message}` }; }
      const hosts = proxy.hostsSince(m, 'allow'); const denied = proxy.hostsSince(m, 'deny');
      const rec = (PATHS[key] = PATHS[key] || {}); rec[phase] = { ...r, hosts, denied };
      evidence[`${phase}:${key}`] = { ...r, hosts, denied, patch: /PATCH unsupported|executePatchUsingApacheHttpClient/.test(logSlice(l)) ? 'apache-fallback' : (/executePatch - response code/.test(logSlice(l)) ? 'httpurlconnection-reflection' : 'unknown') };
      console.log(`  [${phase}] ${key}: ok=${r.ok} ${r.detail}  via-proxy=${JSON.stringify(hosts)} denied=${JSON.stringify(denied)}`);
      return r;
    };
    const names = ['search', 'labels', 'dataload', 'poster', 'imagecache', 'workflow'];

    // ---- dead proxy: everything must fail and persist nothing --------------------------------
    await setProxy(orig, dead);
    for (const k of names) { if (k === 'labels' && !label) continue; await run('dead', k, A[k], {}); } // a null label would be sent as the string "null"
    // ---- routed through the logging proxy ----------------------------------------------------
    await setProxy(orig, proxy.address);
    if (!label) notMeasured('labels (PATCH)', 'account has no labels to apply');
    for (const k of names) { if (k === 'labels' && !label) continue; await run('routed', k, A[k], {}); }
    // ---- deny: image + S3 hosts refused by the proxy -----------------------------------------
    let posterHost = null; try { const im = (await cms.get(vid)).images || {}; posterHost = im.poster && new URL(im.poster.src).hostname; } catch (e) { /* none */ }
    const denyRes = [S3_HOST, /(^|\.)httpbin\.org$/]; for (const h of new Set([thumbHost, posterHost].filter(Boolean))) denyRes.push(new RegExp('^' + h.replace(/\./g, '\\.') + '$'));
    proxy.setDeny(denyRes);
    if (label && label2) { proxy.setDeny([/^cms\.api\.brightcove\.com$/]); await run('deny', 'labelsDeny', A.labelsDeny, {}); proxy.setDeny(denyRes); }
    await run('deny', 'dataload', A.dataload, {});
    await run('deny', 'poster', A.poster, {});
    await run('deny', 'imagecache', A.imagecache, {});
    await run('deny', 'workflow', A.workflow, {});
    proxy.setDeny([]);

    // ---- judge ----------------------------------------------------------------------------------
    const judge = (key, label2, need) => {
      const d = (PATHS[key] || {}).dead, r = (PATHS[key] || {}).routed;
      if (!d || !r) return notMeasured(label2, 'phase did not run');
      const probs = [];
      if (!r.ok) probs.push(`routed run failed (${r.detail})`);
      for (const h of need) if (!r.hosts.some((x) => h.test(x))) probs.push(`proxy never saw ${h}`);
      if (d.ok) probs.push(`BYPASS: succeeded with a dead proxy (${d.detail})`);
      verdict(label2, probs.length ? 'FAIL' : 'PASS', probs.length ? probs.join('; ') : `routed ok via ${r.hosts.join(',')}; dead proxy -> fails (${d.detail})`);
    };
    judge('search', 'OAuth + CMS read (HttpURLConnection GET/POST via getSSLConnection)', [/^oauth\.brightcove\.com$/, /^cms\.api\.brightcove\.com$/]);
    // Which PATCH implementation ran is read from brightcove.log (BRC_LOG), not inferred from the JVM version.
    const impl = { 'apache-fallback': 'Apache HttpClient fallback', 'httpurlconnection-reflection': 'reflection PATCH on HttpURLConnection' }[(evidence['routed:labels'] || {}).patch] || 'implementation NOT MEASURED: set BRC_LOG to the instance brightcove.log';
    if (label) judge('labels', `CMS PATCH (${impl}) via Save Labels`, [/^oauth\.brightcove\.com$/, /^cms\.api\.brightcove\.com$/]);
    judge('poster', 'Dynamic Ingest request (poster upload)', [/^ingest\.api\.brightcove\.com$/]);
    judge('workflow', 'DAM workflow sync: create + Dynamic Ingest + S3 upload', [/^cms\.api\.brightcove\.com$/, /^ingest\.api\.brightcove\.com$/, S3_HOST]);
    judge('dataload', 'dataload sync: CMS list + thumbnail fetch (getRemoteBinary -> executeFullGet)', thumbHost ? [/^cms\.api\.brightcove\.com$/, new RegExp('^' + thumbHost.replace(/\./g, '\\.') + '$')] : [/^cms\.api\.brightcove\.com$/]);

    // deny-control verdicts: the paths a dead proxy cannot isolate
    const dn = PATHS.dataload && PATHS.dataload.deny, rt = PATHS.dataload && PATHS.dataload.routed;
    if (!thumbHost || !dn || !rt) notMeasured('rendition image fetch honours proxy (deny control)', 'no thumbnail host or phase missing');
    else {
      const realFetched = dimsMatch(rt.rd, realDims); const placeholder = dn.ok && dn.rd !== null && !dimsMatch(dn.rd, realDims);
      const sawDeny = dn.denied.includes(thumbHost);
      verdict('rendition image fetch honours proxy (deny control)', realFetched && placeholder && sawDeny ? 'PASS' : 'FAIL',
        `routed rendition ${rt.rd} ~= real ${realDims} (within 1px, even-dimension encode): ${realFetched}; image host denied -> placeholder ${dn.rd}: ${placeholder}; proxy logged the denied CONNECT: ${sawDeny}`);
    }
    const pd = PATHS.poster && PATHS.poster.deny, pr = PATHS.poster && PATHS.poster.routed;
    if (!pd || !pr || !pr.hasR) notMeasured('poster rendition fetch honours proxy (deny control)', `routed run did not produce a DAM brc_poster.png rendition (hasR=${pr && pr.hasR}); the rendition path was not reached`);
    else verdict('poster rendition fetch honours proxy (deny control)', pr.hasR && !pd.hasR && pd.denied.some((h) => /httpbin\.org$/.test(h)) ? 'PASS' : 'FAIL',
      `routed brc_poster.png=${pr.hasR}; httpbin denied -> brc_poster.png=${pd.hasR}; denied hosts=${JSON.stringify(pd.denied)}`);
    const wd = PATHS.workflow && PATHS.workflow.deny;
    if (!wd) notMeasured('S3 upload honours proxy (deny control)', 'phase missing');
    else verdict('S3 upload honours proxy (deny control)', !wd.ok && wd.denied.some((h) => S3_HOST.test(h)) ? 'PASS' : 'FAIL',
      `S3 host denied -> sync ${wd.ok ? 'STILL SUCCEEDED (bypass)' : 'failed'}; denied CONNECTs=${JSON.stringify(wd.denied)}`);
    const ld = PATHS.labelsDeny && PATHS.labelsDeny.deny;
    if (!label || !label2) notMeasured('CMS PATCH honours proxy (deny control)', 'account has fewer than two labels');
    else if (!ld) notMeasured('CMS PATCH honours proxy (deny control)', 'phase missing');
    else verdict('CMS PATCH honours proxy (deny control)', !ld.ok && ld.denied.includes('cms.api.brightcove.com') && ld.hosts.includes('oauth.brightcove.com') ? 'PASS' : 'FAIL',
      `CMS host denied at the proxy, OAuth allowed -> label ${ld.ok ? 'STILL PERSISTED (bypass)' : 'not persisted'}; denied=${JSON.stringify(ld.denied)} via=${JSON.stringify(ld.hosts)}; ${ld.detail}`);
    // Save Labels must REPORT the failure (error_code), never an empty {} the UI toasts as "Labels saved".
    if (ld && label && label2) verdict('Save Labels under a denied CMS reports an error_code (no false success)', ld.reported ? 'PASS' : 'FAIL',
      ld.reported ? `update_labels answered with an error: ${ld.detail}` : `update_labels did NOT report an error while the CMS was unreachable: ${ld.detail}`);
    else notMeasured('Save Labels under a denied CMS reports an error_code (no false success)', 'account has fewer than two labels or the phase did not run');
    // BrcImageApi.doGet fetches the poster through HttpServices.getRemoteBinary (it used ImageIO.read(URL), which ignored the proxy).
    const ic = PATHS.imagecache || {};
    if (!ic.routed || !ic.dead || !ic.deny || !posterHost) notMeasured('image cache servlet BrcImageApi', 'phase or poster host missing');
    else {
      const viaProxy = ic.routed.hosts.includes(posterHost);
      const probs = [];
      if (!ic.routed.ok) probs.push(`routed run failed (${ic.routed.detail})`);
      if (!viaProxy) probs.push(`BYPASS: image host ${posterHost} never reached the proxy`);
      if (ic.dead.ok) probs.push(`BYPASS: still served the image with a dead proxy`);
      if (ic.deny.ok) probs.push(`BYPASS: still served the image with the image host denied at the proxy`);
      verdict('image cache servlet BrcImageApi honours proxy', probs.length ? 'FAIL' : 'PASS', probs.length ? probs.join('; ') : `image host via proxy; denied -> ${ic.deny.detail}`);
    }
  } finally {
    // Always: restore the original config exactly, verify it, then clean up.
    try {
      const ok = await restoreConfig(orig);
      console.log(`config restore: ${ok ? 'VERIFIED all ' + Object.keys(orig.props).length + ' properties equal to the original' : 'FAILED, recover from ' + backup}; proxyServer=${JSON.stringify((await readConfig()).props.proxyServer)}`);
      if (ok) fs.unlinkSync(backup); else process.exitCode = 3;
    } catch (e) { console.log(`config restore ERROR ${e.message}; recover from ${backup}`); process.exitCode = 3; }
    for (const ap of created.assets) { try { if ((await request.get(`${ap}.json`)).status() === 200) await slingPost(request, ap, { ':operation': 'delete' }); } catch (e) { console.log(`cleanup asset ${ap}: ${e.message}`); } }
    for (const v of created.videos) {
      // The DAM asset is named by the video id, so it is only removed once the SERVER-SIDE name
      // of that video is re-read and carries the throwaway prefix (ids can come from brc_id /
      // getByRef and are not proof of ownership). A video that is already gone is not removed
      // blind: its asset is left and reported.
      try {
        const cur = await cms.tryGet(v);
        if (cur && String(cur.name || '').startsWith(PREFIX)) {
          await slingPost(request, `${root}/${v}.mp4`, { ':operation': 'delete' }).catch(() => {});
          await cms.delIfThrowaway(v);
        } else console.log(`cleanup video ${v}: ${cur ? 'NOT deleting, name has no throwaway prefix' : 'already gone; its DAM asset (if any) is left, not verifiable'}`);
      } catch (e) { console.log(`cleanup video ${v}: ${e.message}`); }
    }
    for (const v of created.videos) { try { console.log(`cleanup video ${v}: ${(await cms.tryGet(v)) === null ? 'gone' : 'STILL PRESENT'}`); } catch (e) { /* ignore */ } }
    try { await proxy.stop(); } catch (e) { console.log(`proxy stop: ${e.message}`); }
    try { await request.dispose(); } catch (e) { /* ignore */ }
    if (probeOut) { try { fs.writeFileSync(path.join(probeOut, `evidence-${new URL(AEM_BASE).port}.json`), JSON.stringify({ verdicts, evidence }, null, 2)); } catch (e) { console.log(`evidence write: ${e.message}`); } }
  }
}

function finish() {
  const bad = verdicts.filter((v) => v.verdict !== 'PASS');
  console.log(`\nSUMMARY ${verdicts.length - bad.length} PASS, ${bad.filter((v) => v.verdict === 'FAIL').length} FAIL, ${bad.filter((v) => v.verdict === 'NOT MEASURED').length} NOT MEASURED`);
  if (bad.length && !process.exitCode) process.exitCode = 1;
}

// Summary after main() and its finally have run; then exit explicitly so no lingering
// handle (proxy sockets, keep-alive agents) can hang the process.
main()
  .catch((e) => { console.error('PROBE ERROR', e.stack || e.message); process.exitCode = 2; })
  .then(() => { finish(); process.exit(process.exitCode || 0); });
