#!/usr/bin/env node
// Builds the gate's machine-readable summary from artifacts the gate leaves in a work
// dir, and prints the verdict table. Plain Node, no dependencies.
// Called by tests/e2e/pre-qa-gate.sh (also on failure, so a FAIL still produces JSON).
// Context: docs/testing.md (JSON schema, verdict states).
//
//   node summarize.js --work <dir> --out <file> --mode full|unit-only --platforms "cloud onprem"
//        [--java21 1] [--allow-no-live 1] [--live-missing "VAR VAR"] [--sha S] [--branch B]
//        [--version V] [--e2e-root <tests/e2e dir>] [--lock-wait <secs>] [--lock-beds "url url"]
//
// Work dir layout (written by the gate):
//   stages.tsv            platform <TAB> stage <TAB> verdict <TAB> reason [<TAB> waived]
//                         verdict PENDING (reason "exit=N") is resolved here for unit,
//                         unit_java21 and e2e from the artifacts below.
//   aem.tsv               platform <TAB> url <TAB> bundle_version <TAB> bundle_state
//   unit-<p>/             TEST-*.xml + jacoco.xml copied from core/target
//   unit-<p>-java21/      the same for the forked Java 21 run
//   e2e.tsv               platform <TAB> path of the Playwright JSON report
//
// Exit code: 0 only for PASS, or NOT_MEASURED where every unmeasured stage is waived by
// an explicit flag (--allow-no-live, --skip-build). 1 otherwise.
'use strict';
const fs = require('fs');
const path = require('path');

const args = {};
for (let i = 2; i < process.argv.length; i += 2) args[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
const work = args.work;
const platforms = (args.platforms || '').split(/\s+/).filter(Boolean);
const mode = args.mode || 'full';
const java21 = args.java21 === '1';
const allowNoLive = args['allow-no-live'] === '1';
const liveMissing = (args['live-missing'] || '').trim();
const e2eRoot = args['e2e-root'] || '';
const lockWait = args['lock-wait'] === undefined || args['lock-wait'] === '' ? null : Number(args['lock-wait']);
const lockBeds = (args['lock-beds'] || '').split(/\s+/).filter(Boolean);

function readTsv(name) {
  const f = path.join(work, name);
  if (!fs.existsSync(f)) return [];
  return fs.readFileSync(f, 'utf8').split('\n').filter(Boolean).map((l) => l.split('\t'));
}
const unesc = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'")
  .replace(/&#(\d+);/g, (m, d) => String.fromCharCode(+d)).replace(/&amp;/g, '&');

// ---- unit: surefire XML + JaCoCo ----
function parseUnit(dir, platform) {
  const out = { files: 0, tests: [], jvm: null, coverage: null };
  if (!fs.existsSync(dir)) return out;
  const seen = {};
  for (const f of fs.readdirSync(dir).filter((n) => /^TEST-.*\.xml$/.test(n)).sort()) {
    out.files++;
    const xml = fs.readFileSync(path.join(dir, f), 'utf8');
    const jv = xml.match(/name="java\.version" value="([^"]*)"/);
    if (jv && !out.jvm) out.jvm = jv[1];
    const re = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
    let m;
    while ((m = re.exec(xml))) {
      const at = {};
      m[1].replace(/(\w+)="([^"]*)"/g, (x, k, v) => { at[k] = unesc(v); return x; });
      const body = m[2] || '';
      let outcome = 'passed'; let reason = '';
      const sk = body.match(/<skipped\b([^>]*?)(?:\/>|>)/);
      const fl = body.match(/<(failure|error)\b([^>]*?)(?:\/>|>)/);
      if (fl) {
        outcome = 'failed';
        const msg = fl[2].match(/message="([^"]*)"/);
        reason = unesc(msg ? msg[1] : fl[1]).slice(0, 300);
      } else if (sk) {
        outcome = 'skipped';
        const msg = sk[1].match(/message="([^"]*)"/);
        reason = msg ? unesc(msg[1]) : '';
      }
      let id = `unit:${platform}:${at.classname}#${at.name}`;
      seen[id] = (seen[id] || 0) + 1;
      if (seen[id] > 1) id += `#${seen[id]}`;
      const t = { id, kind: 'unit', platform, outcome };
      if (reason) t.reason = reason;
      if (outcome === 'skipped' && /^DEFECT\b/.test(reason)) t.defect = true;
      out.tests.push(t);
    }
  }
  const jf = path.join(dir, 'jacoco.xml');
  if (fs.existsSync(jf)) {
    const xml = fs.readFileSync(jf, 'utf8');
    const tail = xml.slice(xml.lastIndexOf('</package>') + 10); // report-level totals follow the last package
    const cov = {};
    tail.replace(/<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\/>/g, (x, ty, mi, co) => {
      const tot = +mi + +co;
      cov[ty.toLowerCase()] = { covered: +co, missed: +mi, pct: tot ? Math.round((1000 * +co) / tot) / 10 : null };
      return x;
    });
    if (cov.line && cov.branch) out.coverage = { line_pct: cov.line.pct, branch_pct: cov.branch.pct, counters: cov };
  }
  return out;
}

