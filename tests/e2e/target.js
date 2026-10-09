// Per-target isolation for the e2e harness.
//
// ⚠️ Cookies on `localhost` ignore the port, and Playwright's storageState and
// test-results paths were previously fixed, so two concurrent runs against
// different AEM instances (e.g. cloud :4502 and on-prem :4602) overwrote each
// other's login state mid-run and one run ended up driving the other's
// instance. Measured 2026-09-17 during the parity baselines. Everything
// derived from the target now lives here so config and global-setup agree.
// Context: ../parity/README.md (parity runs) and ONPREM-PARITY-PLAN.md §3 Phase 1.
const path = require('path');

const AEM_BASE = process.env.AEM_BASE || 'http://localhost:4502';
const AEM_USER = process.env.AEM_USER || 'admin';
const AEM_PASS = process.env.AEM_PASS || 'admin';

const u = new URL(AEM_BASE);
// e.g. "localhost-4502"; safe as a path segment.
const TARGET_KEY = `${u.hostname}-${u.port || (u.protocol === 'https:' ? '443' : '80')}`.replace(/[^A-Za-z0-9.-]/g, '_');

const STATE_PATH = path.join(__dirname, '.auth', `state-${TARGET_KEY}.json`);
const OUTPUT_DIR = path.join(__dirname, 'test-results', TARGET_KEY);

// True only for a loopback AEM. Anything else (a shared QA or customer instance) is remote.
// The ONE implementation: the parity probe guards (parity/probes/lib/guards.js) reuse it.
function isLocalHost(url) {
  let h;
  try { h = new URL(url).hostname.toLowerCase(); } catch (e) { return false; }
  return h === 'localhost' || h === '127.0.0.1' || h === '[::1]' || h === '::1';
}

// Gate for harness code that WRITES to the AEM under test (OSGi config, etc.): refuse a
// non-loopback AEM_BASE unless E2E_ALLOW_REMOTE=1 is set explicitly. Throws; never returns false.
function assertLocalOrOptIn(what, url = AEM_BASE) {
  if (isLocalHost(url) || process.env.E2E_ALLOW_REMOTE === '1') return;
  throw new Error(`refusing to ${what} on non-local AEM ${new URL(url).host}: the harness only writes to localhost. Set E2E_ALLOW_REMOTE=1 to override on purpose.`);
}

module.exports = { AEM_BASE, AEM_USER, AEM_PASS, TARGET_KEY, STATE_PATH, OUTPUT_DIR, isLocalHost, assertLocalOrOptIn };
