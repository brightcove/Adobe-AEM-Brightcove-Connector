#!/usr/bin/env node
// Self-test for compare.js against the fixtures in tests/gate/fixtures. Plain Node.
//   node tests/gate/compare.test.js
// baseline.json / candidate.json: one test of every class. thin.json: nothing measured
// (stages NOT_MEASURED, one skipped test, no coverage or pins), the "absent is not false" case.
'use strict';
const assert = require('assert');
const path = require('path');
const { spawnSync } = require('child_process');
const { compare } = require('./compare');

const fx = (n) => require(path.join(__dirname, 'fixtures', `${n}.json`));
const cls = (r) => Object.fromEntries(r.rows.map((x) => [x.id.split(' > ').pop().replace(/^unit:cloud:.*#/, 'A#'), x.class]));
let n = 0;
const t = (name, fn) => { fn(); n++; console.log(`ok - ${name}`); };

t('every class is produced and a regression is reported', () => {
  const r = compare(fx('baseline'), fx('candidate'));
  assert.strictEqual(r.result, 'REGRESSION');
  assert.deepStrictEqual(cls(r), {
    'A#same': 'SAME', 'goes red': 'REGRESSION', 'was broken, now fixed': 'FIXED', 'broken both': 'STILL_FAILING',
    'feature absent on baseline': 'NOT_MEASURED', pinned: 'NOT_MEASURED', 'deleted later': 'REMOVED', 'brand new': 'NEW_TEST',
  });
});

t('a skipped baseline test that now passes is NOT_MEASURED, with the skip reason', () => {
  const r = compare(fx('baseline'), fx('candidate'));
  const row = r.rows.find((x) => x.id.endsWith('feature absent on baseline'));
  assert.strictEqual(row.class, 'NOT_MEASURED');
  assert.ok(/no such panel/.test(row.reason), row.reason);
});

t('coverage drop and pin changes are reported', () => {
  const r = compare(fx('baseline'), fx('candidate'));
  assert.strictEqual(r.coverage.find((v) => v.metric === 'branch').verdict, 'DROPPED');
  assert.strictEqual(r.coverage.find((v) => v.metric === 'line').verdict, 'ROSE');
  assert.strictEqual(r.pins.find((p) => p.pin === 'e2e_fixme_defect').delta, 2);
});

t('a summary compared with itself has zero regressions', () => {
  const r = compare(fx('candidate'), fx('candidate'));
  assert.notStrictEqual(r.result, 'REGRESSION');
  assert.strictEqual(r.counts.REGRESSION || 0, 0);
});

t('thin data (nothing measured) is NOT_MEASURED, never PASS or NO_REGRESSION', () => {
  const r = compare(fx('baseline'), fx('thin'));
  assert.strictEqual(r.result, 'NOT_MEASURED');
  assert.strictEqual(r.comparable, 0);
  assert.ok(r.rows.every((x) => x.class === 'NOT_MEASURED'), JSON.stringify(cls(r)));
  assert.ok(r.coverage.every((v) => v.verdict === 'NOT_MEASURED'));
  assert.ok(r.pins.every((p) => p.delta === null));
  const r2 = compare(fx('thin'), fx('candidate'));
  assert.strictEqual(r2.result, 'NOT_MEASURED');
  assert.ok(!r2.rows.some((x) => x.class === 'NEW_TEST'), 'tests absent from an unmeasured baseline are not NEW_TEST');
});

t('CLI exit codes: 1 regression, 0 clean, 3 not measured, 2 bad input', () => {
  const run = (...a) => spawnSync(process.execPath, [path.join(__dirname, 'compare.js'), ...a.map((x) => (x.endsWith('.json') ? path.join(__dirname, 'fixtures', x) : x))], { encoding: 'utf8' });
  assert.strictEqual(run('baseline.json', 'candidate.json').status, 1);
  assert.strictEqual(run('candidate.json', 'candidate.json').status, 0);
  assert.strictEqual(run('baseline.json', 'thin.json').status, 3);
  assert.strictEqual(run('baseline.json', 'missing.json').status, 2);
  assert.strictEqual(run('baseline.json').status, 2);
});

const multi = () => compare(['multi-b1', 'multi-b2'].map(fx), ['multi-c1', 'multi-c2'].map(fx));
const mcls = (r) => Object.fromEntries(r.rows.filter((x) => x.kind === 'e2e').map((x) => [x.id.split(' > ').pop(), x.class]));

t('2+2 runs: REGRESSION needs every baseline run passing and every candidate run failing', () => {
  assert.deepStrictEqual(mcls(multi()), {
    'goes red': 'REGRESSION', 'flaky candidate': 'FLAKY', 'flaky baseline': 'FLAKY',
    'one bad run is not a regression': 'FLAKY', fixed: 'FIXED', 'still failing': 'STILL_FAILING', same: 'SAME',
  });
  assert.strictEqual(multi().result, 'REGRESSION');
});

t('a unit stage waived on the baseline is one "not compared" count, not per-test NOT_MEASURED rows', () => {
  const r = multi();
  assert.strictEqual(r.notCompared.unit, 1);
  assert.ok(!r.rows.some((x) => x.kind === 'unit' && x.class === 'NOT_MEASURED'));
  assert.ok(r.coverage.every((v) => v.verdict === 'NOT_COMPARED'));
  const out = spawnSync(process.execPath, [path.join(__dirname, 'compare.js'), '--baseline', ...['multi-b1', 'multi-b2'].map((n) => path.join(__dirname, 'fixtures', `${n}.json`)), '--candidate', ...['multi-c1', 'multi-c2'].map((n) => path.join(__dirname, 'fixtures', `${n}.json`))], { encoding: 'utf8' });
  assert.strictEqual(out.status, 1);
  assert.ok(/not compared \(stage waived: unit\): 1 tests/.test(out.stdout), out.stdout);
});

t('flaky tests alone never produce a regression', () => {
  const r = compare(['multi-b1', 'multi-b2'].map(fx), ['multi-b1', 'multi-b2'].map(fx));
  assert.notStrictEqual(r.result, 'REGRESSION');
  assert.strictEqual(r.counts.FLAKY, 1);
});

console.log(`\n${n} passed`);
