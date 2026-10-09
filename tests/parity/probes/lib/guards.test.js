// Self-test for guards.js. Plain node, no AEM, no network: `node guards.test.js`
const g = require('./guards');
let failures = 0;
function t(name, fn) { try { fn(); console.log(`ok   ${name}`); } catch (e) { failures++; console.log(`FAIL ${name}: ${e.message}`); } }
const refuses = (fn) => { try { fn(); } catch (e) { if (e instanceof g.GuardError) return; throw e; } throw new Error('expected a GuardError, got none'); };

t('localhost accepted', () => g.assertLocalTarget('http://localhost:4502'));
t('127.0.0.1 accepted', () => g.assertLocalTarget('http://127.0.0.1:4602'));
t('remote refused', () => refuses(() => g.assertLocalTarget('https://author.example.com')));
t('remote refused when flag absent from cli()', () => refuses(() => g.assertLocalTarget('https://author.example.com', { allowRemote: g.cli(['http://x']).allowRemote })));
t('remote accepted with --allow-remote', () => g.assertLocalTarget('https://author.example.com', { allowRemote: g.cli(['a', '--allow-remote']).allowRemote }));
t('unparseable url refused', () => refuses(() => g.assertLocalTarget('not a url')));
t('cli strips flags from positionals', () => { const c = g.cli(['http://localhost:4502', '--allow-remote', 'out']); if (c.positional.join() !== 'http://localhost:4502,out') throw new Error(c.positional.join()); });
t('throwaway name accepted', () => g.assertMutable('video', { id: '1', name: 'e2e-throwaway-abc' }));
t('real name refused', () => refuses(() => g.assertMutable('video', { id: '2', name: 'Some Real Video' })));
t('absent name refused', () => refuses(() => g.assertMutable('playlist', { id: '3' })));
t('prefix must be at the start', () => refuses(() => g.assertMutable('folder', { id: '4', name: 'my e2e-throwaway-x' })));
t('created-in-run id accepted', () => { g.markCreated('99'); g.assertMutable('video', { id: 99, name: 'whatever' }); });
t('verdict PASS', () => { if (g.verdictOf([{ ok: true }, { ok: true }]) !== 'PASS') throw new Error('x'); });
t('verdict FAIL beats NOT MEASURED', () => { if (g.verdictOf([{ ok: null }, { ok: false }]) !== 'FAIL') throw new Error('x'); });
t('verdict NOT MEASURED on absent', () => { if (g.verdictOf([{ ok: true }, { ok: undefined }]) !== 'NOT MEASURED') throw new Error('x'); });
t('verdict NOT MEASURED on empty', () => { if (g.verdictOf([]) !== 'NOT MEASURED') throw new Error('x'); });

console.log(failures ? `\n${failures} FAILED` : '\nall guard self-tests passed');
process.exit(failures ? 1 : 0);
