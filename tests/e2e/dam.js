// DAM / replication / workflow kit for the publish-tier specs (parity-matrix rows
// 29-33, DAM ingest, move). App-agnostic plumbing only: the questions live in the
// specs. Context: ../parity/README.md and specs/dam-publish-tier.spec.js header.
//
// Everything here goes through AEM's own HTTP surfaces:
//   - author writes: Sling POST from the cookie session (fixtures.slingPost, which
//     carries the Referer + CSRF-Token the Sling/Granite filters demand)
//   - activation: /bin/replicate.json (the classic Replicator; on the AEMaaCS SDK
//     this is also what the Sites/Assets "Publish" action calls)
//   - workflows: POST /var/workflow/instances (Granite workflow REST)
//   - OSGi state: /system/console/components.json (basic auth, read-only)
//   - publish reads: a separate request context with basic auth against
//     AEM_PUBLISH_URL
//
// Nothing account- or customer-specific lives here: ids are resolved at run time.
const fs = require('fs');
const path = require('path');
const { request: pwRequest } = require('@playwright/test');
const { slingPost } = require('./fixtures');
const { AEM_BASE, AEM_USER, AEM_PASS } = require('./target');

const PREFIX = 'e2e-throwaway-';
const FIXTURE_MP4 = path.join(__dirname, 'fixtures', 'tiny-2s.mp4');

const AEM_PUBLISH_URL = process.env.AEM_PUBLISH_URL || '';
const AEM_PUBLISH_USER = process.env.AEM_PUBLISH_USER || AEM_USER;
const AEM_PUBLISH_PASS = process.env.AEM_PUBLISH_PASS || AEM_PASS;

const basic = (u, p) => 'Basic ' + Buffer.from(`${u}:${p}`).toString('base64');
const AUTHOR_BASIC = { Authorization: basic(AEM_USER, AEM_PASS) };

function stamp() {
  return `${Date.now()}-${Math.floor(Math.random() * 1e4)}`;
}

async function sleep(ms) {
  await new Promise((r) => setTimeout(r, ms));
}

// Poll fn() until it returns a truthy value or the timeout passes. Returns the
// value, or null on timeout (callers decide whether null is a failure).
async function pollUntil(fn, { timeout = 60_000, interval = 2_000 } = {}) {
  const end = Date.now() + timeout;
  for (;;) {
    const v = await fn();
    if (v) return v;
    if (Date.now() > end) return null;
    await sleep(interval);
  }
}

// ---- OSGi console (read-only) ------------------------------------------------

// The DS component record for `name`, with its runtime properties parsed into an
// object ({ state, props: { 'event.topics': '[a, b]', isEnabled: 'false', ... } }).
// Returns null when the component does not exist (bundle not installed).
async function component(request, name) {
  const list = await request.get('/system/console/components.json', { headers: AUTHOR_BASIC });
  if (!list.ok()) throw new Error(`components.json HTTP ${list.status()}`);
  const hit = (await list.json()).data.find((c) => c.name === name);
  if (!hit) return null;
  const det = await request.get(`/system/console/components/${hit.id}.json`, { headers: AUTHOR_BASIC });
  if (!det.ok()) throw new Error(`component ${name} HTTP ${det.status()}`);
  const d = (await det.json()).data[0];
  const props = {};
  const raw = (d.props || []).find((p) => p.key === 'Properties');
  for (const line of (raw && raw.value) || []) {
    const i = line.indexOf(' = ');
    if (i > 0) props[line.slice(0, i)] = line.slice(i + 3);
  }
  return { id: hit.id, state: d.state, props };
}

// Lines of an instance log (Sling log tailer, read-only) containing `needle`.
// `file` is the appender name, e.g. '/logs/brightcove.log' (the connector's own
// LogManager config in ui.config) or '/logs/error.log'.
async function logLines(request, file, needle, tail = 5000) {
  const res = await request.get(
    `/system/console/slinglog/tailer.txt?tail=${tail}&name=${encodeURIComponent(file)}`, { headers: AUTHOR_BASIC });
  if (!res.ok()) return null; // could not read: callers must treat null as NOT MEASURED, not as zero
  return (await res.text()).split('\n').filter((l) => l.includes(needle));
}

