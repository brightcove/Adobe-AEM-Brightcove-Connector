/*
 * One place that turns a connector servlet path into a URL the browser can
 * actually fetch. Every `/bin/brightcove/...` request in this package must go
 * through brc.url().
 *
 * Context: current/docs/clientlibs-context-path.md
 *   (why the bare literal breaks, the source order, and the harness that
 *   proves it: tests/e2e/specs/bcon-context-path.spec.js)
 *
 * ⚠️ AEM as a Cloud Service always serves from the root, so a bare
 *    '/bin/brightcove/api.js' works there and the defect is invisible on
 *    cloud. An on-prem AEM deployed under a servlet context path (a WAR in a
 *    container, or Felix `org.apache.felix.http.context_path`) serves the same
 *    servlet at '<ctx>/bin/brightcove/api.js', so every bare literal 404s and
 *    the admin tool comes up empty. Ported from the on-prem line
 *    (upstream issue #64; ONPREM-PARITY-PLAN.md §3 Phase 3 item 1).
 */
(function (window) {
    'use strict';

    var brc = window.brc = window.brc || {};
    if (brc.url) {
        return;                     // a clientlib graph can pull this in twice
    }

    var UNKNOWN = null;             // deliberately not '' — see resolve()

    // Each source answers with a string (possibly the empty string) when it
    // knows the context path, or UNKNOWN when it cannot answer at all. The two
    // are NOT the same thing: '' is a real answer ("deployed at the root",
    // always true on AEMaaCS) while UNKNOWN means nothing on the page could
    // tell us, which is a wiring defect worth one console warning rather than
    // something to quietly default.
    function fromPage() {
        // Rendered by the page's own HTL from ${request.contextPath}. The
        // admin tool is a plain HTML page with no Granite runtime, so this is
        // the only authoritative source there.
        return typeof brc.contextPath === 'string' ? brc.contextPath : UNKNOWN;
    }

    function fromGranite() {
        try {
            var p = window.Granite.HTTP.getContextPath();
            return typeof p === 'string' ? p : UNKNOWN;
        } catch (e) {
            return UNKNOWN;
        }
    }

    function fromClassicUI() {
        try {
            var p = window.CQ.shared.HTTP.getContextPath();
            return typeof p === 'string' ? p : UNKNOWN;
        } catch (e) {
            return UNKNOWN;
        }
    }

    var SOURCES = [
        { name: 'page', get: fromPage },
        { name: 'granite', get: fromGranite },
        { name: 'classic', get: fromClassicUI }
    ];

    var warned = false;

    // Resolved on every call, never cached: Granite and CQ are installed by
    // other clientlibs and may not exist yet when this file is evaluated.
    function resolve() {
        for (var i = 0; i < SOURCES.length; i++) {
            var value = SOURCES[i].get();
            if (value !== UNKNOWN) {
                // A trailing slash would double up against the leading slash
                // of the servlet path.
                return { path: value.replace(/\/+$/, ''), source: SOURCES[i].name };
            }
        }
        return { path: '', source: 'none' };
    }

    /**
     * The context path this page is served under, plus which source said so.
     * Exposed so a test (or a support engineer in the console) can tell
     * "root deployment" from "nothing could answer".
     *
     * @return {{path: string, source: string}} source is page|granite|classic|none
     */
    brc.contextPathInfo = resolve;

    /**
     * Prefix an absolute AEM path with the servlet context path.
     * Anything that is not an absolute path (protocol-relative, absolute URL,
     * relative path, non-string) is returned untouched.
     *
     * @param {string} path e.g. '/bin/brightcove/api.js'
     * @return {string} e.g. '/aem/bin/brightcove/api.js'
     */
    brc.url = function (path) {
        if (typeof path !== 'string' || path.charAt(0) !== '/' || path.charAt(1) === '/') {
            return path;
        }
        var info = resolve();
        if (info.source === 'none' && !warned) {
            warned = true;
            // Not fatal: the root path is still right for the majority of
            // deployments. Loud once so a genuinely unwired surface is
            // diagnosable instead of silently 404ing on-prem.
            console.warn('[brightcove] context path unknown on this page ' +
                '(no brc.contextPath, Granite.HTTP or CQ.shared.HTTP); ' +
                'assuming the root. If this AEM is deployed under a context ' +
                'path, connector requests from this page will 404.');
        }
        return info.path + path;
    };
}(window));
