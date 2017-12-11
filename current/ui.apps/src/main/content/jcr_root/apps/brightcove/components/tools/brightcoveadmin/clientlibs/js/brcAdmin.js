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

//CONFIG

var brc_admin = brc_admin || {},
    apiLocation = brc_admin.apiProxy; //This should be set to point to proxy.jsp on your server


//Default Fields
//specifying a subset of fields cuts down on the amount of data sent over the wire.  If you want to access another field, include it here.
var fields = "id,name,shortDescription,longDescription,publishedDate,lastModifiedDate,linkURL,linkText,tags,thumbnailURL,referenceId,length,economics,videoStillURL";
//END CONFIG

//oCurrentVideoList and oCurrentPlaylistList hold the JSON data about videos and playlists
var oCurrentVideoList;
var oCurrentPlaylistList;

function getAllVideosURL() {
    $(".sortable", $("#nameCol").parent()).not($("#nameCol")).addClass("NONE");
    $("#nameCol").removeClass("ASC");
    $("#nameCol").removeClass("DESC");
    $("#nameCol").removeClass("NONE");
    $("#nameCol").addClass("ASC");
    $("#nameCol").attr("data-sortType", "");
    return getAllVideosURLOrdered("name", "");

}
function getAllVideosURLOrdered(sort_by, sort_type) {
    loadStart();
    //If currentFunction == this function name, then the current view already correspdonds to this function, so use the generic holder value
    //otherwise, we're switching from a different view so use the last stored value.
    paging.allVideos = (paging.currentFunction == getAllVideosURL) ? paging.generic : paging.allVideos;
    paging.currentFunction = getAllVideosURL;
    if (typeof searchField != "undefined" && searchVal != "" && searchVal != "Search Videos") {
        if (!isNumber(searchVal)) {
            if (typeof searchField == "undefined" || searchField == "every_field" || searchField == "") {
                return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=' + searchVal + '&sort=' + sort_type +sort_by
                    + '&limit=' + paging.size + '&start=' + paging.generic
                    + '&fields=' + fields;
            } else {
                return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=' + searchField + ':' + searchVal + '&sort=' + sort_type +sort_by
                    + '&limit=' + paging.size + '&start=' + paging.generic
                    + '&fields=' + fields;
            }
        } else {
            return apiLocation +
                '.js?isID=true&account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=' + searchVal
                + '&fields=' + fields;
        }
    } else {
        return apiLocation +
            '.js?account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&sort=' + sort_type +sort_by
            + '&limit=' + paging.size + '&start=' + paging.generic
            + '&fields=' + fields;
    }
}
function getVideoAPIURL(idx){
    return apiLocation +
    '.json?isID=true&account_id='+$("#selAccount").val()+'&a=search_videos&query='+ idx;
}
function isNumber(n) {
    return !isNaN(parseFloat(n)) && isFinite(n);
}
function getAllPlaylistsURL() {
    loadStart();
    paging.allPlaylists = (paging.currentFunction == getAllPlaylistsURL) ? paging.generic : paging.allPlaylists;
    paging.currentFunction = getAllPlaylistsURL;
    return apiLocation +
        '.js?account_id='+$("#selAccount").val()+'&a=search_playlists&callback=showAllPlaylistsCallBack'
        + '&limit=' + paging.size + '&start=' + paging.allPlaylists;
}