async function runModes(request) {
  const res = await request.get('/system/console/status-slingsettings.txt', { headers: AUTHOR_BASIC });
  if (!res.ok()) throw new Error(`slingsettings HTTP ${res.status()}`);
  const m = /Run Modes = \[([^\]]*)\]/.exec(await res.text());
  return m ? m[1].split(',').map((s) => s.trim()) : [];
}

// ---- Author JCR ----------------------------------------------------------------

async function json(request, p, depth = 0) {
  const res = await request.get(depth ? `${p}.${depth}.json` : `${p}.json`);
  if (res.status() === 404) return null;
  if (!res.ok()) throw new Error(`GET ${p} HTTP ${res.status()}`);
  return res.json();
}

async function mustPost(request, url, form, what) {
  const res = await slingPost(request, url, form);
  if (res.status() >= 300) throw new Error(`${what}: HTTP ${res.status()} ${(await res.text()).slice(0, 200)}`);
  return res;
}

// A plain DAM folder (sling:OrderedFolder). Name must carry the throwaway prefix.
async function createDamFolder(request, folderPath) {
  if (!path.posix.basename(folderPath).startsWith(PREFIX)) throw new Error(`refusing to create non-throwaway folder ${folderPath}`);
  await mustPost(request, folderPath, {
    'jcr:primaryType': 'sling:OrderedFolder',
    'jcr:content/jcr:primaryType': 'nt:unstructured',
    'jcr:content/jcr:title': path.posix.basename(folderPath),
  }, `create folder ${folderPath}`);
}

// Upload a binary as a DAM asset through the Assets UI's own upload servlet
// (<folder>.createasset.html), so the asset is created by AssetManager exactly as an
// author's drag-and-drop creates it, then wait until its metadata node exists.
// Returns { assetPath, assetState }.
// ⚠️ Not measured yet on either line. On the AEMaaCS SDK Adobe documents direct
// binary upload as the supported path; if createasset.html is refused there this
// throws with the status rather than silently falling back to a hand-built node.
async function uploadAsset(request, folderPath, fileName, filePath = FIXTURE_MP4, mimeType = 'video/mp4') {
  if (!fileName.startsWith(PREFIX)) throw new Error(`refusing to upload non-throwaway asset ${fileName}`);
  const tok = await (await request.get('/libs/granite/csrf/token.json')).json();
  const res = await request.post(`${folderPath}.createasset.html`, {
    headers: { 'CSRF-Token': tok.token, Referer: `${AEM_BASE}/` },
    multipart: {
      file: { name: fileName, mimeType, buffer: fs.readFileSync(filePath) },
      fileName,
      _charset_: 'utf-8',
    },
  });
  if (res.status() >= 300) throw new Error(`createasset.html ${folderPath}/${fileName}: HTTP ${res.status()}`);
  const assetPath = `${folderPath}/${fileName}`;
  const meta = await pollUntil(() => json(request, `${assetPath}/jcr:content/metadata`), { timeout: 60_000 });
  if (!meta) throw new Error(`${assetPath} has no jcr:content/metadata 60s after upload`);
  // Let DAM processing (DAM Update Asset on 6.5, asset processing on the SDK) finish
  // before the caller writes metadata, or metadata extraction can overwrite it.
  // ⚠️ The local SDK has no Asset Compute, so an asset there may never reach
  // "processed"; the state is returned so the spec can record it, not asserted here.
  const state = await pollUntil(async () => {
    const c = await json(request, `${assetPath}/jcr:content`);
    return c && c['dam:assetState'] === 'processed' ? 'processed' : null;
  }, { timeout: 90_000, interval: 3_000 });
  const c = await json(request, `${assetPath}/jcr:content`);
  return { assetPath, assetState: state || (c && c['dam:assetState']) || 'absent' };
}