function counts(tests) {
  const c = { run: tests.length, passed: 0, failed: 0, skipped: 0, fixme: 0 };
  for (const t of tests) c[t.outcome]++;
  return c;
}

// ---- e2e: Playwright JSON reporter output (never re-run to gather) ----
function parseE2e(file, platform) {
  const rep = JSON.parse(fs.readFileSync(file, 'utf8'));
  const tests = []; const seen = {};
  const src = {};
  const lines = (f) => (src[f] = src[f] || (e2eRoot && fs.existsSync(path.join(e2eRoot, 'specs', f))
    ? fs.readFileSync(path.join(e2eRoot, 'specs', f), 'utf8').split('\n') : []));
  const walk = (suite, chain, isRoot) => {
    const here = isRoot || !suite.title || suite.file === suite.title ? chain : chain.concat(suite.title);
    for (const spec of suite.specs || []) {
      for (const t of spec.tests || []) {
        const ann = t.annotations || [];
        const fixme = ann.find((a) => a.type === 'fixme');
        const skip = ann.find((a) => a.type === 'skip');
        let outcome; let reason = '';
        if (t.status === 'expected') outcome = 'passed';
        else if (t.status === 'flaky') outcome = 'passed';
        else if (t.status === 'unexpected') outcome = 'failed';
        else outcome = fixme ? 'fixme' : 'skipped';
        if (outcome === 'skipped' || outcome === 'fixme') reason = ((fixme || skip || {}).description) || '';
        if (outcome === 'failed') {
          const r = (t.results || []).slice(-1)[0] || {};
          reason = String((r.error && r.error.message) || '').split('\n')[0].replace(/\u001b\[[0-9;]*m/g, '').slice(0, 300);
        }
        const title = here.concat(spec.title).join(' > ');
        let id = `e2e:${platform}:${spec.file} > ${title}`;
        seen[id] = (seen[id] || 0) + 1;
        if (seen[id] > 1) id += ` #${seen[id]}`;
        const o = { id, kind: 'e2e', platform, outcome, file: spec.file, title };
        if (reason) o.reason = reason;
        if (t.status === 'flaky') o.flaky = true;
        if (ann.some((a) => a.type === 'live')) o.live = true; // markLive() in tests/e2e/fixtures.js
        if (outcome === 'fixme') {
          // The label is the contiguous // comment block directly above test.fixme, or the same line.
          const L = lines(spec.file); const ln = (spec.line || 1) - 1;
          const block = [L[ln] || ''];
          for (let k = ln - 1; k >= 0 && /^\s*\/\//.test(L[k]); k--) block.push(L[k]);
          if (/\/\/\s*DEFECT:/.test(block.join('\n'))) o.defect = true;
          else if (/\/\/\s*LIMITATION:/.test(block.join('\n'))) o.limitation = true;
        }
        tests.push(o);
      }
    }
    for (const s of suite.suites || []) walk(s, here, false);
  };
  for (const s of rep.suites || []) walk(s, [], true);
  return tests;
}

// ---- assemble ----
const stageRows = readTsv('stages.tsv');
const stages = [];
const allTests = []; const unitSummary = {}; const e2eSummary = {}; const coverage = {};
const e2eReports = {}; for (const r of readTsv('e2e.tsv')) e2eReports[r[0]] = r[1];

const exitOf = (reason) => { const m = /exit=(\d+)/.exec(reason || ''); return m ? +m[1] : null; };

for (const p of platforms) {
  const unit = parseUnit(path.join(work, `unit-${p}`), p);
  allTests.push(...unit.tests);
  const uc = counts(unit.tests);
  unitSummary[p] = { files: unit.files, ...uc, errors_counted_as_failed: true, jvm: unit.jvm };
  if (unit.coverage) coverage[p] = unit.coverage;
  if (e2eReports[p] && fs.existsSync(e2eReports[p])) {
    const et = parseE2e(e2eReports[p], p);
    allTests.push(...et);
    e2eSummary[p] = { ...counts(et), report: path.basename(e2eReports[p]) };
  }
}

function resolve(row) {
  const [platform, stage, verdict, reason, waived] = row;
  const st = { platform, stage, verdict, reason: reason || '' };
  if (waived === 'waived') st.waived = true;
  if (verdict !== 'PENDING') return st;
  const code = exitOf(reason);
  if (stage === 'unit' || stage === 'unit_java21') {
    const dir = path.join(work, stage === 'unit' ? `unit-${platform}` : `unit-${platform}-java21`);
    const u = parseUnit(dir, platform); const c = counts(u.tests);
    const jvmNote = ` on JVM ${u.jvm || '?'}`;
    if (!u.files) { st.verdict = code ? 'FAIL' : 'NOT_MEASURED'; st.reason = code ? `mvn exited ${code} before any test ran (compile or setup failure)` : 'no surefire XML found'; }
    else if (c.failed) { st.verdict = 'FAIL'; st.reason = `${c.failed} of ${c.run} failed${jvmNote}`; }
    else if (code) { st.verdict = 'FAIL'; st.reason = `mvn exited ${code} with no failing test (build or plugin error)`; }
    else if (stage === 'unit_java21' && !/^21\./.test(u.jvm || '')) { st.verdict = 'FAIL'; st.reason = `tests ran on JVM ${u.jvm || 'unknown'}, not Java 21`; }
    else if (!c.passed) { st.verdict = 'NOT_MEASURED'; st.reason = 'no test passed (all skipped or none ran)'; }
    else { st.verdict = 'PASS'; st.reason = `${c.passed} passed, ${c.skipped} skipped${jvmNote}`; }
  } else if (stage === 'e2e_live') {
    // Derived from the Playwright results, never from the credentials merely being set: those
    // make the live specs ELIGIBLE to run, not proof that one did (a missing publish tier, an
    // unreachable media host or a failed fixture still skip them).
    const live = allTests.filter((t) => t.kind === 'e2e' && t.platform === platform && t.live);
    const n = (o) => live.filter((t) => t.outcome === o).length;
    if (!e2eSummary[platform]) { st.verdict = 'NOT_MEASURED'; st.reason = 'no Playwright JSON report, so no live spec result'; }
    else if (!live.length) { st.verdict = 'NOT_MEASURED'; st.reason = 'no credential-dependent (live) spec is in the report'; }
    else if (n('failed')) { st.verdict = 'FAIL'; st.reason = `${n('failed')} of ${live.length} live tests failed`; }
    else if (n('passed')) { st.verdict = 'PASS'; st.reason = `${n('passed')} live tests passed, ${n('skipped')} skipped, ${n('fixme')} fixme`; }
    else {
      const why = (live.find((t) => t.reason) || {}).reason || 'all skipped';
      st.verdict = 'NOT_MEASURED'; st.reason = `no live test passed (${live.length} skipped or fixme): ${why}`.slice(0, 300);
    }
  } else if (stage === 'e2e') {
    const rf = e2eReports[platform];
    const es = e2eSummary[platform];
    if (!rf || !fs.existsSync(rf) || !es) { st.verdict = code ? 'FAIL' : 'NOT_MEASURED'; st.reason = code ? `playwright exited ${code} and left no readable report` : 'no Playwright JSON report found'; }
    else if (es.failed) { st.verdict = 'FAIL'; st.reason = `${es.failed} of ${es.run} failed`; }
    else if (code) { st.verdict = 'FAIL'; st.reason = `playwright exited ${code} with no failing test`; }
    else if (!es.passed) { st.verdict = 'NOT_MEASURED'; st.reason = 'no spec passed (all skipped or none ran)'; }
    else { st.verdict = 'PASS'; st.reason = `${es.passed} passed, ${es.skipped} skipped, ${es.fixme} fixme`; }
  }
  return st;
}
for (const r of stageRows) stages.push(resolve(r));

// Stages that should exist but were never recorded: not run, never silently green.
const expected = mode === 'unit-only'
  ? ['unit'].concat(java21 ? ['unit_java21'] : [])
  : ['unit'].concat(java21 ? ['unit_java21'] : [], ['bed_lock', 'version_bump', 'build', 'deployed_bundle', 'failed_components', 'clientlib', 'proxy_config', 'e2e', 'e2e_live']);
const anyFail = stages.some((s) => s.verdict === 'FAIL');
for (const p of platforms) {
  for (const s of expected) {
    if (!stages.some((x) => x.platform === p && x.stage === s)) {
      stages.push({ platform: p, stage: s, verdict: 'NOT_MEASURED', reason: anyFail ? 'not run: an earlier stage failed' : 'not run' });
    }
  }
}
const order = ['unit', 'unit_java21', 'bed_lock', 'version_bump', 'build', 'deployed_bundle', 'failed_components', 'clientlib', 'proxy_config', 'e2e', 'e2e_live'];
stages.sort((a, b) => platforms.indexOf(a.platform) - platforms.indexOf(b.platform) || order.indexOf(a.stage) - order.indexOf(b.stage));

// Live waiver: --allow-no-live turns the missing-credentials NOT_MEASURED into an accepted one.
for (const s of stages) if (s.stage === 'e2e_live' && s.verdict === 'NOT_MEASURED' && allowNoLive && liveMissing) s.waived = true;

let verdict = 'PASS';
if (stages.some((s) => s.verdict === 'FAIL')) verdict = 'FAIL';
else if (stages.some((s) => s.verdict === 'NOT_MEASURED' && (!s.waived || s.stage === 'e2e_live'))) verdict = 'NOT_MEASURED';
const exitOk = verdict === 'PASS' || (verdict === 'NOT_MEASURED' && stages.filter((s) => s.verdict === 'NOT_MEASURED').every((s) => s.waived));

const aem = {};
for (const r of readTsv('aem.tsv')) {
  aem[r[0]] = { url: r[1] };
  if (r[2]) aem[r[0]].bundle_version = r[2];
  if (r[3]) aem[r[0]].bundle_state = r[3];
}
const sumPins = (kind, key) => allTests.filter((t) => t.kind === kind && t.outcome === key);
// Counted once per test, not once per platform: both platforms run the same sources.
const distinct = (list) => new Set(list.map((t) => t.id.replace(/^(unit|e2e):[^:]+:/, '$1:'))).size;
const defectPins = {
  unit_disabled_defect: distinct(allTests.filter((t) => t.kind === 'unit' && t.defect)),
  e2e_fixme_total: distinct(sumPins('e2e', 'fixme')),
  e2e_fixme_defect: distinct(allTests.filter((t) => t.kind === 'e2e' && t.outcome === 'fixme' && t.defect)),
  e2e_fixme_limitation: distinct(allTests.filter((t) => t.kind === 'e2e' && t.outcome === 'fixme' && t.limitation)),
};

const summary = {
  schema: 1,
  generated_at: new Date().toISOString(),
  verdict,
  exit_ok: exitOk,
  mode,
  git: { sha: args.sha || '', branch: args.branch || '' },
  connector_version: args.version || '',
  platforms,
  // Per-bed run lock (docs/testing.md). waited_secs null = no lock taken (unit-only, dry run).
  lock: { waited_secs: lockWait, beds: lockBeds },
  aem,
  stages,
  unit: unitSummary,
  coverage,
  e2e: e2eSummary,
  defect_pins: defectPins,
  tests: allTests,
};
if (args.out) fs.writeFileSync(args.out, JSON.stringify(summary, null, 2) + '\n');

// ---- table ----
console.log('');
console.log('── gate summary');
for (const s of stages) {
  console.log(`   ${s.verdict.padEnd(12)} ${(s.platform + '/' + s.stage).padEnd(28)} ${s.reason}${s.waived ? ' (waived by flag)' : ''}`);
}
for (const p of platforms) {
  if (coverage[p]) console.log(`   coverage [${p}]: line ${coverage[p].line_pct}%  branch ${coverage[p].branch_pct}%`);
}
console.log(`   defect pins: ${defectPins.unit_disabled_defect} unit @Disabled(DEFECT), ${defectPins.e2e_fixme_defect} of ${defectPins.e2e_fixme_total} e2e fixme marked DEFECT`);
console.log(`   VERDICT: ${verdict}${args.out ? `  (json: ${args.out})` : ''}`);
process.exit(exitOk ? 0 : 1);
