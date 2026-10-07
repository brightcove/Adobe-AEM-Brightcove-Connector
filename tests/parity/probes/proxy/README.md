# Proxy-honoured probe (parity row 37, BGS-1600)

`bgs-1600-proxy-probe.js` answers, per outbound call path: is it sent through the
configured proxy, and does it fail when that proxy is dead (so it is not going direct)?
It prints `PASS`, `FAIL` or `NOT MEASURED` per path and exits non-zero on anything but PASS.

## How the connector takes its proxy

- Factory OSGi config `com.coresecure.brightcove.wrapper.sling.ConfigurationServiceImpl.<uuid>`,
  property `proxyServer` (`host:port`, HTTP proxy, no credentials, no scheme). Same PID and key
  on cloud and on the 6.5 LTS (`-prem`) build.
- `Platform.setProxy` parses it and stores it in a **static** `HttpServices.PROXY`. It is set
  every time a `BrightcoveAPI` is built (every servlet request, every import thread), so the
  last config read wins globally. A value that is not exactly `host:port` silently means no proxy.
- Honoured by: `HttpServices.getSSLConnection` (OAuth, CMS, Dynamic Ingest, Players, the
  image/thumbnail GETs in `executeFullGet`/`getRemoteBinary`/`ServiceUtil.getRenditionInputStream`/
  `BrcApi.uploadImage`), the Apache `HttpPatch` fallback (`builder.setProxy`), and the S3 PUT
  (`S3UploadUtil.uploadToUrl(URL, InputStream, Proxy)` fed `HttpServices.getProxy()`).
- Bypass: `BrcImageApi.doGet` (`/bin/brightcove/image`, `/bin/services/brightcove/image`,
  `.../cache/image`) reads the poster with `ImageIO.read(URL)`, which never consults
  `HttpServices.PROXY`. Also unused-but-present: `S3UploadUtil.uploadToUrl(URL, InputStream)`
  opens a direct connection (no caller).

## Method

Four phases flip only `proxyServer` and run the same actions: baseline (original, proxy log
must stay empty), dead (closed local port: every action must fail and persist nothing),
routed (logging proxy: actions succeed and the log must show the hosts) and deny (the proxy
answers 403 to CONNECT for image and S3 hosts, which a dead port cannot isolate because it
fails the CMS call first). Persisted state is re-read with an independent CMS GET / DAM read.
The original config is restored by value in a `finally` and re-verified.

⚠️ The JVM pools keep-alive tunnels per proxy+host, so a later call to a host already
tunnelled to produces no new CONNECT. The harness drops all tunnels before each action
(`LoggingProxy.closeTunnels`). Without that, hosts go missing from the log and a denied host
stays reachable.

## Run

```bash
cd tests/e2e && npm install           # once, provides @playwright/test for dam.js
set -a; source ~/.brightcove/<label>.env; set +a   # the account AEM is configured with
AEM_BASE=http://localhost:4702 BRC_LOG=<instance>/crx-quickstart/logs/brightcove.log \
  PROBE_OUT=/tmp/proxy-evidence node tests/parity/probes/proxy/bgs-1600-proxy-probe.js
```

Needs the account's Video Cloud creds in the environment (otherwise NOT MEASURED). Writes only
`e2e-throwaway-*` videos and DAM assets and removes them. `tests/e2e/proxy-harness.js` is also
a standalone CLI (`node proxy-harness.js --port 3128 --deny 'regex'`).