async function assetNode(request, assetPath) {
  return json(request, assetPath);
}

async function metadata(request, assetPath) {
  return json(request, `${assetPath}/jcr:content/metadata`);
}

// Set metadata properties. Values: string, or { date: Date } for a JCR Date, or
// { long: n } for a Long. brc_lastsync is read by the connector as Long but written
// as a Calendar by its own sync code; a Date property satisfies both reads.
async function setMetadata(request, assetPath, props) {
  const form = {};
  for (const [k, v] of Object.entries(props)) {
    if (v && typeof v === 'object' && v.date) {
      form[k] = v.date.toISOString();
      form[`${k}@TypeHint`] = 'Date';
    } else if (v && typeof v === 'object' && 'long' in v) {
      form[k] = String(v.long);
      form[`${k}@TypeHint`] = 'Long';
    } else if (v === null) {
      form[`${k}@Delete`] = '';
    } else {
      form[k] = String(v);
    }
  }
  await mustPost(request, `${assetPath}/jcr:content/metadata`, form, `set metadata on ${assetPath}`);
}

async function setProps(request, nodePath, form) {
  await mustPost(request, nodePath, form, `set props on ${nodePath}`);
}

// JCR move (Sling :operation=move). Metadata travels with the node, which is what
// the DAM move wizard also guarantees; the wizard additionally rewrites references.
async function moveNode(request, from, toParent) {
  const dest = `${toParent}/${path.posix.basename(from)}`;
  await mustPost(request, from, { ':operation': 'move', ':dest': dest }, `move ${from} -> ${dest}`);
  return dest;
}

// Delete a throwaway node on the author. Never deletes anything without the prefix.
async function deleteNode(request, nodePath) {
  if (!nodePath || !path.posix.basename(nodePath).startsWith(PREFIX)) return;
  const res = await slingPost(request, nodePath, { ':operation': 'delete' });
  if (res.status() >= 300 && res.status() !== 404) console.warn(`[dam] delete ${nodePath}: HTTP ${res.status()}`);
}

// ---- Replication ----------------------------------------------------------------

// Activate one path through the classic Replicator (all enabled agents). Returns the
// author-side cq:lastReplicationAction marker once it is written.
async function activate(request, nodePath, cmd = 'Activate') {
  await mustPost(request, '/bin/replicate.json', { cmd, path: nodePath }, `${cmd} ${nodePath}`);
}

async function enabledAgents(request) {
  const body = await json(request, '/etc/replication/agents.author', 2);
  const out = [];
  for (const [name, v] of Object.entries(body || {})) {
    const c = v && v['jcr:content'];
    if (c && String(c.enabled) === 'true') out.push({ name, transportUri: c.transportUri || '' });
  }
  return out;
}

// ---- Publish reads -------------------------------------------------------------

async function publishContext() {
  if (!AEM_PUBLISH_URL) return null;
  return pwRequest.newContext({
    baseURL: AEM_PUBLISH_URL,
    extraHTTPHeaders: { Authorization: basic(AEM_PUBLISH_USER, AEM_PUBLISH_PASS) },
  });
}

// Wait until `nodePath` exists on publish. Returns its JSON or null.
async function waitOnPublish(pub, nodePath, { timeout = 90_000 } = {}) {
  return pollUntil(async () => {
    const r = await pub.get(`${nodePath}.json`);
    return r.ok() ? r.json() : null;
  }, { timeout });
}

// Remove a throwaway node from publish directly (no replication), so cleanup never
// fires the connector's deactivate path on the author.
async function deleteOnPublish(pub, nodePath) {
  if (!pub || !nodePath || !path.posix.basename(nodePath).startsWith(PREFIX)) return;
  const r = await pub.post(nodePath, {
    form: { ':operation': 'delete' },
    headers: { Referer: `${AEM_PUBLISH_URL}/` },
  });
  if (r.status() >= 300 && r.status() !== 404) console.warn(`[dam] publish delete ${nodePath}: HTTP ${r.status()}`);
}

