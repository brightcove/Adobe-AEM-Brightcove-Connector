// Read, write and restore the connector's factory OSGi config through the Felix console, and
// flip its `proxyServer`. App-specific helper shared by the proxy probe and the specs that need
// a proxied or denied CMS. Context: ../parity/probes/proxy/README.md
//
// ⚠️ The Felix save posts the COMPLETE property set: any property left out reverts to its
// metatype default (credentials become empty strings while `is_set` still reads true). So every
// write here sends every property, and every check compares by value.
const fs = require('fs');
const path = require('path');
const { AEM_BASE, AEM_USER, AEM_PASS, assertLocalOrOptIn } = require('./target');

const FACTORY = 'com.coresecure.brightcove.wrapper.sling.ConfigurationServiceImpl';
const BASIC = 'Basic ' + Buffer.from(`${AEM_USER}:${AEM_PASS}`).toString('base64');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

async function readConfig() {
  const res = await fetch(`${AEM_BASE}/system/console/configMgr/${FACTORY}.*.json`, { headers: { Authorization: BASIC, Referer: `${AEM_BASE}/` } });
  if (res.status !== 200) throw new Error(`configMgr read HTTP ${res.status}`);
  const all = JSON.parse(await res.text());
  if (all.length !== 1) throw new Error(`expected exactly one ${FACTORY} config, found ${all.length}; refusing to guess`);
  const props = {};
  for (const [k, v] of Object.entries(all[0].properties)) props[k] = v.values !== undefined ? v.values : v.value;
  return { pid: all[0].pid, props };
}

async function writeConfig(pid, props) {
  assertLocalOrOptIn('write the connector OSGi config'); // every write (setProxy, restoreConfig) funnels through here
  const names = Object.keys(props);
  const body = new URLSearchParams({ apply: 'true', action: 'ajaxConfigManager', propertylist: names.join(',') });
  for (const k of names) (Array.isArray(props[k]) ? props[k] : [props[k]]).forEach((x) => body.append(k, x));
  const res = await fetch(`${AEM_BASE}/system/console/configMgr/${pid}`, { method: 'POST', headers: { Authorization: BASIC, Referer: `${AEM_BASE}/` }, body });
  if (res.status !== 200) throw new Error(`configMgr write HTTP ${res.status}`);
}

// Set proxyServer and wait until it reads back with every other property unchanged.
async function setProxy(orig, value) {
  await writeConfig(orig.pid, { ...orig.props, proxyServer: value });
  for (let i = 0; i < 20; i++) {
    const now = await readConfig();
    if (now.props.proxyServer === value && same({ ...now.props, proxyServer: 0 }, { ...orig.props, proxyServer: 0 })) { await sleep(1500); return; }
    await sleep(500);
  }
  throw new Error(`proxyServer did not read back as ${JSON.stringify(value)} with every other property unchanged`);
}

// Write the original back and verify by value. Returns true when verified.
async function restoreConfig(orig) {
  await writeConfig(orig.pid, orig.props);
  for (let i = 0; i < 20; i++) {
    if (same((await readConfig()).props, orig.props)) return true;
    await sleep(500);
  }
  return false;
}

// Crash-recovery copy of the config. It holds the account's client_secret, so: mode 0600, in
// the gitignored parity run folder (tests/parity/.gitignore: runs/), never os.tmpdir(). The
// caller deletes it once the restore verifies; its CONTENTS are never logged, only the path.
function backupConfig(orig, tag) {
  assertLocalOrOptIn('back up and rewrite the connector OSGi config');
  const dir = path.join(__dirname, '..', 'parity', 'runs', 'config-backup');
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const file = path.join(dir, `${tag}-${new URL(AEM_BASE).port || 80}.json`);
  fs.writeFileSync(file, JSON.stringify(orig), { mode: 0o600 });
  fs.chmodSync(file, 0o600); // writeFile's mode only applies when the file is created
  return file;
}

module.exports = { backupConfig, FACTORY, readConfig, writeConfig, setProxy, restoreConfig, same };
