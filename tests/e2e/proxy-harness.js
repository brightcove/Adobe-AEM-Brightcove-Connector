// Logging forward proxy for the connector's "proxy honoured" checks (parity row 37).
// App-agnostic primitive: no connector knowledge. Context: ../parity/probes/proxy/README.md
//
// What it is: a plain HTTP forward proxy on 127.0.0.1 that handles
//   - CONNECT host:port  (every HTTPS call the connector makes; the host is all we need,
//                         the tunnel is relayed untouched, no TLS interception)
//   - absolute-URI requests (plain http://), relayed
// and records every one as { t, method, host, port, action }, action = allow | deny.
// `deny` is a list of RegExps matched against the target host: a matching CONNECT is
// answered 403 and never relayed. That turns "does this call go through the proxy?"
// into a measurable control: a call that still succeeds against a denied host did not
// use the proxy.
//
// Usage:
//   const p = new LoggingProxy({ logFile }); await p.start();   // p.address = '127.0.0.1:<port>'
//   const m = p.mark(); ... drive traffic ... p.since(m)         // entries after the mark
//   p.setDeny([/amazonaws\.com$/]); await p.stop();
// CLI: node proxy-harness.js [--port N] [--deny regex]...   (prints JSONL to stdout)
const net = require('net');
const http = require('http');
const fs = require('fs');

class LoggingProxy {
  constructor({ port = 0, logFile = null, deny = [] } = {}) {
    this.port = port;
    this.logFile = logFile;
    this.deny = deny;
    this.entries = [];
    this.sockets = new Set();
    this.server = http.createServer((req, res) => this.onPlain(req, res));
    this.server.on('connect', (req, sock, head) => this.onConnect(req, sock, head));
    this.server.on('connection', (s) => { this.sockets.add(s); s.on('close', () => this.sockets.delete(s)); });
  }

  setDeny(list) { this.deny = list; }

  record(method, host, port, action) {
    const e = { t: new Date().toISOString(), method, host, port, action };
    this.entries.push(e);
    if (this.logFile) fs.appendFileSync(this.logFile, JSON.stringify(e) + '\n');
    return e;
  }

  denied(host) { return this.deny.some((re) => re.test(host)); }

  onConnect(req, clientSock, head) {
    const [host, portStr] = req.url.split(':');
    const port = Number(portStr) || 443;
    if (this.denied(host)) {
      this.record('CONNECT', host, port, 'deny');
      clientSock.end('HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n');
      return;
    }
    this.record('CONNECT', host, port, 'allow');
    const up = net.connect(port, host, () => {
      clientSock.write('HTTP/1.1 200 Connection Established\r\n\r\n');
      if (head && head.length) up.write(head);
      up.pipe(clientSock);
      clientSock.pipe(up);
    });
    const kill = () => { up.destroy(); clientSock.destroy(); };
    up.on('error', kill);
    clientSock.on('error', kill);
  }

  onPlain(req, res) {
    let u;
    try { u = new URL(req.url); } catch (e) {
      res.writeHead(400); res.end('absolute URI required'); return;
    }
    const port = Number(u.port) || 80;
    if (this.denied(u.hostname)) {
      this.record(req.method, u.hostname, port, 'deny');
      res.writeHead(403); res.end(); return;
    }
    this.record(req.method, u.hostname, port, 'allow');
    const up = http.request({ host: u.hostname, port, path: u.pathname + u.search, method: req.method, headers: req.headers },
      (r) => { res.writeHead(r.statusCode, r.headers); r.pipe(res); });
    up.on('error', () => { res.writeHead(502); res.end(); });
    req.pipe(up);
  }

  start() {
    return new Promise((resolve) => {
      this.server.listen(this.port, '127.0.0.1', () => {
        this.port = this.server.address().port;
        resolve(this);
      });
    });
  }

  get address() { return `127.0.0.1:${this.port}`; }

  mark() { return this.entries.length; }
  since(mark) { return this.entries.slice(mark); }
  // Distinct hosts seen after `mark`, optionally only the allowed/denied ones.
  hostsSince(mark, action) {
    return [...new Set(this.since(mark).filter((e) => !action || e.action === action).map((e) => e.host))];
  }

  // Drop every open tunnel. ⚠️ The JVM pools keep-alive connections per proxy+host, so a
  // later call to a host it already tunnelled to REUSES the tunnel and the proxy sees no
  // new CONNECT (measured: a dataload's CMS calls were invisible after an earlier search,
  // and a denied host was still reachable through a pooled tunnel). Call this before each
  // measured action so every call has to open, and therefore log, a fresh CONNECT.
  closeTunnels() {
    for (const s of this.sockets) s.destroy();
  }

  stop() {
    return new Promise((resolve) => {
      for (const s of this.sockets) s.destroy();
      this.server.close(() => resolve());
    });
  }
}

// A TCP port nothing listens on, for the dead-proxy negative control.
function deadPort() {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolve(p)); });
    s.on('error', reject);
  });
}

module.exports = { LoggingProxy, deadPort };

if (require.main === module) {
  const args = process.argv.slice(2);
  const opt = { deny: [] };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--port') opt.port = Number(args[++i]);
    else if (args[i] === '--deny') opt.deny.push(new RegExp(args[++i]));
  }
  const p = new LoggingProxy(opt);
  p.start().then(() => {
    console.error(`logging proxy on ${p.address}`);
    const orig = p.record.bind(p);
    p.record = (...a) => { const e = orig(...a); console.log(JSON.stringify(e)); return e; };
  });
}
