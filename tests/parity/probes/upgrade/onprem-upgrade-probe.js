#!/usr/bin/env node
// On-prem in-place upgrade probe: connector 6.0.x -> unified 7.x (-prem).
// Context: current/docs/onprem-upgrade-6.0-to-7.md ("The upgrade path that was actually
// verified") and tests/parity/matrix.md (upgrade rows).
//
// Two phases against the SAME instance:
//
//   node onprem-upgrade-probe.js snapshot --aem http://localhost:4704 --out before.json
//     (on the 6.0.x install, before the upgrade; records state, asserts nothing)
//   node onprem-upgrade-probe.js check --aem http://localhost:4704 --before before.json \
//        --zip current/all/target/brightcove.all-<v>-prem.zip [--resync] [--thumbless-delete]
//     (after installing the -prem package through the package manager)
//
// Every check prints PASS, FAIL (expected vs actual) or NOT MEASURED. Exit code is 0 only
// when every check is PASS. Informational lines are prefixed "INFO".
//
// --resync          POSTs /bin/brightcove/dataload and asserts no asset is duplicated.
// --thumbless-delete  DESTRUCTIVE to the DAM of the target instance (throwaway beds only):
//                   deletes the DAM assets of videos that have no thumbnail, re-runs the
//                   dataload and asserts they come back (the bundled-placeholder fix).
//
// Env: AEM_AUTH (default admin:admin). No account ids or secrets are printed: the
// account id is read at run time from /bin/brightcove/accounts and shown as <account>.
'use strict';
const fs = require('fs');
const crypto = require('crypto');
const { execFileSync } = require('child_process');

const args = process.argv.slice(2);
const mode = args[0];
const opt = (n, d) => { const i = args.indexOf(`--${n}`); return i > 0 ? args[i + 1] : d; };
const flag = (n) => args.includes(`--${n}`);
const AEM = opt('aem', process.env.AEM_BASE || 'http://localhost:4704');
const AUTH = 'Basic ' + Buffer.from(process.env.AEM_AUTH || 'admin:admin').toString('base64');

const AGENTS = '/etc/replication/agents.author';
const DAM_ROOT = '/content/dam/brightcove_assets';
const LEGACY_BUNDLE = 'com.coresecure.brightcove.cq5.brightcove-services';
const CORE_BUNDLE = 'brightcove.core';
const LEGACY_PID = 'com.coresecure.brightcove.wrapper.sling.BrcServiceImpl';
const CURRENT_PID = 'com.coresecure.brightcove.wrapper.sling.ConfigurationServiceImpl';
const CLEARED_PATHS = ['/apps/brightcove/install', '/apps/brightcove/runmodes',
  '/etc/designs/cs/brightcove', '/etc/clientlibs/brightcove'];
// What the 7.x repoinit scripts must grant brightcove_admin (shared + ui.config.onprem).
const EXPECTED_GRANTS = { '/content': 'jcr:read', '/home/groups': 'jcr:read', '/home/users': 'jcr:read' };

let ACCOUNT = null; // resolved at run time, never printed
const redact = (s) => (ACCOUNT ? String(s).split(ACCOUNT).join('<account>') : String(s));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function get(p, { raw = false } = {}) {
  const res = await fetch(AEM + p, { headers: { Authorization: AUTH }, redirect: 'manual' });
  if (raw) return res;
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) { /* not JSON */ }
  return { status: res.status, json, text };
}
async function post(p, form) {
  const body = new URLSearchParams(form || {});
  const res = await fetch(AEM + p, { method: 'POST', headers: { Authorization: AUTH, Referer: AEM + '/' }, body });
  return { status: res.status, text: await res.text() };
}
const qb = async (q) => {
  const r = await get(`/bin/querybuilder.json?${q}&p.limit=-1&p.hits=full&p.nodedepth=0`);
  if (r.status !== 200 || !r.json) throw new Error(`querybuilder HTTP ${r.status}`);
  return r.json.hits;
};

// ---- state readers (shared by snapshot and check) ---------------------------------------

