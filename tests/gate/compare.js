#!/usr/bin/env node
// Regression compare for two gate summaries (pre-qa-gate.sh --json).
// Plain Node, no dependencies. Self-test: node tests/gate/compare.test.js
// Context: docs/testing.md ("Comparing against a baseline release").
//
//   node tests/gate/compare.js [--json <out>] [-v] <baseline.json> <candidate.json>
//
// Each test present in either file is classified:
//   REGRESSION      passed in baseline, failed in candidate
//   FIXED           failed in baseline, passed in candidate
//   STILL_FAILING   failed on both sides
//   NEW_TEST        only in candidate, and the baseline DID measure that kind of test
//   REMOVED         only in baseline, and the candidate DID measure that kind of test
//   NOT_MEASURED    skipped or fixme on either side (both reasons shown), or absent from
//                   a side that did not measure that kind at all
//   SAME            passed on both sides (counted, listed with -v)
//
// Absent is not pass and not fail: a test missing from the baseline build (feature
// absent) is NEW_TEST or NOT_MEASURED, never a pass or a regression. A baseline taken
// without unit or e2e measuring makes every such test NOT_MEASURED, not NEW_TEST.
//
// Result and exit code:
//   REGRESSION      exit 1   at least one REGRESSION
//   NOT_MEASURED    exit 3   nothing was measured on both sides, so "no regression" is unproven
//   NO_REGRESSION   exit 0
//   usage / unreadable input: exit 2
'use strict';
const fs = require('fs');

function measured(summary, kind, platform) {
  const stage = kind === 'unit' ? 'unit' : 'e2e';
  const st = (summary.stages || []).find((s) => s.platform === platform && s.stage === stage);
  return !!st && st.verdict !== 'NOT_MEASURED';
}

function classify(b, c) {
  const bt = new Map((b.tests || []).map((t) => [t.id, t]));
  const ct = new Map((c.tests || []).map((t) => [t.id, t]));
  const ids = Array.from(new Set([...bt.keys(), ...ct.keys()])).sort();
  const rows = [];
  for (const id of ids) {
    const x = bt.get(id); const y = ct.get(id);
    const any = x || y;
    const row = { id, kind: any.kind, platform: any.platform, baseline: x ? x.outcome : 'absent', candidate: y ? y.outcome : 'absent' };
    const reasons = [];
    if (x && x.reason && x.outcome !== 'passed') reasons.push(`baseline: ${x.reason}`);
    if (y && y.reason && y.outcome !== 'passed') reasons.push(`candidate: ${y.reason}`);
    if (reasons.length) row.reason = reasons.join(' | ');
    if (x && y) {
      const bo = x.outcome; const co = y.outcome;
      if (bo === 'passed' && co === 'failed') row.class = 'REGRESSION';
      else if (bo === 'failed' && co === 'passed') row.class = 'FIXED';
      else if (bo === 'failed' && co === 'failed') row.class = 'STILL_FAILING';
      else if (bo === 'passed' && co === 'passed') row.class = 'SAME';
      else row.class = 'NOT_MEASURED'; // skipped or fixme on a side, or an outcome this tool does not know
    } else if (y) {
      if (measured(b, y.kind, y.platform)) row.class = 'NEW_TEST';
      else { row.class = 'NOT_MEASURED'; row.reason = `baseline did not measure ${y.kind} on ${y.platform}`; }
    } else {
      if (measured(c, x.kind, x.platform)) row.class = 'REMOVED';
      else { row.class = 'NOT_MEASURED'; row.reason = `candidate did not measure ${x.kind} on ${x.platform}`; }
    }
    rows.push(row);
  }
  return rows;
}

function compareCoverage(b, c) {
  const out = [];
  const plats = Array.from(new Set([...Object.keys(b.coverage || {}), ...Object.keys(c.coverage || {})]));
  for (const p of plats) {
    for (const k of ['line_pct', 'branch_pct']) {
      const x = b.coverage && b.coverage[p] ? b.coverage[p][k] : null;
      const y = c.coverage && c.coverage[p] ? c.coverage[p][k] : null;
      const r = { platform: p, metric: k.replace('_pct', ''), baseline: x, candidate: y };
      if (x == null || y == null) r.verdict = 'NOT_MEASURED';
      else { r.delta = Math.round((y - x) * 10) / 10; r.verdict = r.delta < 0 ? 'DROPPED' : r.delta > 0 ? 'ROSE' : 'SAME'; }
      out.push(r);
    }
  }
  return out;
}