function getFindPlaylistsURL() {
    loadStart();
    paging.allPlaylists = (paging.currentFunction == getAllPlaylistsURL) ? paging.generic : paging.allPlaylists;
    paging.currentFunction = getAllPlaylistsURL;
    if (searchVal != "" && searchVal != "Search Playlists") {
        if (searchField == "find_playlist_by_id") {
            return apiLocation +
                '.js?isID=true&account_id='+$("#selAccount").val()+'&a=search_playlists&callback=showAllPlaylistsCallBack&query=' + searchVal
                + '&limit=' + paging.size + '&start=' + paging.allPlaylists
        } else if (searchField == "find_playlist_by_reference_id") {
            return apiLocation +
                '.js?isID=true&account_id='+$("#selAccount").val()+'&a=search_playlists&callback=showAllPlaylistsCallBack&query=ref:' + searchVal
                + '&limit=' + paging.size + '&start=' + paging.allPlaylists
        }
    } else {
        return apiLocation +
            '.js?account_id='+$("#selAccount").val()+'&a=search_playlists&callback=showAllPlaylistsCallBack'
            + '&limit=' + paging.size + '&start=' + paging.allPlaylists
    }
}
function searchVideoURL() {
    loadStart();
    paging.textSearch = (paging.currentFunction == searchVideoURL) ? paging.generic : paging.textSearch;
    paging.currentFunction = searchVideoURL;
    var sort_by = $("#trHeader th.sortable").not("NONE").attr("data-sortby");
    var data_sorttype = $("#trHeader th.sortable").not("NONE").attr("data-sorttype");
    if (searchVal != "" && searchVal != "Search Videos") {
        if (!isNumber(searchVal)) {
            if ( typeof searchField == "undefined" || searchField == "every_field" || searchField == "") {
                return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=' + searchVal + '&sort=' + data_sorttype +sort_by
                    + '&limit=' + paging.size + '&start=' + paging.textSearch
                    + '&fields=' + fields;
            } else {
                return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=' + searchField + ':' + searchVal + '&sort=' + data_sorttype +sort_by
                    + '&limit=' + paging.size + '&start=' + paging.textSearch
                    + '&fields=' + fields;
            }
        } else {
            return apiLocation +
                '.js?isID=true&account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=' + searchVal
                + '&fields=' + fields;
        }
    } else {
        loadEnd();
    }
}

function findByTag() {
    loadStart();
    var sort_by = $("#trHeader th.sortable").not("NONE").attr("data-sortby");
    var data_sorttype = $("#trHeader th.sortable").not("NONE").attr("data-sorttype");
    paging.tagSearch = (paging.currentFunction == findByTag) ? paging.generic : paging.tagSearch;
    paging.currentFunction = findByTag;
    return apiLocation
            + '.js?account_id='+$("#selAccount").val()+'&a=search_videos&callback=showAllVideosCallBack&query=tags:' + searchVal + '&sort=' + data_sorttype +sort_by
            + '&limit=' + paging.size + '&start=' + paging.tagSearch
            + '&fields=' + fields;
}

/*delete_video is a write method, and needs to be submitted to the api server as a multipart/post request.
 * however, since there are only a few fields of data being sent from this request, we'll send it as a GEt request to the proxy
 * server and let it format the multipart request.~
 * the line '}else if ( write_methods.contains(request.getParameter("command")) && request.getMethod() == "GET"){'
 * in proxy.jsp handles this kind of request.
 */
function deleteVideoURL(videoId) {
    var sort_by = $("#trHeader th.sortable").not("NONE").attr("data-sortby");
    var data_sorttype = $("#trHeader th.sortable").not("NONE").attr("data-sorttype");
    loadStart();
    paging.textSearch = (paging.currentFunction == searchVideoURL) ? paging.generic : paging.textSearch;

    return apiLocation + '.js?account_id='+$("#selAccount").val()+'&a=delete_video&query=' + videoId + '&callback=showAllVideosCallBack'+ '&sort=' + data_sorttype +sort_by
        + '&limit=' + paging.size + '&start=' + paging.textSearch;

}

function showAllVideosCallBack(o) {
    if (null == o.error) {
        oCurrentVideoList = o.items;
        buildMainVideoList("All Videos");
        doPageList(o.totals, "Videos");
    } else {
        var message = (null != o.error.message) ? o.error.message : o.error;
        console.log("Server Error: " + message);
    }
    loadEnd();
}