async function bundles() {
  const r = await get('/system/console/bundles.json');
  return r.json.data.filter((b) => /brightcove|coresecure/i.test(b.symbolicName))
    .map((b) => ({ id: b.id, symbolicName: b.symbolicName, version: b.version, state: b.state }));
}
async function failedComponents() {
  const r = await get('/system/console/components.json');
  const all = r.json.data;
  const failed = all.filter((c) => /fail/i.test(c.state || ''));
  return { total: failed.length, connector: failed.filter((c) => /coresecure|brightcove/i.test(c.name)).map((c) => c.name) };
}
async function agents() {
  const r = await get(`${AGENTS}.2.json`);
  const out = [];
  for (const [name, node] of Object.entries(r.json || {})) {
    const c = node && node['jcr:content'];
    if (!c || typeof c !== 'object') continue;
    out.push({ name, transportUri: c.transportUri, enabled: c.enabled, title: c['jcr:title'],
      description: c['jcr:description'] ?? null, userId: c.userId ?? null, serializationType: c.serializationType ?? null });
  }
  return out;
}
async function brcAces() {
  const hits = await qb('path=/&type=rep:ACE&property=rep:principalName&property.value=brightcove_admin');
  return hits.map((h) => ({ path: h['jcr:path'], type: h['jcr:primaryType'], privileges: [...(h['rep:privileges'] || [])].sort(),
    restrictions: Object.keys(h).filter((k) => /^rep:(glob|ntNames|itemNames|prefixes)$/.test(k)) }))
    .sort((a, b) => a.path.localeCompare(b.path));
}
async function pathStatus(paths) {
  const o = {};
  for (const p of paths) o[p] = (await get(`${p}.json`)).status;
  return o;
}
async function etcClientlibs() {
  const hits = await qb('path=/etc&type=cq:ClientLibraryFolder');
  return hits.filter((h) => /brightcove/.test(h['jcr:path'])).map((h) => ({ path: h['jcr:path'], categories: h.categories || [] }));
}
async function brcCategoryOwners() {
  // every clientlib folder declaring a brc.* category, anywhere the HtmlLibraryManager looks
  const owners = {};
  for (const root of ['/apps', '/etc', '/libs']) {
    for (const h of await qb(`path=${root}&type=cq:ClientLibraryFolder`)) {
      for (const c of [].concat(h.categories || [])) {
        if (/^brc\./.test(c)) (owners[c] = owners[c] || []).push(h['jcr:path']);
      }
    }
  }
  return owners;
}
async function dam() {
  const hits = await qb(`path=${DAM_ROOT}&type=dam:Asset`);
  const assets = {};
  for (const h of hits) {
    const m = await get(`${h['jcr:path']}/jcr:content/metadata.json`);
    assets[h['jcr:path']] = (m.json && m.json.brc_id) || null;
  }
  return assets;
}
async function configs() {
  const r = await get('/system/console/status-Configurations.txt');
  const out = [];
  for (const block of r.text.split('\n\n')) {
    const fp = /Factory PID = (\S+)/.exec(block);
    if (!fp || !/brightcove\.wrapper\.sling\.(BrcServiceImpl|ConfigurationServiceImpl)$/.test(fp[1])) continue;
    const props = {};
    for (const l of block.split('\n')) {
      const m = /^\s+([\w.]+) = (.*)$/.exec(l);
      if (m && m[1] !== 'Factory PID' && m[1] !== 'PID') props[m[1]] = m[2];
    }
    const acct = props.key || props.accountId || '';
    out.push({ factoryPid: fp[1], keys: Object.keys(props).sort(), accountIdLength: acct.length,
      groups: props.allowed_groups || props.allowedGroups || null, account: acct });
  }
  return out;
}
async function accountsServlet() {
  const r = await get('/bin/brightcove/accounts.json');
  const list = (r.json && r.json.accounts) || [];
  return list.map((a) => String(a.value));
}
async function searchVideos(account, limit = 100) {
  const r = await get(`/bin/brightcove/api.json?account_id=${account}&a=search_videos&query=&limit=${limit}&start=0`);
  return r.json && Array.isArray(r.json.items) ? r.json.items : null;
}
async function snapshot() {
  const cfg = await configs();
  ACCOUNT = (cfg.find((c) => c.account) || {}).account || null;
  const strip = cfg.map(({ account, ...rest }) => rest);
  return {
    takenAt: new Date().toISOString(), aem: AEM,
    bundles: await bundles(), failedComponents: await failedComponents(), agents: await agents(),
    brcAces: await brcAces(), paths: await pathStatus(CLEARED_PATHS), etcClientlibs: await etcClientlibs(),
    configs: strip, accountsListed: (await accountsServlet()).length,
    dam: Object.fromEntries(Object.entries(await dam()).map(([p, id]) => [redact(p), id ? redact(id) : null])),
  };
}

