#!/usr/bin/env node
// Regression compare for two gate summaries (pre-qa-gate.sh --json).
// Plain Node, no dependencies. Self-test: node tests/gate/compare.test.js
// Context: docs/testing.md ("Comparing against a baseline release").
//
//   node tests/gate/compare.js [--json <out>] [-v] <baseline.json> <candidate.json>
//   node tests/gate/compare.js [--json <out>] [-v] --baseline <a.json> [b.json ...] --candidate <c.json> [d.json ...]
//
// Several runs per side (the 2+2 rule) separate a regression from a flake: REGRESSION needs the
// test to pass in EVERY baseline run and fail in EVERY candidate run. A test that is mixed
// within a side is FLAKY: reported, never counted as a regression or a fix.
//
// Each test present in either file is classified:
//   REGRESSION      passed in baseline, failed in candidate
//   FIXED           failed in baseline, passed in candidate
//   STILL_FAILING   failed on both sides
//   NEW_TEST        only in candidate, and the baseline DID measure that kind of test
//   REMOVED         only in baseline, and the candidate DID measure that kind of test
//   NOT_MEASURED    skipped or fixme on either side (both reasons shown), or absent from
//                   a side that did not measure that kind at all
//   FLAKY           mixed passed/failed within one side's runs
//   SAME            passed on both sides (counted, listed with -v)
//
// Stages waived by a flag on one side (--baseline waives unit) are not compared at all: those
// tests are summarised as one line "not compared (stage waived: unit): N tests", not as
// per-test NOT_MEASURED rows, so they cannot bury the e2e signal.
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

const STAGE_OF = { unit: 'unit', e2e: 'e2e' };

// A side is a list of runs (gate summaries). A kind is "measured" on a side when some run
// has that stage with a verdict other than NOT_MEASURED.
function measured(runs, kind, platform) {
  return runs.some((r) => (r.stages || []).some((s) => s.platform === platform && s.stage === STAGE_OF[kind] && s.verdict !== 'NOT_MEASURED'));
}
// Waived on a side: every run has the stage NOT_MEASURED by an explicit flag (--baseline
// waives unit, --skip-build waives build). Its tests are not compared at all.
function waived(runs, kind, platform) {
  return runs.length > 0 && runs.every((r) => (r.stages || []).some((s) => s.platform === platform && s.stage === STAGE_OF[kind] && s.verdict === 'NOT_MEASURED' && s.waived));
}

// Per-run outcome list for one side: 'absent' where a run has no such test.
function outcomes(maps, id) { return maps.map((m) => (m.has(id) ? m.get(id).outcome : 'absent')); }

function classify(B, C) {
  const bm = B.map((r) => new Map((r.tests || []).map((t) => [t.id, t])));
  const cm = C.map((r) => new Map((r.tests || []).map((t) => [t.id, t])));
  const ids = Array.from(new Set([...bm, ...cm].flatMap((m) => Array.from(m.keys())))).sort();
  const rows = [];
  for (const id of ids) {
    const any = [...bm, ...cm].map((m) => m.get(id)).find(Boolean);
    const b = outcomes(bm, id); const c = outcomes(cm, id);
    const row = { id, kind: any.kind, platform: any.platform, baseline: b.join('/'), candidate: c.join('/') };
    const reasons = [];
    const note = (label, maps) => maps.forEach((m) => { const t = m.get(id); if (t && t.reason && t.outcome !== 'passed' && !reasons.includes(`${label}: ${t.reason}`)) reasons.push(`${label}: ${t.reason}`); });
    note('baseline', bm); note('candidate', cm);
    if (reasons.length) row.reason = reasons.join(' | ');
    const bAbsent = b.every((x) => x === 'absent'); const cAbsent = c.every((x) => x === 'absent');
    const all = (arr, v) => arr.every((x) => x === v);
    const has = (arr, ...v) => arr.some((x) => v.includes(x));
    if (waived(B, any.kind, any.platform) || waived(C, any.kind, any.platform)) {
      row.class = 'NOT_COMPARED';
      row.waivedKind = any.kind;
      delete row.reason;
    } else if (bAbsent) {
      if (measured(B, any.kind, any.platform)) row.class = 'NEW_TEST';
      else { row.class = 'NOT_MEASURED'; row.reason = `baseline did not measure ${any.kind} on ${any.platform}`; }
    } else if (cAbsent) {
      if (measured(C, any.kind, any.platform)) row.class = 'REMOVED';
      else { row.class = 'NOT_MEASURED'; row.reason = `candidate did not measure ${any.kind} on ${any.platform}`; }
    } else if (has(b, 'absent') || has(c, 'absent')) {
      row.class = 'NOT_MEASURED'; row.reason = 'absent from some runs of one side';
    } else if (has(b, 'skipped', 'fixme') || has(c, 'skipped', 'fixme')) {
      row.class = 'NOT_MEASURED'; // skipped or fixme in some run, or an outcome this tool does not know
    } else if (has(b, 'passed') && has(b, 'failed') || has(c, 'passed') && has(c, 'failed')) {
      row.class = 'FLAKY'; // mixed within a side: not evidence either way
    } else if (all(b, 'passed') && all(c, 'failed')) row.class = 'REGRESSION'; // passes in every baseline run, fails in every candidate run
    else if (all(b, 'failed') && all(c, 'passed')) row.class = 'FIXED';
    else if (all(b, 'failed') && all(c, 'failed')) row.class = 'STILL_FAILING';
    else if (all(b, 'passed') && all(c, 'passed')) row.class = 'SAME';
    else { row.class = 'NOT_MEASURED'; row.reason = 'unrecognised outcomes'; }
    rows.push(row);
  }
  return rows;
}

