/*
 * Brightcove Admin tool - JSONP transport.
 *
 * Defines the global Load(url) used throughout brcAdmin.js and brcUI.js to talk
 * to the /bin/brightcove/api servlet. The servlet is called with a `callback`
 * parameter and responds with `text/javascript` of the form
 * `theCallbackName({ ... json ... });`, so the response is fetched by injecting
 * a script element and letting the browser execute it.
 *
 * This replaces the com.iskitz.ajile vendor bundle (AJILE 1.2.1, 2003-2007),
 * which previously supplied this function as a side effect of its module-import
 * machinery. Static security scanners flag DOM-XSS patterns in that bundle's
 * unused import/parsing paths (BGS-1706). Only the script-injection behaviour
 * below was ever used, so the bundle is replaced by this equivalent.
 *
 * Behavioural parity with the function it replaces:
 *  - the script element is appended to <head> and therefore loads asynchronously
 *    (dynamically inserted scripts default to async), matching AJILE, which never
 *    set `defer`;
 *  - no cache-busting parameter is appended. AJILE's refresher defaulted to off
 *    and was only enabled through a query-string option on its own <script src>,
 *    which never applied here because the file was served concatenated inside a
 *    clientlib;
 *  - returns true when a request was issued, false when it was not.
 *
 * Every call site passes a single URL argument.
 */
(function (global) {
    'use strict';

    function Load(url) {
        if (!url) {
            return false;
        }

        var script = document.createElement('script');
        script.type = 'text/javascript';
        script.async = true;
        script.src = url;

        // The servlet response executes on load; the element itself is not needed
        // afterwards. Removing it keeps <head> from growing over a long session,
        // since the admin tool re-issues these requests on every refresh, sort,
        // search and page change.
        function cleanup() {
            script.onload = script.onerror = null;
            if (script.parentNode) {
                script.parentNode.removeChild(script);
            }
        }

        script.onload = cleanup;
        script.onerror = function () {
            // The JSONP callback never fires on a transport failure, so surface it
            // rather than leaving the caller waiting silently.
            if (global.console && global.console.error) {
                global.console.error('Brightcove admin: request failed to load [' + url + ']');
            }
            cleanup();
        };

        (document.head || document.getElementsByTagName('head')[0]).appendChild(script);
        return true;
    }

    // Exposed globally: called from brcAdmin.js and brcUI.js, and from inline
    // onclick handlers those files generate, which resolve against global scope.
    global.Load = Load;
})(window);