function showSearchPlaylistsCallBack(o) {
    if (null == o.error) {
        oCurrentPlaylistList = o.items;
        buildPlaylistList();
        var total = o.totals;
        if (total < o.items.length)total = o.items.length;
        doPageList(total, "Playlists");
    } else {
        var message = (null != o.error.message) ? o.error.message : o.error;
        console.log("Server Error: " + message);
    }
    loadEnd();
}
function showAllPlaylistsCallBack(o) {
    if (null == o.error) {
        oCurrentPlaylistList = o.items;
        buildPlaylistList();
        doPageList(o.totals, "Playlists");
    } else {
        var message = (null != o.error.message) ? o.error.message : o.error;
        console.log("Server Error: " + message);
    }
    loadEnd();
}

function searchVideoCallBack(o) {
    if (null == o.error) {
        oCurrentVideoList = o.items;
        buildMainVideoList("Search: " + searchVal);
        var count = (o.total_count >= 0) ? o.total_count : o.items.length;
        doPageList(count, "Videos");
    } else {
        var message = (null != o.error.message) ? o.error.message : o.error;
        console.log("Server Error: " + message);
    }
    loadEnd();
}

function findByTagCallBack(o) {
    if (null == o.error) {
        oCurrentVideoList = o.items;
        buildMainVideoList("Tag Search: " + searchVal);
        doPageList(o.total_count, "Videos");
    } else {
        var message = (null != o.error.message) ? o.error.message : o.error;
        console.log("Server Error: " + message);
    }
    loadEnd();
}

//Function calls update_video to change metadata
//Also see metaEdit in vm_ui.js
function metaSubmit() {
    var form = document.getElementById("metaEditForm");
    form.action = apiLocation;
    form.submit();
    loadStart();
    //noWrite();
    closeBox('metaEditPop');
    Load(getAllVideosURL());
}

function delConfYes() {
    var checkedVideos = $("#tblMainList input:checked");
    var IDs = "";
    checkedVideos.each(function () {
            IDs = IDs + $(this).val() + ",";
        }
    );
    Load(deleteVideoURL(IDs));
    closeBox('delConfPop');
    Load(getAllVideosURL());
}

function buildJSONRequest(form) {
    if (document.getElementById('name').value == "" || document.getElementById('shortDescription').value == "") {
        alert("Require Name, Short Description and File");
        return;
    } else {
        json = document.getElementById('video').value
        //Construct the JSON request:
        json.value = '{"name": "' +
            document.getElementById('name').value + '", "shortDescription": "' + document.getElementById('shortDescription').value +
            '"}';
        document.getElementById('video').value = json.value;
    }
}
function startUpload() {
    var form = document.getElementById("uploadForm");
    buildJSONRequest(form);
    form.action = apiLocation;
    form.submit();
    loadStart();
    //noWrite();
    closeBox('uploadDiv', form);
    Load(getAllVideosURL());
}
function startExtUpload(window, formid) {
    var form = document.getElementById(formid);
    buildJSONRequest(form);
    form.action = apiLocation;
    form.submit();
    loadStart();
    //noWrite();
    window.destroy();
    //closeBox('uploadDiv', form);
    Load(getAllVideosURL());
}

function startVideoImageUpload() {
    var form = document.getElementById("uploadVideoImageForm");
    form.action = apiLocation;
    form.submit();
    loadStart();
    //noWrite();
    closeBox('uploadVideoImageDiv', form);
    Load(getAllVideosURL());
}
//See createPlaylistBox for more info
function createPlaylistSubmit() {
    var form = document.getElementById("createPlaylistForm");

    form.action = apiLocation+"?account_id="+$("#selAccount").val();
    form.submit();
    $('#createPlstVideoTable').empty();
    loadStart();
    //noWrite();
    closeBox('createPlaylistDiv', form);
    Load(getAllPlaylistsURL());
}

function modPlstSubmit() {
    loadStart();
    noWrite();
    closeBox('modPlstPop');
    Load(getAllPlaylistsURL());
}
