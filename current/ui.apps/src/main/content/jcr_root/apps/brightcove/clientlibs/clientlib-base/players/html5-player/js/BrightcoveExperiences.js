/*
 Adobe AEM Brightcove Connector

 Copyright (C) 2020 3|SHARE Corporation

 Authors:
  Zoheb Haider
  Paul Legan

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 - Additional permission under GNU GPL version 3 section 7
 If you modify this Program, or any covered work, by linking or combining
 it with httpclient 4.1.3, httpcore 4.1.4, httpmine 4.1.3, jsoup 1.7.2,
 squeakysand-commons and squeakysand-osgi (or a modified version of those
 libraries), containing parts covered by the terms of APACHE LICENSE 2.0
 or MIT License, the licensors of this Program grant you additional
 permission to convey the resulting work.
 */

//setVideoTrackingEvents();

document.addEventListener("DOMContentLoaded", (event) => {
  createPlayers();
});

// BGS-1750: page-level registry of injected player loaders, keyed by loader src,
// so each index.min.js is added at most once per page however many containers
// share it and however often createPlayers() runs (the BGS-1690 observer below
// calls it on every inserted container). Entry: { loaded, pending: {playerID: build} }.
// ⚠️ Kept on window, not in a closure or on createPlayers: if this clientlib is
// evaluated again (a REFRESH_SELF fragment carrying its <script>), createPlayers
// is redefined and a registry stored on it would reset and re-inject every loader.
// The property name must differ from every global function name in this file: a
// top-level function declaration IS a window property.
function brcPlayerLoaders() {
    return window.brcPlayerLoaderRegistry || (window.brcPlayerLoaderRegistry = {});
}

// The live player registered under playerID, or null. ⚠️ Uses the non-creating
// videojs.getPlayers() lookup: videojs(id) CREATES a player when an element with
// that id exists. videojs is undefined until the first loader has run.
function brcLivePlayer(playerID) {
    try {
        if (typeof videojs === "undefined" || typeof videojs.getPlayers !== "function") return null;
        var player = videojs.getPlayers()[playerID];
        return (player && !player.isDisposed()) ? player : null;
    } catch (e) {
        return null;
    }
}

// True when the container already holds this live player, attached to the document,
// for the same video/account/player/embed. video.js copies the <video> tag's
// attributes onto the player element, so they compare directly.
function brcPlayerIsCurrent(player, container) {
    var el = player.el();
    if (!el || !container.contains(el) || !document.documentElement.contains(el)) return false;
    var keys = ["data-video-id", "data-account", "data-player", "data-embed"];
    for (var k = 0; k < keys.length; k++) {
        if (el.getAttribute(keys[k]) !== container.getAttribute(keys[k])) return false;
    }
    return true;
}

