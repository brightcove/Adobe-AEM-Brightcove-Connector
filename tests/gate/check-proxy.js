#!/usr/bin/env node
// Preflight for the e2e stage: the connector's HTTP proxy must be direct (empty) or the
// value the caller expects. Some live specs swap `proxyServer` to a local logging proxy for
// their duration; a crashed run leaves it pointing at a dead proxy, and every later live
// spec then fails silently (200 responses, nothing persisted).
// Context: docs/testing.md ("Bed lock"). Reads AEM_BASE / AEM_USER / AEM_PASS like the specs.
//   AEM_BASE=<url> node tests/gate/check-proxy.js
// Exit 0 PASS, 10 FAIL (prints the observed value), 20 NOT_MEASURED (config unreadable).
// BRC_GATE_EXPECT_PROXY: accept this exact proxyServer value (an intentional proxy).
'use strict';
const path = require('path');
const { readConfig } = require(path.join(__dirname, '..', 'e2e', 'connector-config'));

(async () => {
  let observed;
  try {
    observed = (await readConfig()).props.proxyServer;
  } catch (e) {
    console.log(`NOT_MEASURED: ${e.message}`);
    process.exit(20);
  }
  const value = observed == null ? '' : String(observed);
  const shown = value.replace(/\/\/[^@/]*@/, '//***@'); // never echo credentials embedded in a proxy URL
  const expect = process.env.BRC_GATE_EXPECT_PROXY || '';
  if (value === '' || value === expect) {
    console.log(`PASS: proxyServer ${value === '' ? 'is empty (direct)' : 'is the expected value'}`);
    process.exit(0);
  }
  console.log(`FAIL: connector proxyServer is ${JSON.stringify(shown)}, expected empty (direct)${expect ? ` or the BRC_GATE_EXPECT_PROXY value` : ''}. A crashed proxy-swapping live spec leaves this behind; restore the config, or set BRC_GATE_EXPECT_PROXY if the proxy is intentional.`);
  process.exit(10);
})();
