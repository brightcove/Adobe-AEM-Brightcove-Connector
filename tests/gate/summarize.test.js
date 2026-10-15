#!/usr/bin/env node
// Self-test for the e2e_live verdict in summarize.js. Plain Node, no AEM:
//   node tests/gate/summarize.test.js
// Builds a work dir with a synthetic Playwright JSON report and checks the verdict is derived from
// live-annotated test results, not from the credentials being set.
'use strict';
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const spec = (title, status, live, extra = []) => ({ title, file: 'x.spec.js', line: 1, tests: [{ status, annotations: (live ? [{ type: 'live' }] : []).concat(extra), results: [{ error: { message: 'boom' } }] }] });
function liveVerdict(specs) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sum-'));
  const report = path.join(dir, 'report.json');
  fs.writeFileSync(report, JSON.stringify({ suites: [{ title: 'x.spec.js', file: 'x.spec.js', specs }] }));
  fs.writeFileSync(path.join(dir, 'stages.tsv'), 'cloud\te2e\tPENDING\texit=0\ncloud\te2e_live\tPENDING\tcredentials present\n');
  fs.writeFileSync(path.join(dir, 'e2e.tsv'), `cloud\t${report}\n`);
  const out = path.join(dir, 'out.json');
  spawnSync(process.execPath, [path.join(__dirname, 'summarize.js'), '--work', dir, '--out', out, '--mode', 'full', '--platforms', 'cloud'], { encoding: 'utf8' });
  const st = JSON.parse(fs.readFileSync(out, 'utf8')).stages.find((s) => s.stage === 'e2e_live');
  fs.rmSync(dir, { recursive: true, force: true });
  return st;
}
const skip = [{ type: 'skip', description: 'NOT MEASURED: AEM_PUBLISH_URL not set' }];
let n = 0;
const t = (name, fn) => { fn(); n++; console.log(`ok - ${name}`); };

t('a live test passed: PASS', () => assert.strictEqual(liveVerdict([spec('a', 'expected', true), spec('b', 'skipped', true, skip)]).verdict, 'PASS'));
t('a live test failed: FAIL', () => assert.strictEqual(liveVerdict([spec('a', 'expected', true), spec('b', 'unexpected', true)]).verdict, 'FAIL'));
t('credentials set but every live test skipped: NOT_MEASURED with the skip reason', () => {
  const st = liveVerdict([spec('a', 'skipped', true, skip), spec('plain', 'expected', false)]);
  assert.strictEqual(st.verdict, 'NOT_MEASURED');
  assert.ok(/AEM_PUBLISH_URL/.test(st.reason), st.reason);
});
t('only non-live tests passed: NOT_MEASURED, never PASS', () => assert.strictEqual(liveVerdict([spec('plain', 'expected', false)]).verdict, 'NOT_MEASURED'));
console.log(`\n${n} passed`);