const firstWith = (runs, key) => { const r = runs.find((x) => x[key] && Object.keys(x[key]).length); return r ? r[key] : null; };

function compareCoverage(B, C) {
  const bc = firstWith(B, 'coverage') || {}; const cc = firstWith(C, 'coverage') || {};
  const out = [];
  const plats = Array.from(new Set([...Object.keys(bc), ...Object.keys(cc), ...B.concat(C).flatMap((r) => r.platforms || [])]));
  for (const p of plats) {
    for (const k of ['line_pct', 'branch_pct']) {
      const x = bc[p] ? bc[p][k] : null;
      const y = cc[p] ? cc[p][k] : null;
      const r = { platform: p, metric: k.replace('_pct', ''), baseline: x, candidate: y };
      if (waived(B, 'unit', p) || waived(C, 'unit', p)) r.verdict = 'NOT_COMPARED';
      else if (x == null || y == null) r.verdict = 'NOT_MEASURED';
      else { r.delta = Math.round((y - x) * 10) / 10; r.verdict = r.delta < 0 ? 'DROPPED' : r.delta > 0 ? 'ROSE' : 'SAME'; }
      out.push(r);
    }
  }
  return out;
}

function comparePins(B, C) {
  const bp = firstWith(B, 'defect_pins'); const cp = firstWith(C, 'defect_pins');
  const out = [];
  for (const k of ['unit_disabled_defect', 'e2e_fixme_total', 'e2e_fixme_defect']) {
    const x = bp && bp[k] != null ? bp[k] : null;
    const y = cp && cp[k] != null ? cp[k] : null;
    const unitWaived = k.startsWith('unit_') && B.concat(C).some((r) => (r.platforms || []).some((p) => waived(B, 'unit', p) || waived(C, 'unit', p)));
    out.push({ pin: k, baseline: x, candidate: y, delta: x == null || y == null || unitWaived ? null : y - x, ...(unitWaived ? { notCompared: true } : {}) });
  }
  return out;
}

// compare(baseline, candidate): each a gate summary or an array of them (several runs of one side).
function compare(b, c) {
  const B = Array.isArray(b) ? b : [b]; const C = Array.isArray(c) ? c : [c];
  const rows = classify(B, C);
  const counts = {};
  for (const r of rows) counts[r.class] = (counts[r.class] || 0) + 1;
  const comparable = rows.filter((r) => ['REGRESSION', 'FIXED', 'STILL_FAILING', 'SAME'].includes(r.class)).length;
  const notCompared = {};
  for (const r of rows) if (r.class === 'NOT_COMPARED') notCompared[r.waivedKind] = (notCompared[r.waivedKind] || 0) + 1;
  let result = 'NO_REGRESSION';
  if (counts.REGRESSION) result = 'REGRESSION';
  else if (!comparable) result = 'NOT_MEASURED';
  return { result, runs: { baseline: B.length, candidate: C.length }, counts, notCompared, comparable, rows, coverage: compareCoverage(B, C), pins: comparePins(B, C) };
}

