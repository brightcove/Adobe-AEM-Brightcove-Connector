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

var $ACTIVE_TRACKS;
var brc_admin = brc_admin || {};


//tUploadBar has the timer id for the upload progress bar, so it can be cancelled.  progressPos is used to keep track of the progress bar's position.
var tUploadBar,
    progressPos = 0,
    searchVal,
    paging;

//called once body loads.  sets the api location and loads all videos
$(function () {
    if ($.browser.msie) {
        //IE css opacity hack
        $("#screen").css("filter", "alpha(opacity = 65)")
            .css("zoom", 1);
    }

    //Initialize the paging object
    //each member variable stores the current page
    //for different views in the console.
    //currentFunction calls the corresponding function
    //on a page change.
    paging = {
        allVideos: 0,
        allPlaylists: 0,
        curPlaylist: 0,
        textSearch: 0,
        tagSearch: 0,
        generic: 0,     //used as a placeholder
        currentFunction: null,  //called when a page is changed
        size: 30     //Default Page Size [30], can be no larger than 100.
    };

    Load(getAllVideosURL());
    var app = new CQ.Switcher({});
    app.render(document.body);
    //app = new CQ.HomeLink({});
    //app.render(document.body);
    $("#selAccount").change(function () {
        var accountVal = $(this).val();
        CQ.Ext.util.Cookies.set('brc_act', accountVal);
        //createCookie("brc_act",accountVal, 1);
        window.location.reload();
    });

});

//function to move the progress bar on the video upload progress window
function uploadProgressBar() {
    progressPos += 10;
    document.getElementById("progress").style.left = (progressPos % 80) + '%';
}
function switchSort(sortIn) {
    if ("DESC" == sortIn) {
        return "";
    } else if ("ASC" == sortIn) {
        return "-";
    } else {
        return "";
    }
}
function newSortClass(sortIn) {
    if ("DESC" == sortIn) {
        return "ASC";
    } else if ("ASC" == sortIn) {
        return "DESC";
    } else {
        return "ASC";
    }
}
function newSortType(sortIn) {
    if ("-" == sortIn) {
        return "DESC";
    } else {
        return "ASC";
    }
}

