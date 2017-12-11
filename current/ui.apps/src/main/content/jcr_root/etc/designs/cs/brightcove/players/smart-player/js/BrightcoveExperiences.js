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
var BCLplayer,
    BCLexperienceModule,
    BCLvideoPlayer,
    BCLcurrentVideo;
    var api;

//listener for player error
function onPlayerError(event) {
    /* */
}

//listener for when player is loaded
function onPlayerLoaded(id) {
    // newLog();
//  log("EVENT: onPlayerLoaded");
    try {
        experienceID = id;

        BCLplayer = brightcove.getExperience(id);
        if(BCLplayer)
        {
            api = "FLASH-ONLY";
        } else {
            BCLplayer = brightcove.api.getExperience(id);
            api = "SMART";
        }
        if (typeof BCLplayer!='undefined') {
            BCLexperienceModule = BCLplayer.getModule(APIModules.EXPERIENCE);
        }
    } catch(e) {
    }
    //BCLexperienceModule = BCLplayer.getModule(APIModules.EXPERIENCE);
}
function playVideoID(videoID){
    // add a listener for media change events
    BCLvideoPlayer.addEventListener(BCMediaEvent.BEGIN, onMediaBegin);
    BCLvideoPlayer.addEventListener(BCMediaEvent.COMPLETE, onMediaBegin);
    BCLvideoPlayer.addEventListener(BCMediaEvent.CHANGE, onMediaBegin);
    BCLvideoPlayer.addEventListener(BCMediaEvent.ERROR, onMediaBegin);
    BCLvideoPlayer.addEventListener(BCMediaEvent.PLAY, onMediaBegin);
    BCLvideoPlayer.addEventListener(BCMediaEvent.STOP, onMediaBegin);
}
//listener for when player is ready
function onPlayerReady(event) {
    // log("EVENT: onPlayerReady");
    if (typeof BCLplayer=='undefined') onPlayerLoaded(myExpId);
    // get a reference to the video player module
    BCLvideoPlayer = BCLplayer.getModule(APIModules.VIDEO_PLAYER);

    //fetch the video data and process the cuepoint
    var videoID;
    if ("SMART" === api) {
        BCLvideoPlayer.getCurrentVideo(function (videoDTO) {
            if (videoDTO) {
                videoID =videoDTO.id;
                playVideoID(videoID);
            }
        });
    } else {
        videoID= BCLvideoPlayer.getCurrentVideo().id;
        playVideoID(videoID);
    }


}
//listener for media change events
function onMediaBegin(event) {
    var BCLcurrentVideoID;
    var BCLcurrentVideoNAME;
    BCLcurrentVideoID = BCLvideoPlayer.getCurrentVideo().id;
    BCLcurrentVideoNAME = BCLvideoPlayer.getCurrentVideo().displayName;
    if (typeof ga != "undefined") {
        switch (event.type) {
            case "mediaBegin":
                var currentVideoLength = "0";
                currentVideoLength = BCLvideoPlayer.getCurrentVideo().length;
                if (currentVideoLength != "0") currentVideoLength = currentVideoLength / 1000;
                ga("send", "video", event.type, BCLcurrentVideoNAME + " - " + BCLcurrentVideoID  + " - " + currentVideoLength);
                break;
            case "mediaPlay":
                ga("send", "video", event.type, BCLcurrentVideoNAME + " - " + BCLcurrentVideoID, event.position);
                break;
            case "mediaStop":
                ga("send", "video", event.type, BCLcurrentVideoNAME + " - " + BCLcurrentVideoID, event.position);
                break;
            case "mediaChange":
                ga("send", "video", event.type, BCLcurrentVideoNAME + " - " + BCLcurrentVideoID, event.position);
                break;
            case "mediaComplete":
                ga("send", "video", event.type, BCLcurrentVideoNAME + " - " + BCLcurrentVideoID, event.position);
                break;
            default:
                ga("send", "video", event.type, BCLcurrentVideoNAME + " - " + BCLcurrentVideoID);
        }
    }
}