function comparePins(b, c) {
  const out = [];
  for (const k of ['unit_disabled_defect', 'e2e_fixme_total', 'e2e_fixme_defect']) {
    const x = b.defect_pins ? b.defect_pins[k] : null;
    const y = c.defect_pins ? c.defect_pins[k] : null;
    out.push({ pin: k, baseline: x == null ? null : x, candidate: y == null ? null : y, delta: x == null || y == null ? null : y - x });
  }
  return out;
}

function compare(b, c) {
  const rows = classify(b, c);
  const counts = {};
  for (const r of rows) counts[r.class] = (counts[r.class] || 0) + 1;
  const comparable = rows.filter((r) => ['REGRESSION', 'FIXED', 'STILL_FAILING', 'SAME'].includes(r.class)).length;
  let result = 'NO_REGRESSION';
  if (counts.REGRESSION) result = 'REGRESSION';
  else if (!comparable) result = 'NOT_MEASURED';
  return { result, counts, comparable, rows, coverage: compareCoverage(b, c), pins: comparePins(b, c) };
}

function print(r, b, c, verbose) {
  const meta = (s) => `${(s.git && s.git.sha) || '?'} ${s.connector_version || '?'} ${s.mode || ''}`.trim();
  console.log(`baseline:  ${meta(b)}`);
  console.log(`candidate: ${meta(c)}`);
  console.log('');
  const show = r.rows.filter((x) => verbose || x.class !== 'SAME');
  if (show.length) {
    console.log(`${'CLASS'.padEnd(14)} ${'BASE'.padEnd(8)} ${'CAND'.padEnd(8)} TEST`);
    for (const x of show) {
      console.log(`${x.class.padEnd(14)} ${x.baseline.padEnd(8)} ${x.candidate.padEnd(8)} ${x.id}${x.reason ? `\n${' '.repeat(32)}${x.reason}` : ''}`);
    }
    console.log('');
  }
  console.log('counts: ' + (Object.keys(r.counts).sort().map((k) => `${k}=${r.counts[k]}`).join('  ') || 'none'));
  for (const v of r.coverage) {
    const d = v.verdict === 'NOT_MEASURED' ? `NOT_MEASURED (baseline ${v.baseline}, candidate ${v.candidate})` : `${v.baseline}% -> ${v.candidate}% (${v.delta >= 0 ? '+' : ''}${v.delta}) ${v.verdict}`;
    console.log(`coverage ${v.platform}/${v.metric}: ${d}`);
  }
  for (const p of r.pins) {
    console.log(`pins ${p.pin}: ${p.delta == null ? `NOT_MEASURED (baseline ${p.baseline}, candidate ${p.candidate})` : `${p.baseline} -> ${p.candidate} (${p.delta >= 0 ? '+' : ''}${p.delta})`}`);
  }
  const msg = { REGRESSION: `REGRESSION (${r.counts.REGRESSION} test(s) passed on baseline and fail on candidate)`,
    NOT_MEASURED: 'NOT_MEASURED (no test was measured on both sides, so "no regression" is unproven)',
    NO_REGRESSION: `NO_REGRESSION (${r.comparable} test(s) compared)` }[r.result];
  console.log(`\nRESULT: ${msg}`);
}

function main(argv) {
  let out = ''; let verbose = false; const files = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--json') out = argv[++i];
    else if (argv[i] === '-v') verbose = true;
    else files.push(argv[i]);
  }
  if (files.length !== 2) { console.error('usage: compare.js [--json <out>] [-v] <baseline.json> <candidate.json>'); return 2; }
  let b; let c;
  try { b = JSON.parse(fs.readFileSync(files[0], 'utf8')); c = JSON.parse(fs.readFileSync(files[1], 'utf8')); } catch (e) {
    console.error(`cannot read input: ${e.message}`); return 2;
  }
  const r = compare(b, c);
  print(r, b, c, verbose);
  if (out) fs.writeFileSync(out, JSON.stringify(r, null, 2) + '\n');
  return r.result === 'REGRESSION' ? 1 : r.result === 'NOT_MEASURED' ? 3 : 0;
}

if (require.main === module) process.exit(main(process.argv.slice(2)));
module.exports = { compare, classify };