function print(r, B, C, verbose) {
  const meta = (runs) => runs.map((s) => `${(s.git && s.git.sha) || '?'} ${s.connector_version || '?'} ${s.mode || ''}`.trim()).join(' + ');
  console.log(`baseline:  ${meta(B)} (${B.length} run${B.length > 1 ? 's' : ''})`);
  console.log(`candidate: ${meta(C)} (${C.length} run${C.length > 1 ? 's' : ''})`);
  console.log('');
  const show = r.rows.filter((x) => x.class !== 'NOT_COMPARED' && (verbose || x.class !== 'SAME'));
  if (show.length) {
    const w = Math.max(8, ...show.map((x) => x.baseline.length));
    console.log(`${'CLASS'.padEnd(14)} ${'BASE'.padEnd(w)} ${'CAND'.padEnd(w)} TEST`);
    for (const x of show) {
      console.log(`${x.class.padEnd(14)} ${x.baseline.padEnd(w)} ${x.candidate.padEnd(w)} ${x.id}${x.reason ? `\n${' '.repeat(16 + 2 * w)}${x.reason}` : ''}`);
    }
    console.log('');
  }
  for (const k of Object.keys(r.notCompared).sort()) {
    console.log(`not compared (stage waived: ${k}): ${r.notCompared[k]} tests`);
  }
  const counts = Object.keys(r.counts).filter((k) => k !== 'NOT_COMPARED').sort().map((k) => `${k}=${r.counts[k]}`).join('  ');
  console.log('counts: ' + (counts || 'none'));
  for (const v of r.coverage) {
    const d = v.verdict === 'NOT_COMPARED' ? 'not compared (stage waived: unit)'
      : v.verdict === 'NOT_MEASURED' ? `NOT_MEASURED (baseline ${v.baseline}, candidate ${v.candidate})`
        : `${v.baseline}% -> ${v.candidate}% (${v.delta >= 0 ? '+' : ''}${v.delta}) ${v.verdict}`;
    console.log(`coverage ${v.platform}/${v.metric}: ${d}`);
  }
  for (const p of r.pins) {
    console.log(`pins ${p.pin}: ${p.notCompared ? 'not compared (stage waived: unit)' : p.delta == null ? `NOT_MEASURED (baseline ${p.baseline}, candidate ${p.candidate})` : `${p.baseline} -> ${p.candidate} (${p.delta >= 0 ? '+' : ''}${p.delta})`}`);
  }
  const msg = { REGRESSION: `REGRESSION (${r.counts.REGRESSION} test(s) passed in every baseline run and failed in every candidate run)`,
    NOT_MEASURED: 'NOT_MEASURED (no test was measured on both sides, so "no regression" is unproven)',
    NO_REGRESSION: `NO_REGRESSION (${r.comparable} test(s) compared${r.counts.FLAKY ? `, ${r.counts.FLAKY} flaky and not counted either way` : ''})` }[r.result];
  console.log(`\nRESULT: ${msg}`);
}

function main(argv) {
  let out = ''; let verbose = false; const pos = []; const bf = []; const cf = []; let into = pos;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--json') out = argv[++i];
    else if (a === '-v') verbose = true;
    else if (a === '--baseline') into = bf;
    else if (a === '--candidate') into = cf;
    else into.push(a);
  }
  let bfiles = bf; let cfiles = cf;
  if (!bf.length && !cf.length && pos.length === 2) { bfiles = [pos[0]]; cfiles = [pos[1]]; }
  else if (!bf.length || !cf.length || pos.length) {
    console.error('usage: compare.js [--json <out>] [-v] <baseline.json> <candidate.json>\n       compare.js [--json <out>] [-v] --baseline <a.json> [b.json ...] --candidate <c.json> [d.json ...]');
    return 2;
  }
  let B; let C;
  try {
    B = bfiles.map((f) => JSON.parse(fs.readFileSync(f, 'utf8')));
    C = cfiles.map((f) => JSON.parse(fs.readFileSync(f, 'utf8')));
  } catch (e) { console.error(`cannot read input: ${e.message}`); return 2; }
  const r = compare(B, C);
  print(r, B, C, verbose);
  if (out) fs.writeFileSync(out, JSON.stringify(r, null, 2) + '\n');
  return r.result === 'REGRESSION' ? 1 : r.result === 'NOT_MEASURED' ? 3 : 0;
}

if (require.main === module) process.exit(main(process.argv.slice(2)));
module.exports = { compare, classify };