function createPlayers(root) {
    var scope = root || document;
    var loaders = brcPlayerLoaders();
    var all = scope.getElementsByClassName("brightcove-container");
    for (var i = 0, max = all.length; i < max; i++) {
        var selected_element = all[i];
        var playerID= selected_element.getAttribute("data-playerid");
        // Idempotency guard: a re-run (observer pass over a sibling insert, SPA or
        // slider re-render) must not dispose and rebuild a healthy player, which
        // tears down its MediaSource mid-use. Anything else falls through to rebuild.
        var existing = brcLivePlayer(playerID);
        if (existing && brcPlayerIsCurrent(existing, selected_element)) {
            continue;
        }
        if (existing) {
            try {
                existing.dispose();
            } catch (e) {

            }
        }
        var dataAccount= selected_element.getAttribute("data-account");
        var dataPlayer= selected_element.getAttribute("data-player");
        var dataEmbed= selected_element.getAttribute("data-embed");
        var dataVideoId= selected_element.getAttribute("data-video-id");
        var dataWidth= (selected_element.hasAttribute("data-width") ? selected_element.getAttribute("data-width") : '')
        var dataHeight= (selected_element.hasAttribute("data-height") ? selected_element.getAttribute("data-height") : '');
        var dataUsage = selected_element.getAttribute("data-usage");
        var src = "//players.brightcove.net/" + dataAccount + "/" + dataPlayer + "_"+dataEmbed+"/index.min.js";
        var buildPlayer = (function(playerID,dataVideoId,dataAccount,dataPlayer,dataEmbed,dataWidth,dataHeight,dataUsage,selected_element) {
            return function() {
                // Only emit width/height when the author set them: no width="" or
                // width="null" on the tag (BGS-1750).
                var sizeAttrs = (dataWidth ? ' width=\"' + dataWidth + '\"' : '') + (dataHeight ? ' height=\"' + dataHeight + '\"' : '');
                var playerHTML = '<video id=\"' + playerID + '\" data-video-id=\"' + dataVideoId + '\"  data-account=\"' + dataAccount + '\" data-player=\"' + dataPlayer + '\" data-embed=\"' + dataEmbed + '\" data-usage=\"' + dataUsage + '\" class=\"video-js\" controls' + sizeAttrs + '></video>';
                selected_element.innerHTML = playerHTML;
                bc(document.getElementById(playerID));
                videojs(playerID).ready(function () {
                    var myPlayer = this;
                    if (typeof myPlayer !== "undefined" && typeof ga != "undefined") {
                        myPlayer.on("firstplay", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            var videoDuration = myPlayer.mediainfo.duration;
                            ga("send", "video", "firstplay", videoName + " - " + videoID + " - " + videoDuration);
                        });
                        myPlayer.on("play", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "play", videoName + " - " + videoID, myPlayer.currentTime());
                        });
                        myPlayer.on("ended", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "ended", videoName + " - " + videoID, myPlayer.currentTime());
                        });
                        myPlayer.on("fullscreenchange", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "fullscreenchange", videoName + " - " + videoID, myPlayer.currentTime() + (myPlayer.isFullscreen() ? " Fullscreen" : ""));
                        });
                        myPlayer.on("pause", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "pause", videoName + " - " + videoID, myPlayer.currentTime());
                        });
                        myPlayer.on("resize", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "resize", videoName + " - " + videoID, myPlayer.currentTime());
                        });
                        myPlayer.on("seeked", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "seeked", videoName + " - " + videoID, myPlayer.currentTime());
                        });
                        myPlayer.on("volumechange", function () {
                            var videoName = myPlayer.mediainfo.name;
                            var videoID = myPlayer.mediainfo.id;
                            ga("send", "video", "volumechange", videoName + " - " + videoID, (myPlayer.volume() * 100) + "%" + (myPlayer.muted() ? " Muted" : ""));
                        });
                    }
                });
            };
        }(playerID,dataVideoId,dataAccount,dataPlayer,dataEmbed,dataWidth,dataHeight,dataUsage,selected_element));

        var loader = loaders[src];
        if (loader && loader.loaded) {
            buildPlayer();
        } else if (loader) {
            // Loader in flight: keyed by playerID so a re-run replaces this
            // container's pending build instead of stacking a second one.
            loader.pending[playerID] = buildPlayer;
        } else {
            loader = loaders[src] = { loaded: false, pending: {} };
            loader.pending[playerID] = buildPlayer;
            var s = document.createElement('script');
            s.src = src;
            s.onload = (function(ld) {
                return function() {
                    ld.loaded = true;
                    var pending = ld.pending;
                    ld.pending = {};
                    for (var id in pending) {
                        if (pending.hasOwnProperty(id)) pending[id]();
                    }
                };
            }(loader));
            s.onerror = (function(failedSrc, el) {
                return function() {
                    // Drop the entry and the dead tag so a later pass re-injects;
                    // otherwise every container for this src queues forever.
                    delete loaders[failedSrc];
                    if (el.parentNode) el.parentNode.removeChild(el);
                };
            }(src, s));
            document.body.appendChild(s);
        }
    }
}

// BGS-1690: In AEM authoring, REFRESH_SELF swaps in new component HTML but
// DOMContentLoaded never re-fires, so createPlayers() doesn't bootstrap the
// new container and the live preview stays blank until manual refresh.
// Watch the document for inserted/replaced .brightcove-container nodes and
// re-init just the affected subtree. Works for insert / edit / copy / paste
// regardless of which AEM editor event fires.
// Builds only touch nodes inside the container (<video>, the video.js wrapper)
// and append loader <script>s to <body>; none is or holds a container, so a
// build never re-triggers this observer.
(function () {
    if (typeof MutationObserver === "undefined") return;
    // BGS-1750: one observer per page. A REFRESH_SELF fragment carries this
    // clientlib's <script>; each evaluation would otherwise add another observer.
    if (window.brcPlayerObserver) return;
    window.brcPlayerObserver = true;

    var pendingRoots = [];
    var scheduled = false;

    // Bind to window so the native methods retain their receiver — calling
    // a detached window.requestAnimationFrame throws TypeError: Illegal
    // invocation in strict mode, and even in non-strict mode it's fragile.
    var defer = typeof window.requestAnimationFrame === "function"
        ? window.requestAnimationFrame.bind(window)
        : function (cb) { return window.setTimeout(cb, 0); };

    function flush() {
        scheduled = false;
        var roots = pendingRoots;
        pendingRoots = [];
        for (var i = 0; i < roots.length; i++) {
            createPlayers(roots[i]);
        }
    }

    function schedule(root) {
        pendingRoots.push(root);
        if (!scheduled) {
            scheduled = true;
            defer(flush);
        }
    }

    var observer = new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
            var added = mutations[i].addedNodes;
            for (var j = 0; j < added.length; j++) {
                var node = added[j];
                if (node.nodeType !== 1) continue;
                if (node.classList && node.classList.contains("brightcove-container")) {
                    schedule(node.parentNode || node);
                } else if (node.querySelector && node.querySelector(".brightcove-container")) {
                    schedule(node);
                }
            }
        }
    });

    function start() {
        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();