// ---- Workflows -----------------------------------------------------------------

// Start `modelId` (e.g. /var/workflow/models/bc-sync-new-asset) on a JCR payload and
// wait for a terminal state. Returns { instance, status }.
// ⚠️ Endpoint not measured yet: /var/workflow/instances is the 6.4+ location, the
// legacy /etc/workflow/instances is tried only when the first one is not routed.
async function runWorkflow(request, modelId, payload, { timeout = 180_000 } = {}) {
  const form = { model: modelId, payloadType: 'JCR_PATH', payload };
  let res = await slingPost(request, '/var/workflow/instances', form);
  if (res.status() === 404 || res.status() === 405) res = await slingPost(request, '/etc/workflow/instances', form);
  if (res.status() !== 201) throw new Error(`start ${modelId} on ${payload}: HTTP ${res.status()} ${(await res.text()).slice(0, 200)}`);
  const loc = res.headers().location;
  if (!loc) throw new Error(`start ${modelId}: 201 with no Location header`);
  const instance = new URL(loc, 'http://x').pathname.replace(/\.html$/, '');
  const terminal = await pollUntil(async () => {
    const r = await request.get(`${instance}.json`);
    if (!r.ok()) return null;
    const b = await r.json();
    const s = b.status || b.state;
    return s && s !== 'RUNNING' ? s : null;
  }, { timeout, interval: 3_000 });
  return { instance, status: terminal || 'TIMEOUT' };
}

// ---- Throwaway bookkeeping -----------------------------------------------------

// Records everything a spec creates so afterAll can remove it even when a test died
// half-way. Cleanup re-derives video ids from the DAM side too (brc_id and the
// jcr:uuid reference id), because a failed test may have created a video it never
// got to record. Every delete is guarded by the throwaway prefix (cms.delIfThrowaway,
// cms.delFolderIfThrowaway, deleteNode, deleteOnPublish).
function tracker() {
  const t = { videos: new Set(), assets: new Set(), folders: new Set(), bcFolderNames: new Set() };
  t.cleanup = async ({ cms, request, pub }) => {
    const errors = [];
    const tryDo = async (what, fn) => { try { await fn(); } catch (e) { errors.push(`${what}: ${e.message}`); } };
    for (const a of t.assets) {
      await tryDo(`read ${a}`, async () => {
        const n = await json(request, a);
        const m = await json(request, `${a}/jcr:content/metadata`);
        if (m && m.brc_id) t.videos.add(String(m.brc_id));
        if (n && n['jcr:uuid'] && cms) {
          const v = await cms.getByRef(n['jcr:uuid']);
          if (v) t.videos.add(String(v.id));
        }
      });
    }
    if (cms) {
      for (const id of t.videos) await tryDo(`delete video ${id}`, () => cms.delIfThrowaway(id));
      await tryDo('delete folders', async () => {
        for (const f of await cms.folders()) {
          if (t.bcFolderNames.has(f.name)) await cms.delFolderIfThrowaway(f);
        }
      });
    }
    for (const p of [...t.assets, ...t.folders]) {
      await tryDo(`delete ${p}`, () => deleteNode(request, p));
      await tryDo(`publish delete ${p}`, () => deleteOnPublish(pub, p));
    }
    if (errors.length) console.warn(`[dam] cleanup problems:\n  ${errors.join('\n  ')}`);
    return errors;
  };
  return t;
}

module.exports = {
  tracker,
  PREFIX, FIXTURE_MP4, AEM_PUBLISH_URL, stamp, sleep, pollUntil,
  component, runModes, logLines,
  json, createDamFolder, uploadAsset, assetNode, metadata, setMetadata, setProps, moveNode, deleteNode,
  activate, enabledAgents,
  publishContext, waitOnPublish, deleteOnPublish,
  runWorkflow,
};
