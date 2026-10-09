# Context paths and connector servlet URLs

Why every `/bin/brightcove/...` URL in `ui.apps` goes through one helper, what
the helper reads, and how the behaviour is proved.

Read this before adding a request to a connector servlet from client-side code.
If you change what it describes, update it in the same PR.

## The defect

AEM as a Cloud Service always serves from the root, so a bare absolute literal

```js
$.ajax({ url: '/bin/brightcove/api.js', ... })
```

works there, and on cloud it is indistinguishable from a correct URL. An on-prem
AEM can be deployed under a servlet **context path**: as a WAR in a container
under `/aem`, or with Felix `org.apache.felix.http.context_path`. That instance
serves the same servlet at `/aem/bin/brightcove/api.js`, so every bare literal
404s. Symptom: the admin tool loads its chrome and then stays empty, with 404s
in the network log and no server-side error at all.

The on-prem 6.0.x line fixed this in `c100a16` (prefix everything) and
`cf674b7` (fall back to `''` when the context path is falsy) in response to
upstream issue #64. The fix was never on the cloud line, and the parity work
ports it: `ONPREM-PARITY-PLAN.md` §3 Phase 3 item 1.

## The helper

`clientlibs/clientlib-url/js/brcUrl.js`, clientlib category `brc.url`:

```js
brc.url('/bin/brightcove/api.js')   // -> '/aem/bin/brightcove/api.js'
brc.contextPathInfo()               // -> { path: '/aem', source: 'granite' }
```

`brc.url` prefixes absolute paths and returns anything else (absolute URL,
protocol-relative, relative, non-string) untouched. Every consumer clientlib
declares `dependencies="[brc.url]"`:

| Clientlib | Category it hooks | Context-path source there |
|---|---|---|
| `clientlib-tools` | `brc.brightcove-api` (admin tool) | `page` |
| `clientlib-dialogs` | `brightcove.player.dialogs` | `granite` |
| `clientlib-assetfinder` | `cq.authoring.editor.hook.assetfinder` | `granite` |
| `clientlib-variant-dialog` | `dam.gui.admin.coral`, `dam.gui.assetdetails.coral` | `granite` |
| `clientlib-custom-assets-menu-options-visibility` | same as above | `granite` |
| `components/page/brightcoveplayer/clientlib` | `cq.authoring.dialog` | `granite` |

### Source order, and why "absent" is not "root"

`brc.url` asks three sources in order and takes the first that can answer:

1. **`page`** — `window.brc.contextPath`, rendered by the page's own HTL from
   `${request.contextPath}`. The admin tool
   (`components/tools/brightcoveadmin/brightcoveadmin.html`) is a plain HTML
   page with no Granite runtime, so this is the only authoritative source there,
   and it is the reason the admin tool does not depend on `Granite` being
   present.
2. **`granite`** — `Granite.HTTP.getContextPath()`, present on every Touch UI
   surface.
3. **`classic`** — `CQ.shared.HTTP.getContextPath()`, for Classic UI pages.

An empty string from any of these is a **real answer**: "deployed at the root",
which is always the case on AEMaaCS. No source answering at all is a different
state (`source: 'none'`), which means a surface was wired without a context-path
source. The helper still falls back to the root, because that is right for most
deployments, but logs one `console.warn` so the unwired surface is diagnosable
instead of silently 404ing on-prem. ⚠️ Do not collapse `''` and "unknown": on a
root-served instance they produce identical URLs, so a check that cannot tell
them apart reports a pass for an unwired surface.

The context path is resolved on every call, never cached: `Granite` and `CQ` are
installed by other clientlibs and may not exist when `brcUrl.js` is evaluated.

### `brcAdmin.js` is special

`brcAdmin.js` builds 20-odd URLs from a single `apiLocation` variable, which
comes from `brc_admin.apiProxy` set in the admin page's inline script. That
inline script runs before the clientlib includes, so it cannot call `brc.url`.
`apiProxy` therefore stays a bare path and is wrapped once at the point of use:

```js
apiLocation = brc.url(brc_admin.apiProxy);
```

## How it is proved

`tests/e2e/specs/bcon-context-path.spec.js`, part of the pre-QA gate, so it runs
on both platforms.

Neither local AEM is deployed under a context path, so the spec makes the client
believe it is:

1. **Admin tool, full run under a simulated context path.** The spec rewrites
   the one value the page publishes (`window.brc.contextPath`) to `/aemctx`,
   then stands in for the servlet container: `/aemctx/**` requests are served
   from the real path underneath. It then asserts (a) no request reached
   `/bin/brightcove` without the prefix, (b) at least one prefixed connector
   request was actually seen, so a run that measured nothing cannot pass, and
   (c) the video table still rendered rows, so a fix that prefixes but breaks
   the response path fails too. If the page stops publishing the value, the
   route handler throws rather than passing with nothing rewritten.
2. **Touch UI.** On the DAM metadata editor the spec asserts the helper loaded
   at all (the clientlib dependency really resolved) and that
   `contextPathInfo().source === 'granite'`, i.e. a real source answered rather
   than the root default, then stubs `Granite.HTTP.getContextPath` and checks
   the prefix lands.
3. **Source guard.** No bare `/bin/brightcove` literal is left in any shipped
   clientlib JS, with a per-file exemption list that carries a reason.

### Not measured

- **Dialog XML attributes that name a servlet path directly**:
  `field_account.xml` (`options`), `field_videoPlayer.xml`,
  `field_videoPlayerPL.xml`, `field_playerPath.xml`,
  `page/brightcoveplayer/tab_basic.xml` (`storePath`), and the Coral
  autocomplete `src` in `content/brightcoveplayer/_cq_dialog` and
  `content/brightcoveexperiences/_cq_dialog`. Whether Granite externalizes
  these server-side before the browser sees them has not been measured, and
  XML has no expression language to prefix them by hand. The on-prem line never
  fixed them either. Status: **not measured**, not "works".
- A real container-level context path. The honest bed is an AEM actually started
  under one; the spec's simulation covers the client-side logic but not
  server-side URL rewriting.