// ---- checks ---------------------------------------------------------------------------------

const results = [];
function rec(name, verdict, detail) {
  results.push({ name, verdict, detail: redact(detail) });
  console.log(`${verdict.padEnd(12)} ${name}${detail ? ' :: ' + redact(detail) : ''}`);
}
const PASS = (n, d) => rec(n, 'PASS', d);
const FAIL = (n, d) => rec(n, 'FAIL', d);
const NM = (n, d) => rec(n, 'NOT MEASURED', d);
const info = (s) => console.log(`INFO         ${redact(s)}`);
async function guard(name, fn) {
  try { await fn(); } catch (e) { NM(name, `probe error: ${e.message}`); }
}

function zipCoreJar(zip) {
  const list = execFileSync('unzip', ['-Z1', zip]).toString().split('\n');
  const jar = list.find((l) => /application\/install\/brightcove\.core-[^/]+\.jar$/.test(l));
  if (!jar) return null;
  const buf = execFileSync('unzip', ['-p', zip, jar], { maxBuffer: 64 * 1024 * 1024 });
  return { entry: jar, version: /brightcove\.core-(.+)\.jar$/.exec(jar)[1], sha: crypto.createHash('sha256').update(buf).digest('hex') };
}

async function settleDataload(label) {
  const r = await post('/bin/brightcove/dataload', {});
  info(`${label}: POST /bin/brightcove/dataload -> HTTP ${r.status}, body ${r.text.length} bytes`);
  // the import runs on an executor; wait until the asset count is stable for 3 polls
  let last = -1; let stable = 0;
  for (let i = 0; i < 60 && stable < 3; i++) {
    await sleep(10_000);
    const n = (await qb(`path=${DAM_ROOT}&type=dam:Asset`)).length;
    stable = n === last ? stable + 1 : 0; last = n;
  }
  return r.status;
}