function sort(object) {
    if ($(object).hasClass("ASC") || $(object).hasClass("DESC") || $(object).hasClass("NONE")) {
        var sortBy = $(object).attr("data-sortBy"),
            oldSortType = newSortType($(object).attr("data-sortType")),
            sortType = switchSort(oldSortType);
        $(".sortable", $(object).parent()).not($(object)).addClass("NONE");
        $(object).removeClass(oldSortType);
        $(object).toggleClass(newSortClass(oldSortType));
        $(object).removeClass("NONE");
        $(object).attr("data-sortType", sortType);
        Load(getAllVideosURLOrdered(sortBy, sortType));
    }
}
function buildMainVideoList(title) {

    //Wipe out the old results
    $("#tbData").empty();
    if (!$("#nameCol").hasClass("ASC") && !$("#nameCol").hasClass("DESC") && !$("#nameCol").hasClass("NONE")) {
        $("#trHeader th.sortable").add("NONE");
        $("#nameCol").addClass("ASC").attr("data-sortType", "");
    }
    // Display video count
    document.getElementById('divVideoCount').innerHTML = oCurrentVideoList.length + " videos";
    document.getElementById('nameCol').innerHTML = "Video Name<span class='order'></span>";
    document.getElementById('headTitle').innerHTML = title;
    document.getElementById('search').value = searchVal ? searchVal : "Search Videos";
    document.getElementById('tdMeta').style.display = "none";
    document.getElementById('searchDiv').style.display = "inline";
    document.getElementById('searchDiv_pl').style.display = "none";

    document.getElementById('checkToggle').style.display = "inline";
    $("span[name=buttonRow]").show();
    $(":button[name=delFromPlstButton]").hide();


    //For each retrieved video, add a row to the table
    var modDate = new Date();
    $.each(oCurrentVideoList, function (i, n) {
        modDate = new Date(n.updated_at);
        $("#tbData").append(
            "<tr style=\"cursor:pointer;\" id=\"" + (i) + "\"> \
            <td>\
                <input type=\"checkbox\" value=\"" + (n.id) + "\" id=\"" + (i) + "\" onclick=\"checkCheck()\">\
            </td><td>"
            + n.name +
            "</td><td>"
            + (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear() + "\
            </td><td>"
            + ((n.reference_id) ? n.reference_id : '') +
            "</td><td>"
            + n.id +
            "</td></tr>"
        );
        $("tr#"+i,"#tbData").bind('click', function () {
            showMetaData(this.id);
            $("#tdMeta").show();
        });
    });

    //Zebra stripe the table
    $("#tbData>tr:even").addClass("oddLine");

    //And add a hover effect
    $("#tbData>tr").hover(function () {
        $(this).addClass("hover");
    }, function () {
        $(this).removeClass("hover");
    });

    //if there are videos, show the metadata window, else hide it
    if (oCurrentVideoList.length > 0) {
        if (window.selectedVideoId) showMetaDataByVideoID(window.selectedVideoId);
    }
    else {
        closeBox("tdMeta");
    }
}

function buildPlaylistList() {

    //Wipe out the old results
    $("#tbData").empty();
    $("#trHeader th.sortable").removeClass("NONE").removeClass("ASC").removeClass("DESC");

    // Display Playlist count
    document.getElementById('divVideoCount').innerHTML = oCurrentPlaylistList.length + " playlists";
    document.getElementById('nameCol').innerHTML = "Playlist Name";
    document.getElementById('headTitle').innerHTML = "All Playlists";
    document.getElementById('search_pl').value = "Search Playlists";
    document.getElementById('tdMeta').style.display = "none";
    document.getElementById('searchDiv').style.display = "none";
    document.getElementById('searchDiv_pl').style.display = "inline";
    document.getElementById('checkToggle').style.display = "none";
    $("span[name=buttonRow]").hide();
    $(":button[name=delFromPlstButton]").hide();


    //For each retrieved playlist, add a row to the table
    $.each(oCurrentPlaylistList, function (i, n) {
        $("#tbData").append(
            "<tr style=\"cursor:pointer;\" id=\"" + i + "\">\
            <td>\
            </td><td>"
            + n.name +
            "</td><td>\
                <center>---</center>\
            </td><td>"
            + ((n.reference_id) ? n.reference_id : '') +
            "</td><td>"
            + n.id +
            "</td></tr>"
        );
    });

    //Zebra stripe the table
    $("#tbData>tr:even").addClass("oddLine");

    //And add a hover effect
    $("#tbData>tr").hover(function () {
        $(this).addClass("hover");
    }, function () {
        $(this).removeClass("hover");
    });

}
function getPlaylist(idx) {
    oCurrentPlaylistList = oCurrentPlaylistList[idx];
    paging.currentFunction = createSubPlaylist;
    changePage(0);
}

function createSubPlaylist() {
    if (oCurrentPlaylistList.video_ids.length > paging.size) {
        paging.curPlaylist = paging.generic;
        oCurrentVideoList = new Array();
        var i = paging.curPlaylist * paging.size;
        var lim = (paging.curPlaylist + 1) * paging.size > oCurrentPlaylistList.videos.length ?
            oCurrentPlaylistList.videos.length :
        (paging.curPlaylist + 1) * paging.size;
        for (; i < lim; i++) {
            oCurrentVideoList.push(oCurrentPlaylistList.videos[i]);
        }
    } else {
        oCurrentVideoList = oCurrentPlaylistList.videos;
    }

    showPlaylist();
    doPageList(oCurrentPlaylistList.videos.length, "Videos");
    paging.generic = paging.allVideos;
}

function showPlaylist() {
    //Wipe out the old results
    $("#tbData").empty();

    document.getElementById('divVideoCount').innerHTML = oCurrentVideoList.length + " videos";
    //$("#divVideoCount").html(oCurrentVideoList.length + " videos");
    document.getElementById('nameCol').innerHTML = "Video Name";
    document.getElementById('headTitle').innerHTML = oCurrentPlaylistList.name;
    document.getElementById('search').value = "Search Videos";
    document.getElementById('searchDiv').style.display = "inline"
    document.getElementById('searchDiv_pl').style.display = "none";

    document.getElementById('checkToggle').style.display = "inline"
    document.getElementById('tdMeta').style.display = "none";
    $("span[name=buttonRow]").show();
    $(".uplButton").hide();
    $(".delButton").hide();
    $(":button[name=delFromPlstButton]").show();

    //For each retrieved video, add a row to the table
    var modDate = new Date();
    $.each(oCurrentVideoList, function (i, n) {
        modDate = new Date(n.updated_at);
        $("#tbData").append(
            "<tr style=\"cursor:pointer\" id=\"" + (i) + "\"> \
            <td>\
                <input type=\"checkbox\" value=\"" + (i) + "\" id=\"" + (i) + "\" onclick=\"checkCheck()\">\
            </td><td>"
            + n.name +
            "</td><td>"
            + (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear() + "\
            </td><td>"
            + ((n.reference_id) ? n.reference_id : '') +
            "</td><td>"
            + n.id +
            "</td></tr>"
        ).children("tr").bind('click', function () {
                showMetaData(this.id);
            });
    });

    //Zebra stripe the table
    $("#tbData>tr:even").addClass("oddLine");

    //And add a hover effect
    $("#tbData>tr").hover(function () {
        $(this).addClass("hover");
    }, function () {
        $(this).removeClass("hover");
    });

    if (oCurrentVideoList.length > 0) {
        showMetaData(0);
    }
    else {
        closeBox("tdMeta");
    }

}

function showMetaData(idx) {



    $("tr.select").removeClass("select");
    idx = oCurrentVideoList.length > idx ? idx : 0;
    $("#tbData>tr:eq(" + idx + ")").addClass("select");


    //TODO: CURRENTLY
    var v = oCurrentVideoList[idx];



    // Populate the metadata panel
    document.getElementById('divMeta.name').innerHTML = v.name;
    document.getElementById('divMeta.thumbnailURL').src = v.thumbnailURL;
    document.getElementById('divMeta.videoStillURL').src = (v.images != null && v.images.poster != null && v.images.poster.src != null) ? v.images.poster.src : "";
    document.getElementById('divMeta.previewDiv').value = v.id;
    var modDate = new Date(v.updated_at);
    document.getElementById('divMeta.lastModifiedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();

    //v.length is the running time of the video in ms
    var sec = String((Math.floor(v.duration * .001)) % 60); //The number of seconds not part of a whole minute
    sec.length < 2 ? sec = sec + "0" : sec;  //Make sure  the one's place 0 is included.
    document.getElementById('divMeta.length').innerHTML = Math.floor(v.duration / 60000) + ":" + sec;

    document.getElementById('divMeta.id').innerHTML = v.id;
    document.getElementById('divMeta.shortDescription').innerHTML = "<pre>" + (v.description != null ? v.description : "") + "</pre>";

    //Construct the tag section:
    var tagsObject = "";
    if ("" != v.tags) {
        var tags = v.tags.toString().split(',');
        for (var k = 0; k < tags.length; k++) {
            if (k > 0) {
                tagsObject += ', ';
            }
            tagsObject += '<a style="cursor:pointer;color:blue;text-decoration:underline"' +
                'onclick="searchVal=\'' + tags[k].replace(/\'/gi, "\\\'") + '\';Load(findByTag(\'' + tags[k] + '\'))" >' + tags[k] + '</a>';
        }
    }
    document.getElementById('divMeta.tags').innerHTML = tagsObject;

    //if there's no link text use the linkURL as the text
    var linkText = (v.link != null && "" != v.link.text && null != v.link.text) ? v.link.text : (v.link != null && v.link.url != null) ? v.link.url : "";
    var linkURL = (v.link != null && v.link.url != null) ? v.link.url : "";
    document.getElementById('divMeta.linkURL').innerHTML = linkText;

    document.getElementById('divMeta.linkURL').href = linkURL;
    document.getElementById('divMeta.linkText').innerHTML = linkText;
    document.getElementById('divMeta.economics').innerHTML = v.economics;

    modDate = new Date(v.published_at);
    document.getElementById('divMeta.publishedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();
    document.getElementById('divMeta.referenceId').innerHTML = (v.reference_id != null) ? v.reference_id : "";


    //document.getElementById('divMeta.text_tracks').innerHTML = "<button>" +  v.toString() + "</button>";


    $ACTIVE_TRACKS = v.text_tracks != null ? v.text_tracks : "";
    var arr = v.text_tracks != null ? v.text_tracks : "";
    document.getElementById('divMeta.text_tracks').innerHTML = "";

    if (arr.length > 0) {
        var tableTmpl = "<table class=\"tg\"><thead><tr><th class=\"tg-uqo3\">LABEL</th><th class=\"tg-uqo3\">LANGUAGE</th> <th class=\"tg-uqo3\">TYPE</th> <th class=\"tg-uqo3\">DELETE</th> </tr> </thead><tbody id=\"divMeta.text_tracks_table\"></tbody></table>";
        document.getElementById('divMeta.text_tracks').innerHTML = tableTmpl;
        for (var x = 0; x < arr.length; x++) {
            var cur = arr[x];
            var defTrack = "";
            if (cur["default"]) {
                defTrack = "default_track";
            }
            document.getElementById('divMeta.text_tracks_table').innerHTML = document.getElementById('divMeta.text_tracks_table').innerHTML + "<tr class='texttrackrow "+defTrack+"'><td class=\"tg-baqh \">" + cur.label + "</td><td  class=\"tg-baqh\">" + cur.srclang + "</td><td  class=\"tg-baqh\">" + cur.kind + "</td><td class=\"tg-baqh delete_button\" onClick=\"deleteTrack('" + cur.id + "','" + v.id + "')\">X</td> </tr>";
        }
    }
}
function showMetaDataByVideoID(idx) {

    window.selectedVideoId = false;

    $("tr.select").removeClass("select");
    $("#tbData>tr:eq(" + idx + ")").addClass("select");


    $.ajax({
        url: getVideoAPIURL(idx),
        type: 'GET',
        contentType: 'application/json; charset=utf-8',
        success: function (response)
        {
            var v = response.items[0];

            // Populate the metadata panel
            document.getElementById('divMeta.name').innerHTML = v.name;
            document.getElementById('divMeta.thumbnailURL').src = (v.images != null && v.images.thumbnail != null && v.images.thumbnail.src != null) ? v.images.thumbnail.src : "";
            document.getElementById('divMeta.videoStillURL').src = (v.images != null && v.images.poster != null && v.images.poster.src != null) ? v.images.poster.src : "";
            document.getElementById('divMeta.previewDiv').value = v.id;
            var modDate = new Date(v.updated_at);
            document.getElementById('divMeta.lastModifiedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();

            //v.length is the running time of the video in ms
            var sec = String((Math.floor(v.duration * .001)) % 60); //The number of seconds not part of a whole minute
            sec.length < 2 ? sec = sec + "0" : sec;  //Make sure  the one's place 0 is included.
            document.getElementById('divMeta.length').innerHTML = Math.floor(v.duration / 60000) + ":" + sec;

            document.getElementById('divMeta.id').innerHTML = v.id;
            document.getElementById('divMeta.shortDescription').innerHTML = "<pre>" + (v.description != null ? v.description : "") + "</pre>";

            //Construct the tag section:
            var tagsObject = "";
            if ("" != v.tags) {
                var tags = v.tags.toString().split(',');
                for (var k = 0; k < tags.length; k++) {
                    if (k > 0) {
                        tagsObject += ', ';
                    }
                    tagsObject += '<a style="cursor:pointer;color:blue;text-decoration:underline"' +
                        'onclick="searchVal=\'' + tags[k].replace(/\'/gi, "\\\'") + '\';Load(findByTag(\'' + tags[k] + '\'))" >' + tags[k] + '</a>';
                }
            }
            document.getElementById('divMeta.tags').innerHTML = tagsObject;

            //if there's no link text use the linkURL as the text
            var linkText = (v.link != null && "" != v.link.text && null != v.link.text) ? v.link.text : (v.link != null && v.link.url != null) ? v.link.url : "";
            var linkURL = (v.link != null && v.link.url != null) ? v.link.url : "";
            document.getElementById('divMeta.linkURL').innerHTML = linkText;

            document.getElementById('divMeta.linkURL').href = linkURL;
            document.getElementById('divMeta.linkText').innerHTML = linkText;
            document.getElementById('divMeta.economics').innerHTML = v.economics;

            modDate = new Date(v.published_at);
            document.getElementById('divMeta.publishedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();
            document.getElementById('divMeta.referenceId').innerHTML = (v.reference_id != null) ? v.reference_id : "";


            //document.getElementById('divMeta.text_tracks').innerHTML = "<button>" +  v.toString() + "</button>";


            $ACTIVE_TRACKS = v.text_tracks != null ? v.text_tracks : "";
            var arr = v.text_tracks != null ? v.text_tracks : "";
            document.getElementById('divMeta.text_tracks').innerHTML = "";

            if (arr.length > 0) {
                var tableTmpl = "<table class=\"tg\"><thead><tr><th class=\"tg-uqo3\">LABEL</th><th class=\"tg-uqo3\">LANGUAGE</th> <th class=\"tg-uqo3\">TYPE</th> <th class=\"tg-uqo3\">DELETE</th> </tr> </thead><tbody id=\"divMeta.text_tracks_table\"></tbody></table>";
                document.getElementById('divMeta.text_tracks').innerHTML = tableTmpl;
                for (var x = 0; x < arr.length; x++) {
                    var cur = arr[x];
                    var defTrack = "";
                    if (cur["default"]) {
                        defTrack = "default_track";
                    }
                    document.getElementById('divMeta.text_tracks_table').innerHTML = document.getElementById('divMeta.text_tracks_table').innerHTML + "<tr class='texttrackrow "+defTrack+"'><td class=\"tg-baqh \">" + cur.label + "</td><td  class=\"tg-baqh\">" + cur.srclang + "</td><td  class=\"tg-baqh\">" + cur.kind + "</td><td class=\"tg-baqh delete_button\" onClick=\"deleteTrack('" + cur.id + "','" + v.id + "')\">X</td> </tr>";
                }
            }
            $("#tdMeta").show();
        },
        error: function () {
            alert("Error");
        }
    });
}


function deleteTrack(trackid , videoID)
{
    loadStart();
    $.ajax({
        url: apiLocation + '.js',
        type: 'GET',
        data: { a: "remove_text_track", track : trackid , id : videoID } ,
        contentType: 'application/json; charset=utf-8',
        success: function (response)
        {
            window.selectedVideoId = videoID;
            Load(getAllVideosURL());
        },
        error: function () {
            window.selectedVideoId = videoID;
            loadEnd();
            alert("Error in track deletion");
        }
    });


}



//open an overlay and show the screen
function openBox(id) {
    $("#screen")
        .width($(document).width())
        .height($(document).height());

    $('#' + id)
        .css("left", ($(window).width() / 4))
        .css("top", ($(window).height() / 6))
        .draggable();
    //TODO: replace jquery ui with extjs for consistency

    if (!$.browser.msie) {
        $("#screen, #" + id).fadeIn("fast");
    } else {
        $("#screen, #" + id).show();
    }
}

//close an open overlay and hide the screen, if a form is passed, reset it.
function closeBox(id, form) {
    //Don't close the screen if another window is open
    var strSelect = '#' + id + (($("div.overlay:visible").length > 1) ? "" : ",#screen");
    if (!$.browser.msie) {
        $(strSelect).fadeOut("fast");
    } else {
        $(strSelect).hide();
    }
    if (null != form) {
        form.reset();
    }
}
CQ.Ext.brightcove = {};

CQ.Ext.brightcove.economics = new CQ.Ext.data.JsonStore({
    fields: ['value', 'text'],
    data: [
        {value: 'AD_SUPPORTED', text: 'Ad Enabled'},
        {value: 'FREE', text: 'No Ads'}
    ]
});


function syncDB()
{
    syncStart();
    var url = window.location.origin + "/bin/brightcove/dataload";
    data = "account_id="+$("#selAccount").val();
    $.ajax({
        type: 'GET',
        url: url,
        data: data,
        async: true,
        success: function (data)
        {
            syncEnd();
            data = $.parseJSON(data);
        }
    });
}


function uploadPoster()
{
    //var url = window.location.origin + "/bin/brightcove/dataload";

    var form = new CQ.Ext.form.FormPanel({
        baseCls: 'x-plain',
        labelWidth: 130,
        url: apiLocation+
        '.js',
        method: "POST",
        standardSubmit: false,
        defaults: {
            xtype: 'textfield'
        },

        items: [{
            xtype: 'hidden',
            id: 'limit',
            name: 'limit',
            value: paging.size
        },{
            xtype: 'hidden',
            id: 'start',
            name: 'start',
            value:paging.generic
        },{
            xtype: 'hidden',
            id: 'id',
            name: 'id',
            value: document.getElementById('divMeta.id').innerHTML,
            width: "100%"
        },{
            xtype: 'hidden',
            id: 'a',
            name: 'a',
            value: 'upload_image',
            width: "100%"
        }, {
            xtype: 'hidden',
            fieldLabel: 'Account ID:',
            value: $("#selAccount").val(),
            name:"account_id",
            width: "100%"
        },{
            xtype: 'hidden',
            fieldLabel: 'Video ID:',
            value: document.getElementById('divMeta.id').innerHTML,
            disabled: true,
            width: "100%"
        },{
            xtype: 'textfield',
            fieldLabel: 'URL Source:',
            allowBlank: false,
            width: "100%",
            name:"poster_source"
        }]}),

        u = new CQ.Ext.Window({
            title: 'Upload Poster',
            collapsible: true,
            maximizable: true,
            width: 750,
            height: 500,
            minWidth: 300,
            minHeight: 200,
            bodyStyle: 'padding:5px;',
            buttonAlign: 'center',
            items: form,
            buttons: [{
                text: 'Send',
                handler: function (btn, evt)
                {
                    var formobj = form.getForm();
                    if (formobj.isValid())
                    {
                        loadStart();
                        formobj.submit({
                            success: function (form, action) {
                                u.destroy();
                                window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                                Load(getAllVideosURL());
                            },
                            failure: function (form, action) {
                                window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                                CQ.Ext.Msg.alert('Submission Failed', action.result && action.result.msg != "" ? action.result.msg : 'ERROR: Please try again.');
                                Load(getAllVideosURL());
                            }
                        });
                    }
                    else alert('Invalid form');
                }
            }, {
                text: 'Cancel',
                handler: function (btn, evt) {
                    u.destroy()
                }
            }]
        });

    u.setPosition(10, 10);
    u.show();





}




function uploadThumbnail()
{
    //var url = window.location.origin + "/bin/brightcove/dataload";

    var form = new CQ.Ext.form.FormPanel({
        baseCls: 'x-plain',
        labelWidth: 130,
        url: apiLocation+
        '.js',
        method: "POST",
        standardSubmit: false,
        defaults: {
            xtype: 'textfield'
        },

        items: [{
            xtype: 'hidden',
            id: 'limit',
            name: 'limit',
            value: paging.size
        },{
            xtype: 'hidden',
            id: 'start',
            name: 'start',
            value:paging.generic
        },{
            xtype: 'hidden',
            id: 'id',
            name: 'id',
            value: document.getElementById('divMeta.id').innerHTML,
            width: "100%"
        },{
            xtype: 'hidden',
            id: 'a',
            name: 'a',
            value: 'upload_image',
            width: "100%"
        }, {
            xtype: 'hidden',
            fieldLabel: 'Account ID:',
            value: $("#selAccount").val(),
            name:"account_id",
            width: "100%"
        },{
            xtype: 'hidden',
            fieldLabel: 'Video ID:',
            value: document.getElementById('divMeta.id').innerHTML,
            disabled: true,
            width: "100%"
        },{
            xtype: 'textfield',
            fieldLabel: 'URL Source:',
            allowBlank: false,
            width: "100%",
            name:"thumbnail_source"
        }
        ]}),
        o = new CQ.Ext.Window({
            title: 'Upload Thumbnail',
            collapsible: true,
            maximizable: true,
            width: 750,
            height: 500,
            minWidth: 300,
            minHeight: 200,
            bodyStyle: 'padding:5px;',
            buttonAlign: 'center',
            items: form,
            buttons: [{
                text: 'Send',
                handler: function (btn, evt)
                {
                    var formobj = form.getForm();
                    if (formobj.isValid())
                    {
                        loadStart();
                        formobj.submit({
                            success: function (form, action) {
                                o.destroy();
                                window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                                Load(getAllVideosURL());
                            },
                            failure: function (form, action) {
                                window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                                CQ.Ext.Msg.alert('Submission Failed', action.result && action.result.msg != "" ? action.result.msg : 'ERROR: Please try again.');
                                Load(getAllVideosURL());
                            }
                        });
                    }
                    else alert('Invalid form');
                }
            }, {
                text: 'Cancel',
                handler: function (btn, evt) {
                    o.destroy()
                }
            }]
        });

    o.setPosition(10, 10);
    o.show();





}

function uploadtrack()
{var default_tracks = $("#divMeta\\.text_tracks_table tr.default_track");


    var form = new CQ.Ext.form.FormPanel({
            baseCls: 'x-plain',
            labelWidth: 130,
            url: apiLocation+
            '.js',
            method: "POST",
            standardSubmit: false,
            defaults: {
                xtype: 'textfield'
            },

            items: [{
                xtype: 'hidden',
                id: 'limit',
                name: 'limit',
                value: paging.size
            },{
                xtype: 'hidden',
                id: 'start',
                name: 'start',
                value:paging.generic
            },{
                xtype: 'hidden',
                id: 'id',
                name: 'id',
                value: document.getElementById('divMeta.id').innerHTML,
                width: "100%"
            },{
                xtype: 'hidden',
                id: 'a',
                name: 'a',
                value: 'upload_text_track',
                width: "100%"
            }, {
                xtype: 'hidden',
                fieldLabel: 'Account ID:',
                value: $("#selAccount").val(),
                name:"account_id",
                width: "100%"
            },{
                xtype: 'hidden',
                fieldLabel: 'Video ID:',
                value: document.getElementById('divMeta.id').innerHTML,
                disabled: true,
                width: "100%"
            },{
                xtype: "selection",
                fieldLabel: "Language:",
                name: "track_lang",
                type: "select",
                allowBlank: false,
                options: [
                    {"value":"ar","text":"ar"},
                    {"value":"ar-AE","text":"ar-AE"},
                    {"value":"ar-BH","text":"ar-BH"},
                    {"value":"ar-DZ","text":"ar-DZ"},
                    {"value":"ar-EG","text":"ar-EG"},
                    {"value":"ar-IQ","text":"ar-IQ"},
                    {"value":"ar-JO","text":"ar-JO"},
                    {"value":"ar-KW","text":"ar-KW"},
                    {"value":"ar-LB","text":"ar-LB"},
                    {"value":"ar-LY","text":"ar-LY"},
                    {"value":"ar-MA","text":"ar-MA"},
                    {"value":"ar-OM","text":"ar-OM"},
                    {"value":"ar-QA","text":"ar-QA"},
                    {"value":"ar-SA","text":"ar-SA"},
                    {"value":"ar-SD","text":"ar-SD"},
                    {"value":"ar-SY","text":"ar-SY"},
                    {"value":"ar-TN","text":"ar-TN"},
                    {"value":"ar-YE","text":"ar-YE"},
                    {"value":"be","text":"be"},
                    {"value":"be-BY","text":"be-BY"},
                    {"value":"bg","text":"bg"},
                    {"value":"bg-BG","text":"bg-BG"},
                    {"value":"ca","text":"ca"},
                    {"value":"ca-ES","text":"ca-ES"},
                    {"value":"cs","text":"cs"},
                    {"value":"cs-CZ","text":"cs-CZ"},
                    {"value":"da","text":"da"},
                    {"value":"da-DK","text":"da-DK"},
                    {"value":"de","text":"de"},
                    {"value":"de-AT","text":"de-AT"},
                    {"value":"de-CH","text":"de-CH"},
                    {"value":"de-DE","text":"de-DE"},
                    {"value":"de-LU","text":"de-LU"},
                    {"value":"el","text":"el"},
                    {"value":"el-CY","text":"el-CY"},
                    {"value":"el-GR","text":"el-GR"},
                    {"value":"en","text":"en"},
                    {"value":"en-AU","text":"en-AU"},
                    {"value":"en-CA","text":"en-CA"},
                    {"value":"en-GB","text":"en-GB"},
                    {"value":"en-IE","text":"en-IE"},
                    {"value":"en-IN","text":"en-IN"},
                    {"value":"en-MT","text":"en-MT"},
                    {"value":"en-NZ","text":"en-NZ"},
                    {"value":"en-PH","text":"en-PH"},
                    {"value":"en-SG","text":"en-SG"},
                    {"value":"en-US","text":"en-US"},
                    {"value":"en-ZA","text":"en-ZA"},
                    {"value":"es","text":"es"},
                    {"value":"es-AR","text":"es-AR"},
                    {"value":"es-BO","text":"es-BO"},
                    {"value":"es-CL","text":"es-CL"},
                    {"value":"es-CO","text":"es-CO"},
                    {"value":"es-CR","text":"es-CR"},
                    {"value":"es-DO","text":"es-DO"},
                    {"value":"es-EC","text":"es-EC"},
                    {"value":"es-ES","text":"es-ES"},
                    {"value":"es-GT","text":"es-GT"},
                    {"value":"es-HN","text":"es-HN"},
                    {"value":"es-MX","text":"es-MX"},
                    {"value":"es-NI","text":"es-NI"},
                    {"value":"es-PA","text":"es-PA"},
                    {"value":"es-PE","text":"es-PE"},
                    {"value":"es-PR","text":"es-PR"},
                    {"value":"es-PY","text":"es-PY"},
                    {"value":"es-SV","text":"es-SV"},
                    {"value":"es-US","text":"es-US"},
                    {"value":"es-UY","text":"es-UY"},
                    {"value":"es-VE","text":"es-VE"},
                    {"value":"et","text":"et"},
                    {"value":"et-EE","text":"et-EE"},
                    {"value":"fi","text":"fi"},
                    {"value":"fi-FI","text":"fi-FI"},
                    {"value":"fr","text":"fr"},
                    {"value":"fr-BE","text":"fr-BE"},
                    {"value":"fr-CA","text":"fr-CA"},
                    {"value":"fr-CH","text":"fr-CH"},
                    {"value":"fr-FR","text":"fr-FR"},
                    {"value":"fr-LU","text":"fr-LU"},
                    {"value":"ga","text":"ga"},
                    {"value":"ga-IE","text":"ga-IE"},
                    {"value":"he","text":"he"},
                    {"value":"he-IL","text":"he-IL"},
                    {"value":"hi-IN","text":"hi-IN"},
                    {"value":"hr","text":"hr"},
                    {"value":"hr-HR","text":"hr-HR"},
                    {"value":"hu","text":"hu"},
                    {"value":"hu-HU","text":"hu-HU"},
                    {"value":"id","text":"id"},
                    {"value":"id-ID","text":"id-ID"},
                    {"value":"is","text":"is"},
                    {"value":"is-IS","text":"is-IS"},
                    {"value":"it","text":"it"},
                    {"value":"it-CH","text":"it-CH"},
                    {"value":"it-IT","text":"it-IT"},
                    {"value":"ja","text":"ja"},
                    {"value":"ja-JP","text":"ja-JP"},
                    {"value":"ja-JP-u-ca-japanese-x-lvariant-JP","text":"ja-JP-u-ca-japanese-x-lvariant-JP"},
                    {"value":"ko","text":"ko"},
                    {"value":"ko-KR","text":"ko-KR"},
                    {"value":"lt","text":"lt"},
                    {"value":"lt-LT","text":"lt-LT"},
                    {"value":"lv","text":"lv"},
                    {"value":"lv-LV","text":"lv-LV"},
                    {"value":"mk","text":"mk"},
                    {"value":"mk-MK","text":"mk-MK"},
                    {"value":"ms","text":"ms"},
                    {"value":"ms-MY","text":"ms-MY"},
                    {"value":"mt","text":"mt"},
                    {"value":"mt-MT","text":"mt-MT"},
                    {"value":"nl","text":"nl"},
                    {"value":"nl-BE","text":"nl-BE"},
                    {"value":"nl-NL","text":"nl-NL"},
                    {"value":"nn-NO","text":"nn-NO"},
                    {"value":"no","text":"no"},
                    {"value":"no-NO","text":"no-NO"},
                    {"value":"pl","text":"pl"},
                    {"value":"pl-PL","text":"pl-PL"},
                    {"value":"pt","text":"pt"},
                    {"value":"pt-BR","text":"pt-BR"},
                    {"value":"pt-PT","text":"pt-PT"},
                    {"value":"ro","text":"ro"},
                    {"value":"ro-RO","text":"ro-RO"},
                    {"value":"ru","text":"ru"},
                    {"value":"ru-RU","text":"ru-RU"},
                    {"value":"sk","text":"sk"},
                    {"value":"sk-SK","text":"sk-SK"},
                    {"value":"sl","text":"sl"},
                    {"value":"sl-SI","text":"sl-SI"},
                    {"value":"sq","text":"sq"},
                    {"value":"sq-AL","text":"sq-AL"},
                    {"value":"sr","text":"sr"},
                    {"value":"sr-BA","text":"sr-BA"},
                    {"value":"sr-CS","text":"sr-CS"},
                    {"value":"sr-La","text":"sr-La"},
                    {"value":"tn","text":"tn"},
                    {"value":"sr-La","text":"sr-La"},
                    {"value":"tn-BA","text":"tn-BA"},
                    {"value":"sr-La","text":"sr-La"},
                    {"value":"tn-ME","text":"tn-ME"},
                    {"value":"sr-La","text":"sr-La"},
                    {"value":"tn-RS","text":"tn-RS"},
                    {"value":"sr-ME","text":"sr-ME"},
                    {"value":"sr-RS","text":"sr-RS"},
                    {"value":"sv","text":"sv"},
                    {"value":"sv-SE","text":"sv-SE"},
                    {"value":"th","text":"th"},
                    {"value":"th-TH","text":"th-TH"},
                    {"value":"th-TH-u-nu-thai-x-lvariant-TH","text":"th-TH-u-nu-thai-x-lvariant-TH"},
                    {"value":"ar","text":"ar"},
                    {"value":"ia","text":"ia"},
                    {"value":"nt-TH","text":"nt-TH"},
                    {"value":"tr","text":"tr"},
                    {"value":"tr-TR","text":"tr-TR"},
                    {"value":"uk","text":"uk"},
                    {"value":"uk-UA","text":"uk-UA"},
                    {"value":"vi","text":"vi"},
                    {"value":"vi-VN","text":"vi-VN"},
                    {"value":"zh","text":"zh"},
                    {"value":"zh-CN","text":"zh-CN"},
                    {"value":"zh-HK","text":"zh-HK"},
                    {"value":"zh-SG","text":"zh-SG"},
                    {"value":"zh-TW","text":"zh-TW"}
                ]
            },{
                xtype: 'textfield',
                fieldLabel: 'Label:',
                allowBlank: true,
                name:"track_label",
                width: "100%"
            },{
                xtype: 'textfield',
                fieldLabel: 'MIME type:',
                allowBlank: true,
                name:"track_mime_type",
                value:"text/webvtt",
                disabled: true,
                width: "100%"
            },{
                    xtype: "selection",
                    fieldLabel: "Kind:",
                    name: "track_kind",
                    type: "select",
                    value:"captions",
                    options: [
                        {
                            "value": "subtitles",
                            "text": "Subtitles"
                        },
                        {
                            "value": "descriptions",
                            "text": "Description"
                        },
                        {
                            "value": "chapters",
                            "text": "Chapters"
                        },
                        {
                            "value": "metadata",
                            "text": "Metadata"
                        },{
                            "value": "captions",
                            "text": "Captions"
                        }]
            }
            //     {
            //     xtype: 'textfield',
            //     fieldLabel: 'URL Source:',
            //     fieldDescription: 'Note: Non text/vtt sources will result in a submission error',
            //     allowBlank: false,
            //     width: "100%",
            //     name:"track_source"
            // },
                ,{
                xtype: 'selection',
                fieldLabel: 'Default Track:',
                fieldDescription: (default_tracks.length > 0) ? '<b style="color:red;">NOTE: Only a single text track should be set as the default</b>' :'Set new uploaded track as the default text track?',
                inputValue: true,
                name: 'track_default',
                type:'checkbox'
            },{
                xtype: "dialogfieldset",
                collapsible: false,
                collapsed: false,
                fieldLabel: 'Source',
                fieldDescription: 'Note: Please choose only ONE text track source path',
                items: [
                    {
                        xtype: "dialogfieldset",
                        collapsible: false,
                        collapsed: false,
                        items: [
                            {
                                xtype: 'fileuploadfield',
                                id: 'filePath',
                                fieldDescription: 'Upload a text track from the file system (.vtt)',
                                fieldLabel: 'Upload File',
                                name: 'track_filepath',
                                buttonText: 'Browse',
                                width: "100%",
                                allowBlank: true
                            }
                        ]
                    },
                    {
                        xtype: 'label',
                        text: 'OR',
                        margins: '0 0 0 10'
                    },
                    {
                        xtype: "dialogfieldset",
                        collapsible: false,
                        collapsed: false,
                        items: [
                            {
                                xtype: 'textfield',
                                fieldLabel: 'From Source URL',
                                fieldDescription: 'Upload from website (warning: Non text/vtt sources will result in a submission error.)',
                                allowBlank: true,
                                width: "100%",
                                name:"track_source"
                            }
                        ]
                    }
                ]
            }
            ]}),

        z = new CQ.Ext.Window({
            title: 'Upload new text track',
            collapsible: true,
            maximizable: true,
            width: 750,
            height: 500,
            minWidth: 300,
            minHeight: 200,
            bodyStyle: 'padding:5px;',
            buttonAlign: 'center',
            items: form,
            buttons: [{
                text: 'Send',
                handler: function (btn, evt)
                {
                    var formobj = form.getForm();

                    if (formobj.isValid())
                    {


                        //TODO: Find animation midground to make modal only loading thing
                        loadStart();
                        formobj.submit({
                            success: function (form, action)
                            {
                                //FIELDS WERE VALID
                                z.destroy();
                                window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                                Load(getAllVideosURL());
                            },
                            failure: function (form, action) {
                                CQ.Ext.Msg.alert('Track Submission Failed', action.result && action.result.msg != "" ? action.result.msg : 'ERROR: Please verify your connection and the text track source type.');
                                window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                                Load(getAllVideosURL());
                            }
                        });
                    }
                    else
                    {
                        alert('Invalid form');
                    }
                }
            }, {
                text: 'Cancel',
                handler: function (btn, evt) {
                    z.destroy();
                }
            }]
        });

    z.setPosition(10, 10);
    z.show();





}

function extMetaEdit() {

    var v = oCurrentVideoList[$("tr.select").attr("id")],
        modDate = new Date(v.updated_at),
        tags = ((v.tags != null) ? v.tags : new Array()),
        sec = String((Math.floor(v.duration * .001)) % 60); //The number of seconds not part of a whole minute
    sec.length < 2 ? sec = sec + "0" : sec;  //Make sure  the one's place 0 is included.


         var combo = new CQ.Ext.form.ComboBox({
            store: CQ.Ext.brightcove.economics,
            fieldLabel: 'Economics:',
            displayField: 'text',
            valueField: 'value',
            mode: 'local',
            forceSelection: true,
            editable: false,
            width: "100%",
            hiddenName: "economics",
            id: "economics",
            triggerAction: "all",
            value: (v.economics != null) ? v.economics : ""
        }),

        form = new CQ.Ext.form.FormPanel({
            baseCls: 'x-plain',
            labelWidth: 130,
            url: apiLocation,
            method: "POST",
            standardSubmit: false,
            defaults: {
                xtype: 'textfield'
            },

            items: [{
                xtype: 'hidden',
                fieldLabel: 'Account ID:',
                value: $("#selAccount").val(),
                name:"account_id",
                width: "100%"
            },{
                xtype: 'textfield',
                fieldLabel: 'Title:',
                id: 'name',
                name: 'name',
                value: v.name,
                width: "100%",
                allowBlank: false
            }, {
                xtype: 'textfield',
                fieldLabel: 'Last Updated:',
                value: (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear(),
                disabled: true,
                width: "100%"
            }, {
                xtype: 'textfield',
                fieldLabel: 'Date Published:',
                value: (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear(),
                disabled: true,
                width: "100%"
            }, {
                xtype: 'textfield',
                fieldLabel: 'Duration:',
                value: Math.floor(v.duration / 60000) + ":" + sec,
                disabled: true,
                width: "100%"
            }, {
                xtype: 'textfield',
                fieldLabel: 'Video ID:',
                value: v.id,
                disabled: true,
                width: "100%"
            }, {
                xtype: 'textfield',
                fieldLabel: 'Short Description:',
                id: 'shortDescription',
                name: 'shortDescription',
                value: v.description,
                width: "100%",
                allowBlank: false
            }, {
                xtype: 'textfield',
                fieldLabel: 'Link to Related Item:',
                id: 'linkURL',
                name: 'linkURL',
                value: (v.link != null && v.link.url != null) ? v.link.url : "",
                width: "100%"
            }, {
                xtype: 'textfield',
                fieldLabel: 'Text for Related Item:',
                id: 'linkText',
                name: 'linkText',
                value: (v.link != null && v.link.text != null) ? v.link.text : "",
                width: "100%"
            }, {
                xtype: 'tags',
                fieldLabel: 'Tags:',
                id: 'tags',
                name: 'tags',
                value: tags,
                width: "100%"
            }, {
                xtype: 'textfield',
                fieldLabel: 'Reference ID:',
                id: 'referenceId',
                name: 'referenceId',
                value: (v.reference_id != null) ? v.reference_id : "",
                width: "100%"
            }, combo, {
                xtype: 'hidden',
                id: 'id',
                name: 'id',
                value: v.id,
                width: "100%"
            }, {
                xtype: 'hidden',
                id: 'a',
                name: 'a',
                value: 'update_video',
                width: "100%"
            }, {
                xtype: 'hidden',
                id: 'existingTags',
                name: 'existingTags',
                value: tags.join(),
                width: "100%"
            }]
        }),

        w = new CQ.Ext.Window({
            title: 'Update Video',
            collapsible: true,
            maximizable: true,
            width: 750,
            height: 500,
            minWidth: 300,
            minHeight: 200,
            bodyStyle: 'padding:5px;',
            buttonAlign: 'center',
            items: form,
            buttons: [{
                text: 'Send',
                handler: function (btn, evt)
                {
                    var formobj = form.getForm();
                    if (formobj.isValid()) {
                        loadStart();

                        formobj.submit({
                            success: function (form, action) {
                                w.destroy();
                                window.selectedVideoId = v.id;
                                Load(getAllVideosURL());
                            },
                            failure: function (form, action) {
                                window.selectedVideoId = v.id;
                                CQ.Ext.Msg.alert('Submission Failed', action.result && action.result.msg != "" ? action.result.msg : 'ERROR: Please try again.');
                                Load(getAllVideosURL());
                            }
                        });
                    }
                    else alert('Invalid form');
                }
            }, {
                text: 'Cancel',
                handler: function (btn, evt) {
                    w.destroy()
                }
            }]
        });

    w.setPosition(10, 10);
    w.show();

}

function metaEdit() {
    var v = oCurrentVideoList[$("tr.select").attr("id")],
        modDate = new Date().setTime(v.lastModifiedDate);

    document.getElementById('meta.name').value = v.name;
    document.getElementById('meta.lastModifiedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();

    //v.length is the running time of the video in ms
    var sec = String((Math.floor(v.length * .001)) % 60); //The number of seconds not part of a whole minute
    sec.length < 2 ? sec = sec + "0" : sec;  //Make sure  the one's place 0 is included.

    document.getElementById('meta.preview').value = document.getElementById('divMeta.previewDiv').value;
    document.getElementById('meta.length').innerHTML = Math.floor(v.length / 60000) + ":" + sec;
    document.getElementById('tdmeta.id').innerHTML = v.id;
    document.getElementById('meta.id').value = v.id;
    document.getElementById('meta.shortDescription').value = v.shortDescription;
    document.getElementById('meta.tags').value = (v.tags != null) ? v.tags : "";
    document.getElementById('meta.linkURL').value = (v.linkURL != null) ? v.linkURL : "";
    document.getElementById('meta.linkText').value = (v.linkText != null) ? v.linkText : "";
    document.getElementById('meta.economics').value = (v.economics != null) ? v.economics : "";

    modDate.setTime(v.publishedDate);
    document.getElementById('meta.publishedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();
    document.getElementById('meta.referenceId').value = (v.referenceId != null) ? v.referenceId : "";

    openBox('metaEditPop');
}

//Alerts the user that communication is happening, useful for accounts with lots of videos
//where loading times might be a little long.
function loadStart()
{
    if (!$.browser.msie) {
        $("#loading").slideDown("fast");
    } else {
        $("#loading").show();
    }
    $(".loading").show();
}

function loadEnd()
{
    $('html,body').scrollTop(0);
    $("#syncdbutton").css("color", "#333333");
    $("#syncdbutton").html('SYNC DATABASE');
    $("#syncdbutton").prop('disabled', false);
    if (!$.browser.msie) {
        $("#loading").slideUp("fast");
    } else {
        $("#loading").hide();
    }
    $(".loading").hide();
}

function syncStart()
{
    $("#syncdbutton").css("color", "#6D8CAE");
    $("#syncdbutton").html('LOADING SYNC');
    $("#syncdbutton").prop('disabled', true);
    if (!$.browser.msie)
    {
        $("#loading").slideDown("fast");
        $(".loading").show();
    }
    else
    {
        $("#loading").show();
    }

    $(".loadingMsg").hide();
    $(".syncingMsg").show();

}

function syncEnd() {
    $(".syncingMsg").fadeOut();
    loadEnd();
    $(".loadingMsg").hide();
    $(".loading").hide();
}



function createPlaylistBox() {
    var inputTags = document.getElementById('listTable').getElementsByTagName('input'),
        form = document.getElementById('createPlaylistForm'),
        table = document.getElementById("createPlstVideoTable"),
        idx = 1,
        l = inputTags.length;

    form.playlist.value = '';

    for (var i = 2; i < l; i++) {
        if (inputTags[i].checked) {
            $("#createPlstVideoTable").append(
                '<tr ><td>' + oCurrentVideoList[i - 2].name +
                '</td><td>' + oCurrentVideoList[i - 2].id + '</td></tr>'
            );

            if (1 != idx) {
                form.playlist.value += ',';
            }
            form.playlist.value += oCurrentVideoList[i - 2].id;
            idx++;
        }
    }
    if (1 == idx) {
        alert("Cannot Create Empty Playlist, Please Select Some Videos");
        return;
    }
    openBox("createPlaylistDiv");
}

/*show a preview of the selected video
 * this works by embedding an iframe into an existing hidden div.
 * the iframe opens the brightcove player and passes the requested videoId.
 *In console 1, click players, get publishing code.  Copy the Player URL
 * and assign it to the variable previewPlayerLoc at the top of this document.
 * In the publishing module click get code and select Player URL.
 */
function doPreview(id) {
    document.getElementById('playerTitle').innerHTML = '<center>' + document.getElementById('divMeta.name').innerHTML + '</center>';
    var preview = document.createElement('iframe');
    //if ($("a#allVideos").parent("li").attr("class").indexOf("active") != -1){

    // including both query parameters for backwards compatibility.
    preview.setAttribute('src', brc_admin.previewPlayerLoc + id);
    preview.setAttribute('width', 480);
    preview.setAttribute('height', 270);
    /*} else {
     preview.setAttribute("src", previewPlayerListLoc+"?bctid="+id);
     preview.setAttribute("width", 960);
     preview.setAttribute("height", 445);
     }*/
    preview.setAttribute('frameborder', 0);
    preview.setAttribute('scrolling', 'no');
    preview.setAttribute('id', 'previewPlayer');
    document.getElementById('playerDiv').appendChild(preview);

    //This div has a close button, more content can be added  here below the player.  to add content above the player add it to the
    //playerDiv in default.html
    $('#playerDiv').append('<div id="previewClose" style="background-color:#fff;color:#5F9CE3;cursor:pointer; text-transform:uppercase; font-weight:bold;"\
    onclick="stopPreview()"><br/><center>Close Preview</center></div>');
    openBox('playerDiv');

}

/**
 could probably improve preview performance by loading the preview player with no video, then on doPreview using the playerapi to load the video
 that way player is kepy resident which cuts down on loading time.
 **/

function changeImage(id) {
    $("#uploadImageDiv #videoidthumb").val(id);
    openBox('uploadImageDiv');


}
function changeVideoImage(id) {
    $("#uploadVideoImageDiv #videoidthumb").val(id);
    openBox('uploadVideoImageDiv');


}
//before closing the player window, remove the created elements, otherwise they would persist into another preview window.
function stopPreview() {
    document.getElementById("playerDiv").removeChild(document.getElementById("previewPlayer"));
    document.getElementById("playerDiv").removeChild(document.getElementById("previewClose"));
    closeBox('playerDiv');
}

//type should be playlists or videos
function doPageList(total, type) {
    if (total > paging.size) {
        var numOpt = Math.ceil(total / paging.size);
        var select = document.getElementsByName("selPageN");
        var options = "";
        for (var i = 0; i < numOpt; i++) {
            options += '<option style="width:100%" id="' + i + '">';
            if (paging.generic == i) {
                num = (numOpt - 1 == i) ? (total - i * paging.size) : paging.size;
                document.getElementById('divVideoCount').innerHTML = num + ' ' + type + ' (of ' + total + ')';
            }
            if (numOpt - 1 == i) {
                options += 'Page ' + (i+1) + ' (' + type + ' ' + (i * paging.size + 1) + ' to ' + total + ' )</option>';
            } else {
                options += 'Page ' + (i+1) + ' (' + type + ' ' + (i * paging.size + 1) + ' to ' + ((i + 1) * paging.size) + ' )</option>';
            }
        }
        //remove previous options, add the new ones and select the current page in the option list
        $("select[name=selPageN]").empty().append(options).children("[id=" + paging.generic/paging.size + "]").each(function () {
            //need to try/catch for IE6
            try {
                this.selected = true;
            } catch (e) {
            }
        });
        $("div[name=pageDiv]").show();
        document.getElementById('tdOne').appendChild(document.getElementById('searchDiv'));
    } else {
        document.getElementById('divVideoCount').innerHTML = total + ' ' + type + ' (of ' + total + ' )';
        $("div[name=pageDiv]").hide();
        //If there's no page selector, move the search bar down so it doesn't stick out ofplace
        document.getElementById('tdTwo').appendChild(document.getElementById('searchDiv'));
    }
}

function changePage(num) {
    paging.generic = num * paging.size;
    Load(paging.currentFunction());
}

function checkCheck() {
    var count = 1;
    var inputTags = document.getElementById('listTable').getElementsByTagName('input');
    var selChek = document.getElementById('checkToggle');
    var l = inputTags.length
    for (var i = 2; i < l; i++) {
        if (true == inputTags[i].checked) {
            count++;
        } else if ((i - count) > 1) {//If one checkbox was skipped, then the total has to be < l, so uncheck selChek  and return.
            selChek.checked = false;
            return;
        }
    }
    if (selChek.checked == true && count < l) {
        selChek.checked = false;
    } else if (false == selChek.checked && count >= l - 1) {
        selChek.checked = true;
    }
}

function toggleSelect(check) {
    if (check.checked) {
        checkAll();
    } else {
        checkNone();
    }
}

function checkAll() {
    var inputTags = document.getElementById('listTable').getElementsByTagName('input');
    var l = inputTags.length;
    for (var i = 2; i < l; i++) {
        inputTags[i].checked = true;
    }
}

function checkNone() {
    var inputTags = document.getElementById('listTable').getElementsByTagName('input');
    var l = inputTags.length;
    for (var i = 2; i < l; i++) {
        inputTags[i].checked = false;
    }
}

//for example write functions are disabled, so display this message:
function noWrite() {
    alert("In this demo, write methods have been disabled.");
}

function extFormUpload() {

    var form = new CQ.Ext.form.FormPanel({
        baseCls: 'x-plain',
        labelWidth: 130,
        url: apiLocation,
        method: "POST",
        standardSubmit: false,
        defaults: {
            xtype: 'textfield'
        },

        items: [{
            xtype: 'textfield',
            fieldLabel: 'Title:',
            id: 'name',
            name: 'name',
            width: "100%",
            allowBlank: false
        }, {
            xtype: 'textfield',
            fieldLabel: 'Short Description:',
            id: 'shortDescription',
            name: 'shortDescription',
            width: "100%",
            allowBlank: false
        }, {
            xtype: 'textfield',
            fieldLabel: 'Link to Related Item:',
            id: 'linkURL',
            name: 'linkURL',
            width: "100%"
        }, {
            xtype: 'textfield',
            fieldLabel: 'Text for Related Item:',
            id: 'linkText',
            name: 'linkText',
            width: "100%"
        }, {
            xtype: 'tags',
            fieldLabel: 'Tags:',
            id: 'tags',
            name: 'tags',
            width: "100%"
        }, {
            xtype: 'textfield',
            fieldLabel: 'Reference ID:',
            id: 'referenceId',
            name: 'referenceId',
            width: "100%"
        }, {
            xtype: 'textfield',
            fieldLabel: 'Long Description:',
            id: 'longDescription',
            name: 'longDescription',
            width: "100%"
        }, {
            xtype: "dialogfieldset",
            collapsible: false,
            collapsed: false,
            items: [
                {
                    xtype: 'textfield',
                    fieldLabel: 'Dynamic Ingest URL:',
                    id: 'filePath_Ingest',
                    name: 'filePath_Ingest',
                    width: "100%",
                    allowBlank: false
                }, {
                    xtype: "selection",
                    fieldLabel: "Dynamic Ingest Profile:",
                    name: "profile_Ingest",
                    type: "select",
                    allowBlank: false,
                    options: [
                        {
                            "value": "Express Standard",
                            "text": "Express Standard"
                        }, {
                            "value": "Live - HD",
                            "text": "Live - HD"
                        }, {
                            "value": "Live - Premium HD",
                            "text": "Live - Premium HD"
                        }, {
                            "value": "Live - Standard",
                            "text": "Live - Standard"
                        }, {
                            "value": "audio-only",
                            "text": "audio-only"
                        }, {
                            "value": "balanced-high-definition",
                            "text": "balanced-high-definition"
                        }, {
                            "value": "balanced-nextgen-player",
                            "text": "balanced-nextgen-player"
                        }, {
                            "value": "balanced-standard-definition",
                            "text": "balanced-standard-definition"
                        }, {
                            "value": "high-bandwidth-devices",
                            "text": "high-bandwidth-devices"
                        }, {
                            "value": "low-bandwidth-devices",
                            "text": "low-bandwidth-devices"
                        }, {
                            "value": "mp4-only",
                            "text": "mp4-only"
                        }, {
                            "value": "screencast",
                            "text": "screencast"
                        }, {
                            "value": "single-rendition",
                            "text": "single-rendition"
                        }
                    ]
                }
            ]
        }, {
            xtype: 'hidden',
            id: 'video',
            name: 'video',
            value: '',
            width: "100%"
        }, {
            xtype: 'hidden',
            id: 'a',
            name: 'a',
            value: 'create_video',
            width: "100%"
        },{
            xtype: 'hidden',
            fieldLabel: 'Account ID:',
            value: $("#selAccount").val(),
            name:"account_id",
            width: "100%"
        }]
    });

    var w = new CQ.Ext.Window({
        title: 'Compose message',
        collapsible: true,
        maximizable: true,
        width: 750,
        height: 500,
        minWidth: 300,
        minHeight: 200,
        bodyStyle: 'padding:5px;',
        buttonAlign: 'center',
        items: form,
        buttons: [{
            text: 'Send',
            handler: function (btn, evt) {
                var formobj = form.getForm();
                if (formobj.isValid()) {
                    var formel = document.getElementById(formobj.getEl().id);
                    buildJSONRequest(formel);
                    //Ext.getCmp('form').getForm().submit();
                    formobj.submit({
                        success: function (form, action) {
                            w.destroy();
                            loadStart();
                            Load(getAllVideosURL());
                        },
                        failure: function (form, action) {
                            CQ.Ext.Msg.alert('Submission Failed', action.result && action.result.msg != "" ? action.result.msg : 'ERROR: Please try again.');
                            loadStart();
                            Load(getAllVideosURL());
                        }
                    });
                    //w.destroy();
                }
                else alert('Invalid form');
            }
        }, {
            text: 'Cancel',
            handler: function (btn, evt) {
                w.destroy()
            }
        }]
    });
    w.setPosition(10, 10);
    w.show();

}
