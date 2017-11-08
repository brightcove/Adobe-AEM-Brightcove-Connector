/*
 Adobe AEM Brightcove Connector

 Copyright (C) 2017 Coresecure Inc.

 Authors:
 Alessandro Bonfatti
 Yan Kisen
 Pablo Kropilnicki

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

function r(f){/in/.test(document.readyState)?setTimeout('r('+f+')',9):f()}
// use like
r(function(){
    createPlayers();
});
function createPlayers() {
    var all = document.getElementsByClassName("brightcove-container");
    for (var i = 0, max = all.length; i < max; i++) {
        var selected_element = all[i];
        var playerID= selected_element.getAttribute("data-playerid");
        try {
            videojs(playerID).dispose();
        } catch (e) {

        }
        var dataAccount= selected_element.getAttribute("data-account");
        var dataPlayer= selected_element.getAttribute("data-player");
        var dataEmbed= selected_element.getAttribute("data-embed");
        var dataVideoId= selected_element.getAttribute("data-video-id");
        var dataWidth= selected_element.getAttribute("data-width");
        var dataHeight= selected_element.getAttribute("data-height");
        var s = document.createElement('script');
        s.src = "//players.brightcove.net/" + dataAccount + "/" + dataPlayer + "_"+dataEmbed+"/index.min.js";
        s.onload = (function(playerID,dataVideoId,dataAccount,dataPlayer,dataEmbed,dataWidth,dataHeight,selected_element) {
            return function() {
                playerHTML = '<video id=\"' + playerID + '\" data-video-id=\"' + dataVideoId + '\"  data-account=\"' + dataAccount + '\" data-player=\"' + dataPlayer + '\" data-embed=\"' + dataEmbed + '\" class=\"video-js\" controls data-width=\"' + dataWidth + '\" data-height=\"' + dataHeight + '\"></video>';
                selected_element.innerHTML = playerHTML;
                bc(document.getElementById(playerID));
                videojs(playerID).ready(function () {
                    myPlayer = this;
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
        }(playerID,dataVideoId,dataAccount,dataPlayer,dataEmbed,dataWidth,dataHeight,selected_element));
        document.body.appendChild(s);
    }
}