async function check() {
  const before = JSON.parse(fs.readFileSync(opt('before'), 'utf8'));
  const zip = opt('zip');
  const cfg = await configs();
  ACCOUNT = (cfg.find((c) => c.account) || {}).account || null;

  // 1. bundles
  await guard('core bundle Active at the zip version', async () => {
    const z = zip ? zipCoreJar(zip) : null;
    const b = await bundles();
    info(`bundles before: ${before.bundles.map((x) => `${x.symbolicName} ${x.version} ${x.state}`).join('; ')}`);
    info(`bundles after:  ${b.map((x) => `${x.symbolicName} ${x.version} ${x.state}`).join('; ')}`);
    const core = b.filter((x) => x.symbolicName === CORE_BUNDLE);
    if (!z) return NM('core bundle Active at the zip version', '--zip not given or no core jar in it');
    const want = z.version.replace(/-SNAPSHOT$/, '.SNAPSHOT');
    if (core.length === 1 && core[0].state === 'Active' && core[0].version === want) PASS('core bundle Active at the zip version', `${core[0].version} Active`);
    else FAIL('core bundle Active at the zip version', `expected one ${CORE_BUNDLE} ${want} Active, got ${JSON.stringify(core)}`);
    const legacy = b.filter((x) => x.symbolicName === LEGACY_BUNDLE);
    if (legacy.length === 0) PASS('legacy 6.0.x bundle gone', `before: ${before.bundles.filter((x) => x.symbolicName === LEGACY_BUNDLE).map((x) => x.version + ' ' + x.state).join(',') || 'absent'}`);
    else FAIL('legacy 6.0.x bundle gone', `still installed: ${JSON.stringify(legacy)}`);
    const stale = b.filter((x) => x.symbolicName !== CORE_BUNDLE);
    if (stale.length === 0) PASS('no stale connector bundles', 'only brightcove.core');
    else FAIL('no stale connector bundles', JSON.stringify(stale));
  });
  await guard('zero failed components', async () => {
    const f = await failedComponents();
    if (f.connector.length === 0) PASS('zero failed connector components', `instance-wide failed: ${f.total}`);
    else FAIL('zero failed connector components', f.connector.join(', '));
  });
  // 2. jar digest
  await guard('running jar is the jar in the zip', async () => {
    const z = zip ? zipCoreJar(zip) : null;
    if (!z) return NM('running jar is the jar in the zip', 'no --zip');
    const res = await get(`/apps/brightcove-packages/application/install/brightcove.core-${z.version}.jar`, { raw: true });
    if (res.status !== 200) return FAIL('running jar is the jar in the zip', `GET install node HTTP ${res.status}`);
    const sha = crypto.createHash('sha256').update(Buffer.from(await res.arrayBuffer())).digest('hex');
    if (sha === z.sha) PASS('running jar is the jar in the zip', `sha256 ${sha.slice(0, 12)}...`);
    else FAIL('running jar is the jar in the zip', `zip ${z.sha.slice(0, 12)} vs JCR ${sha.slice(0, 12)}`);
  });
  // 3. legacy config read
  await guard('legacy 6.0.x config is served', async () => {
    const legacy = cfg.filter((c) => c.factoryPid === LEGACY_PID);
    const current = cfg.filter((c) => c.factoryPid === CURRENT_PID);
    info(`configs: ${cfg.map((c) => `${c.factoryPid.split('.').pop()} groups=${c.groups} keys=[${c.keys.join(',')}]`).join(' | ')}`);
    if (legacy.length === 0) return NM('legacy 6.0.x config is served', 'no BrcServiceImpl config on the instance');
    if (current.length) return NM('legacy 6.0.x config is served', 'a current-format config also exists, so the legacy read is not isolated');
    const listed = await accountsServlet();
    if (listed.includes(legacy[0].account)) PASS('accounts servlet lists the legacy account', `${listed.length} account(s)`);
    else FAIL('accounts servlet lists the legacy account', `listed ${listed.length}, legacy account not among them`);
    const items = await searchVideos(legacy[0].account, 3);
    if (items && items.length) PASS('search_videos works through the legacy config', `${items.length} items`);
    else FAIL('search_videos works through the legacy config', 'no items');
  });
  // 4. agent
  await guard('replication agent preserved', async () => {
    const a = await agents();
    const brc = a.filter((x) => /^brightcove:\/\//.test(x.transportUri || ''));
    const b0 = before.agents.find((x) => x.name === 'brightcove');
    info(`agents before: ${JSON.stringify(before.agents.filter((x) => /^brightcove:/.test(x.transportUri || '')))}`);
    info(`agents after:  ${JSON.stringify(brc)}`);
    if (brc.length === 1 && brc[0].name === 'brightcove') PASS('exactly one brightcove:// agent', '/etc/replication/agents.author/brightcove');
    else FAIL('exactly one brightcove:// agent', `found ${brc.map((x) => x.name).join(',') || 'none'}`);
    const now = a.find((x) => x.name === 'brightcove');
    if (!b0 || !now) return NM('agent customer edit preserved', 'agent missing before or after');
    if (!b0.description) NM('agent customer edit preserved', 'no jcr:description marker was set before the upgrade');
    else if (now.description === b0.description) PASS('agent customer edit preserved', 'jcr:description unchanged');
    else FAIL('agent customer edit preserved', `before "${b0.description}" after "${now.description}"`);
    const same = ['transportUri', 'enabled', 'title', 'userId', 'serializationType'].filter((k) => b0[k] !== now[k]);
    if (same.length === 0 && String(now.enabled) === 'true') PASS('agent still enabled, definition not overwritten', `enabled=${now.enabled}`);
    else FAIL('agent still enabled, definition not overwritten', `changed: ${same.join(',')} enabled=${now.enabled}`);
  });
  // 5. ACLs
  await guard('brightcove_admin grants', async () => {
    const aces = await brcAces();
    info(`brightcove_admin ACEs before: ${JSON.stringify(before.brcAces)}`);
    info(`brightcove_admin ACEs after:  ${JSON.stringify(aces)}`);
    const rootB = before.brcAces.filter((x) => x.path.startsWith('/rep:policy/'));
    const rootA = aces.filter((x) => x.path.startsWith('/rep:policy/'));
    // Pins the MEASURED behaviour: the 7.x package does not own /rep:policy, so the 6.0.x
    // root grant is left in place. If a later build removes it, update this expectation.
    if (rootB.length === 0) NM('6.0.x root policy left as it was', 'no root ACE before the upgrade');
    else if (JSON.stringify(rootA) === JSON.stringify(rootB)) PASS('6.0.x root policy left as it was', `retained: ${rootA.map((x) => x.privileges.join(' ')).join(' | ')}`);
    else FAIL('6.0.x root policy left as it was', `before ${JSON.stringify(rootB)} after ${JSON.stringify(rootA)}`);
    for (const [p, priv] of Object.entries(EXPECTED_GRANTS)) {
      const hit = aces.find((x) => x.path.startsWith(`${p}/rep:policy/`) && x.type === 'rep:GrantACE' && x.privileges.includes(priv));
      if (hit) PASS(`repoinit grant on ${p}`, hit.privileges.join(' '));
      else FAIL(`repoinit grant on ${p}`, `no ${priv} GrantACE for brightcove_admin at ${p}`);
    }
    const tail = await get(`/system/console/slinglog/tailer.txt?tail=200000&name=${encodeURIComponent('/logs/error.log')}`);
    if (tail.status !== 200) return NM('no repoinit errors in error.log', `tailer HTTP ${tail.status}`);
    const bad = tail.text.split('\n').filter((l) => /repoinit/i.test(l) && /\*ERROR\*|ParseException|RepoInitParsingException|Failed to parse/i.test(l));
    if (bad.length === 0) PASS('no repoinit errors in error.log', 'none in the last 200000 lines');
    else FAIL('no repoinit errors in error.log', bad.slice(0, 3).join(' || '));
  });
  // 6. cleared paths, clientlibs
  await guard('legacy paths cleared', async () => {
    const now = await pathStatus(CLEARED_PATHS);
    for (const p of CLEARED_PATHS) {
      const b = before.paths[p];
      if (now[p] === 404) PASS(`${p} cleared`, `before HTTP ${b}, after 404`);
      else FAIL(`${p} cleared`, `before HTTP ${b}, after HTTP ${now[p]}`);
    }
    const etc = await etcClientlibs();
    if (etc.length === 0) PASS('no brightcove clientlib folders under /etc', `before: ${before.etcClientlibs.map((x) => x.categories.join('+')).join(', ')}`);
    else FAIL('no brightcove clientlib folders under /etc', etc.map((x) => x.path).join(', '));
    const owners = await brcCategoryOwners();
    const dup = Object.entries(owners).filter(([, v]) => v.length > 1);
    if (dup.length === 0) PASS('each brc.* clientlib category has one owner', Object.keys(owners).length + ' categories');
    else FAIL('each brc.* clientlib category has one owner', JSON.stringify(dup));
    const page = await get('/brightcove/admin.html');
    if (page.status !== 200) return FAIL('admin page loads each script once', `HTTP ${page.status}`);
    const srcs = [...page.text.matchAll(/<script[^>]+src="([^"]+)"/g)].map((m) => m[1].replace(/\.(min\.)?[0-9a-f]{32}\./, '.'));
    const seen = {}; srcs.forEach((s) => { seen[s] = (seen[s] || 0) + 1; });
    const twice = Object.entries(seen).filter(([, n]) => n > 1);
    const legacy = srcs.filter((s) => s.startsWith('/etc/designs/cs/brightcove') || s.startsWith('/etc/clientlibs/brightcove'));
    info(`admin page scripts (${srcs.length}): ${srcs.join(' ')}`);
    if (twice.length === 0 && legacy.length === 0) PASS('admin page loads each script once, none from /etc', `${srcs.length} scripts`);
    else FAIL('admin page loads each script once, none from /etc', `dupes ${JSON.stringify(twice)} legacy ${legacy.join(',')}`);
  });
  // 7. DAM intact
  const beforeDam = before.dam;
  const damCompare = async (label) => {
    const now = Object.fromEntries(Object.entries(await dam()).map(([p, id]) => [redact(p), id ? redact(id) : null]));
    const lost = Object.keys(beforeDam).filter((p) => !(p in now));
    // a brc_id that was absent before and present now is a gain (re-import), not a change
    const changed = Object.keys(beforeDam).filter((p) => p in now && beforeDam[p] && now[p] !== beforeDam[p]);
    const gained = Object.keys(beforeDam).filter((p) => p in now && !beforeDam[p] && now[p]);
    const ids = Object.values(now).filter(Boolean);
    const dupIds = [...new Set(ids.filter((x, i) => ids.indexOf(x) !== i))];
    const added = Object.keys(now).filter((p) => !(p in beforeDam));
    const cnt = (o) => `${Object.keys(o).length} assets / ${Object.values(o).filter(Boolean).length} with brc_id`;
    info(`${label} DAM before: ${cnt(beforeDam)}; now: ${cnt(now)}; new paths: ${added.length}; brc_id gained: ${gained.length}`);
    return { now, lost, changed, dupIds, added };
  };
  await guard('DAM assets intact', async () => {
    const d = await damCompare('post-upgrade');
    if (d.lost.length === 0 && d.changed.length === 0) PASS('DAM assets intact (paths and brc_id)', `${Object.keys(beforeDam).length} before, all present, brc_id unchanged`);
    else FAIL('DAM assets intact (paths and brc_id)', `lost ${d.lost.length}, brc_id changed ${d.changed.length}`);
  });
  if (flag('resync')) {
    await guard('dataload re-sync does not duplicate', async () => {
      await settleDataload('re-sync');
      const d = await damCompare('after re-sync');
      if (d.dupIds.length === 0 && d.lost.length === 0) PASS('dataload re-sync does not duplicate assets', `${d.added.length} new paths (videos new in the account since the snapshot)`);
      else FAIL('dataload re-sync does not duplicate assets', `duplicate brc_id ${d.dupIds.length}, lost ${d.lost.length}`);
      const dupNames = d.added.filter((p) => /-\d+\.\w+$|copy/i.test(p));
      if (dupNames.length) info(`suspicious new paths: ${dupNames.join(', ')}`);
    });
  } else NM('dataload re-sync does not duplicate assets', 'run with --resync');
  if (flag('thumbless-delete')) {
    await guard('thumbnail-less videos import', async () => {
      if (!ACCOUNT) return NM('thumbnail-less videos import', 'no account');
      const items = await searchVideos(ACCOUNT, 100);
      const thumbless = (items || []).filter((v) => !(((v.images || {}).thumbnail || {}).src) && v.state === 'ACTIVE');
      const paths = (await qb(`path=${DAM_ROOT}&type=dam:Asset`)).map((h) => h['jcr:path']);
      const targets = paths.filter((p) => thumbless.some((v) => p.endsWith(`/${v.id}.mp4`) || p.includes(`/${v.id}.`)));
      if (targets.length === 0) return NM('thumbnail-less videos import', `${thumbless.length} thumbnail-less videos, none with a DAM asset to delete`);
      for (const p of targets) await post(p, { ':operation': 'delete' });
      const gone = (await qb(`path=${DAM_ROOT}&type=dam:Asset`)).length;
      info(`deleted ${targets.length} thumbnail-less assets; DAM now ${gone}`);
      await settleDataload('thumbless re-import');
      const after = (await qb(`path=${DAM_ROOT}&type=dam:Asset`)).map((h) => h['jcr:path']);
      const back = targets.filter((p) => after.includes(p));
      if (back.length === targets.length) PASS('thumbnail-less videos import', `${back.length}/${targets.length} re-imported`);
      else FAIL('thumbnail-less videos import', `${back.length}/${targets.length} re-imported`);
      const roots = (await brcAces()).filter((x) => x.path.startsWith('/rep:policy/'));
      if (roots.length) info('⚠️ the 6.0.x root grant is still present, so brightcove_admin can read /apps: this result does not by itself discriminate the bundled-placeholder fix');
    });
  } else NM('thumbnail-less videos import', 'run with --thumbless-delete (destructive to the target DAM)');

  const bad = results.filter((r) => r.verdict !== 'PASS');
  console.log(`\nSUMMARY ${results.length - bad.length}/${results.length} PASS; ${bad.filter((r) => r.verdict === 'FAIL').length} FAIL; ${bad.filter((r) => r.verdict === 'NOT MEASURED').length} NOT MEASURED`);
  const out = opt('json');
  if (out) fs.writeFileSync(out, JSON.stringify({ at: new Date().toISOString(), aem: AEM, results }, null, 2));
  process.exit(bad.length ? 1 : 0);
}

(async () => {
  if (mode === 'snapshot') {
    const s = await snapshot();
    fs.writeFileSync(opt('out', 'before.json'), JSON.stringify(s, null, 2));
    console.log(`snapshot written: ${Object.keys(s.dam).length} DAM assets, ${s.bundles.length} connector bundles, ${s.agents.length} agents`);
  } else if (mode === 'check') {
    await check();
  } else {
    console.error('usage: onprem-upgrade-probe.js snapshot|check --aem <url> ... (see header)');
    process.exit(2);
  }
})().catch((e) => { console.error(`probe crashed: ${redact(e.stack || e)}`); process.exit(3); });
