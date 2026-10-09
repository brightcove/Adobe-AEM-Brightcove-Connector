// Shared safety guards for the parity probes that MUTATE a Video Cloud account through the
// connector (label / playlist / folder probes). Plain Node, no Playwright dependency.
//
//   (a) assertLocalTarget   refuse a non-loopback AEM unless `--allow-remote` is on the command line
//   (b) assertMutable       refuse to mutate any video / playlist / folder whose name does not
//                           start with the e2e throwaway prefix, unless THIS run created it
//   (c) finish              PASS / FAIL / NOT MEASURED verdict and a non-zero exit for anything but PASS
//
// Context: ../../README.md (Probes). Self-test: node guards.test.js
const { isLocalHost } = require('../../../e2e/target');

// Same prefix the e2e suite uses (e2e/dam.js, e2e/cms.js): its cleanup only ever deletes these.
const THROWAWAY_PREFIX = 'e2e-throwaway-';
const ALLOW_REMOTE_FLAG = '--allow-remote';

class GuardError extends Error {}

// Split argv (process.argv.slice(2)) into positional args and the recognised flags.
function cli(argv = process.argv.slice(2)) {
  return { positional: argv.filter((a) => !a.startsWith('--')), allowRemote: argv.includes(ALLOW_REMOTE_FLAG) };
}

function assertLocalTarget(url, { allowRemote = false } = {}) {
  if (isLocalHost(url)) return;
  if (allowRemote) { console.log(`WARNING: ${ALLOW_REMOTE_FLAG} given, mutating through non-local AEM ${new URL(url).host}`); return; }
  let host = url;
  try { host = new URL(url).host; } catch (e) { /* report the raw value */ }
  throw new GuardError(`refusing to run a mutating probe against non-local AEM ${host}. Use a localhost AEM, or pass ${ALLOW_REMOTE_FLAG} on purpose.`);
}

const createdInRun = new Set();
// Record an object (video / playlist / folder id) that this run itself created.
function markCreated(id) { createdInRun.add(String(id)); }

function isThrowawayName(name) { return typeof name === 'string' && name.startsWith(THROWAWAY_PREFIX); }

// Throws unless the object is throwaway-named or was created by this run. `obj` = { id, name }.
// An absent name is a refusal, not a pass: absent is not "safe".
function assertMutable(kind, obj) {
  const { id, name } = obj || {};
  if (id !== undefined && createdInRun.has(String(id))) return;
  if (isThrowawayName(name)) return;
  throw new GuardError(`refusing to mutate ${kind} ${id === undefined ? '' : id + ' '}named ${name === undefined || name === null ? '(name unknown)' : JSON.stringify(name)}: only objects named ${THROWAWAY_PREFIX}* (or created by this run) may be mutated. Create a throwaway ${kind} first.`);
}

// checks: [{ name, ok }] with ok === true (pass), false (fail) or null/undefined (not measured).
// Absent is not false: a check that could not run reports NOT MEASURED, never FAIL or PASS.
function verdictOf(checks) {
  if (!checks || !checks.length) return 'NOT MEASURED';
  if (checks.some((c) => c.ok === false)) return 'FAIL';
  if (checks.some((c) => c.ok === null || c.ok === undefined)) return 'NOT MEASURED';
  return 'PASS';
}

function finish(checks, { exit = true } = {}) {
  for (const c of checks || []) console.log(`${(c.ok === true ? 'PASS' : c.ok === false ? 'FAIL' : 'NOT MEASURED').padEnd(12)} ${c.name}`);
  const v = verdictOf(checks);
  console.log(`VERDICT: ${v}`);
  process.exitCode = v === 'PASS' ? 0 : (v === 'FAIL' ? 1 : 2);
  if (exit) process.exit(process.exitCode); // explicit: a lingering browser/handle must not hang the run
  return v;
}

// Turn a guard refusal / unexpected error into a NOT MEASURED or FAIL verdict plus exit code.
function fatal(err) {
  if (err instanceof GuardError) { console.error(`GUARD: ${err.message}`); console.log('VERDICT: NOT MEASURED (guard refused; nothing was mutated)'); process.exit(2); }
  console.error(err && err.stack || err); console.log('VERDICT: FAIL (probe error)'); process.exit(1);
}

module.exports = { THROWAWAY_PREFIX, ALLOW_REMOTE_FLAG, GuardError, cli, assertLocalTarget, assertMutable, markCreated, isThrowawayName, verdictOf, finish, fatal };