if (customBC == undefined) {
    var customBC = {};
    customBC.createElement = function (el) {
        if (document.createElementNS) {
            return document.createElementNS('http://www.w3.org/1999/xhtml', el);
        } else {
            return document.createElement(el);
        }
    };
    customBC.createVideo = function (width, height, playerID, playerKey, videoPlayer, VideoRandomID) {
        var innerhtml = '<object id="myExperience_' + VideoRandomID + '" class="BrightcoveExperience">';
        innerhtml += '<param name="bgcolor" value="#FFFFFF" />';
        innerhtml += '<param name="width" value="' + width + '" />';
        innerhtml += '<param name="height" value="' + height + '" />';
        innerhtml += '<param name="playerID" value="' + playerID + '" />';
        innerhtml += '<param name="playerKey" value="' + playerKey + '" />';
        innerhtml += '<param name="isVid" value="true" />';
        innerhtml += '<param name="isUI" value="true" />';
        innerhtml += '<param name="dynamicStreaming" value="true" />';
        innerhtml += '<param name="@videoPlayer" value="' + videoPlayer + '" />';
		if ( window.location.protocol == 'https:') 
        {
            innerhtml += '<param name="secureConnections" value="true" /> ';
            innerhtml += '<param name="secureHTMLConnections" value="true" />';
        }
		innerhtml += '<param name="templateLoadHandler" value="onPlayerLoaded" />';
        innerhtml += '<param name="templateReadyHandler" value="onPlayerReady" />';
        innerhtml += '<param name="templateErrorHandler" value="onPlayerError" />';
        innerhtml += '<param name="includeAPI" value="true" /> ';
        innerhtml += '<param name="wmode" value="transparent" />';
        innerhtml += '</object>';
        var objID = document.getElementById(VideoRandomID);
        objID.innerHTML = innerhtml;

        var apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.src = "https://sadmin.brightcove.com/js/BrightcoveExperiences.js";
        objID.parentNode.appendChild(apiInclude);

        apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.src = "https://sadmin.brightcove.com/js/APIModules_all.js";
        objID.parentNode.appendChild(apiInclude);

        apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.src = "https://files.brightcove.com/bc-mapi.js";
        objID.parentNode.appendChild(apiInclude);

        apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.text = "window.onload = function() {brightcove.createExperiences();};";
        objID.parentNode.appendChild(apiInclude);
    };
    customBC.createPlaylist = function (width, height, playerID, playerKey, videoPlayer, VideoRandomID) {
        var innerhtml = '<object id="myExperience_' + VideoRandomID + '" class="BrightcoveExperience">';
        innerhtml += '<param name="bgcolor" value="#FFFFFF" />';
        innerhtml += '<param name="width" value="' + width + '" />';
        innerhtml += '<param name="height" value="' + height + '" />';
        innerhtml += '<param name="playerID" value="' + playerID + '" />';
        innerhtml += '<param name="playerKey" value="' + playerKey + '" />';
        innerhtml += '<param name="isVid" value="true" />';
        innerhtml += '<param name="isUI" value="true" />';
        innerhtml += '<param name="dynamicStreaming" value="true" />';
        innerhtml += '<param name="@playlistTabs" value="' + videoPlayer + '" />';
		if ( window.location.protocol == 'https:') 
        {
            innerhtml += '<param name="secureConnections" value="true" /> ';
            innerhtml += '<param name="secureHTMLConnections" value="true" />';
        }
		innerhtml += '<param name="templateLoadHandler" value="onPlayerLoaded" />';
        innerhtml += '<param name="templateReadyHandler" value="onPlayerReady" />';
        innerhtml += '<param name="templateErrorHandler" value="onPlayerError" />';
        innerhtml += '<param name="includeAPI" value="true" /> ';
        innerhtml += '<param name="wmode" value="transparent" />';
        innerhtml += '</object>';
        var objID = document.getElementById(VideoRandomID);
        objID.innerHTML = innerhtml;

        var apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.src = "https://sadmin.brightcove.com/js/BrightcoveExperiences_all.js";
        objID.parentNode.appendChild(apiInclude);

        apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.src = "https://sadmin.brightcove.com/js/APIModules_all.js";
        objID.parentNode.appendChild(apiInclude);

        apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.src = "https://files.brightcove.com/bc-mapi.js";
        objID.parentNode.appendChild(apiInclude);

        apiInclude = customBC.createElement('script');
        apiInclude.type = "text/javascript";
        apiInclude.text = "window.onload = function() {brightcove.createExperiences();};";
        objID.parentNode.appendChild(apiInclude);
    };
}