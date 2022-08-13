/*
 Adobe AEM Brightcove Connector

 Copyright (C) 2018 Coresecure Inc.

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

$( document ).ready(function() {
    console.log($("#selAccount").val());
    if(CQ.Ext!=null)
    {
        CQ.Ext.util.Cookies.set('brc_act', $("#selAccount").val());
    }
});




var $ACTIVE_TRACKS;
var $sortable;
var brc_admin = brc_admin || {};


//tUploadBar has the timer id for the upload progress bar, so it can be cancelled.  progressPos is used to keep track of the progress bar's position.
var tUploadBar,
    progressPos = 0,
    searchVal,
    paging;

//called once body loads.  sets the api location and loads all videos
$(function () {

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
        selectedVideos: [],
        curFolder: 0,
        generic: 0,     //used as a placeholder
        currentFunction: null,  //called when a page is changed
        size: 30     //Default Page Size [30], can be no larger than 100.
    };

    Load(getAllVideosURL());
    loadFolders();
    loadLabels();
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

    $('.butDiv').hide();

    $('body').on('click', function(event) {
        if (!$(event.target).hasClass('menuToggle')) {
            $('.menu').removeClass('open');
        }
        if ( !$(event.target).hasClass('autocomplete-item') ||
            !$(event.target).parent().hasClass('autocomplete-item') ) {
            $('.pml-dialog .autocomplete').empty();
        }
    });

    $('input#filter_clips').on('change', function(event) {
        if (event.currentTarget.checked) {
            // filter only clips
            $('#tbData tr').hide();
            $('#tbData tr.state-clip').show();
        } else {
            // display all
            $('#tbData tr').show();
        }
    });

    $('#tbData').on('click', '.edit-playlist', function(event) {
        editPlaylistHandler(event);
    })

    $('#searchDiv_pl').on('click', '.btn-create-playlist', function(event) {
        var $message =
            $('<p>Please enter a name for your playlist:</p>')
            .append($('<input class="input-playlist-name" required type="text" autofocus />'));
        showPopup('Create Playlist',
            $message.prop('outerHTML'),
            'Create',
            'Cancel',
            function(dialog) {
                // do something here
            },
            function(dialog) {
                // do nothing here
            })
    });

    $('.pml-dialog_content').on('click', '.playlist-listing li a', function(event) {
        $(event.target).parents('li').remove();
    })

    $('.pml-dialog_content').on('click', '.label-listing li a', function(event) {
        $(event.target).parents('li').remove();
    })

    $('#tbData').on('click', '.playlist-actions a', function(event) {
        event.preventDefault();
        var playlist = {
            name: $(event.target).parent().attr('data-playlist-name'),
            id: $(event.target).parent().attr('data-playlist')
        }
        console.log(playlist);
        showPopup('Delete Playlist',
                    'Are you sure you want to delete the playlist "' + playlist.name + '"?',
                    'Delete',
                    'Cancel',
                    function(dialog) {
                        var data = {
                            a: 'delete_playlist',
                            playlist: playlist.id
                        };
                        $.ajax({
                            type: 'GET',
                            url: '/bin/brightcove/api.js',
                            data: data,
                            async: true,
                            success: function (data)
                            {
                                // do something here?
                            }
                        });
                        dialog.hide();
                        Load(getAllPlaylistsURL());
                    },
                    function(dialog) {
                        // do nothing here
                    });
    });

    $('.pml-dialog').on('keyup', '.playlist-add-input input', function(event) {
        var query = $(event.target).val();
        var $parent = $(event.target).parents('.playlist-add-input');
        if (query) {
            // perform an AJAX video search
            var data = {
                a: 'search_videos',
                query: query,
                start: 0,
                limit: 3,
                callback: 'suggestVideosForPlaylist'
            };
            $.ajax({
                type: 'GET',
                url: '/bin/brightcove/api.js',
                data: data,
                async: true,
                success: function (data)
                {
                    // do something here?
                }
            });
        };
    });

    $('.pml-dialog').on('keyup', '.label-add-input input', function(event) {
        var query = $(event.target).val();
        var $parent = $(event.target).parents('.label-add-input');
        if (query) {
            // perform an AJAX video search
            var data = {
                a: 'list_labels',
                query: query,
                start: 0,
                limit: 3,
                callback: 'suggestLabelsForVideo'
            };
            $.ajax({
                type: 'GET',
                url: '/bin/brightcove/api.js',
                data: data,
                async: true,
                success: function (data)
                {
                    // do something here?
                }
            });
        };
    });

    $('.folder-selector .menu-options').on('click', 'li', function(event) {
        var folderId = $(event.target).data('folder-id');
        $.each(paging.selectedVideos, function (i, n) {
            console.log(folderId);
            if (folderId == 'none') {
                // we need to delete the videos from this folder
                // grab the folder ID from the checkbox
                var existingFolderId = $(n).data('folder-id');
                // now call the API
                var data = {
                    a: 'remove_video_from_folder',
                    folder: existingFolderId,
                    video: $(n).val()
                };
                $.ajax({
                    type: 'GET',
                    url: '/bin/brightcove/api.js',
                    data: data,
                    async: true,
                    success: function (data)
                    {
                        // do something here?
                    }
                });
            } else {
                // we are moving the videos to an actual folder
                var data = {
                    a: 'move_video_to_folder',
                    folder: folderId,
                    video: $(n).val()
                };
                $.ajax({
                    type: 'GET',
                    url: '/bin/brightcove/api.js',
                    data: data,
                    async: true,
                    success: function (data)
                    {
                        // do something here?
                    }
                });
            }
        });
        $('#fldr_list').change();
    })

    $('body').on('brc:checked', function(event) {
        var inputTags = document.getElementById('listTable').getElementsByTagName('input');
        paging.selectedVideos = [];
        var l = inputTags.length
        for (var i = 2; i < l; i++) {
            if (true == inputTags[i].checked) {
                paging.selectedVideos.push(inputTags[i]);
            }
        }
        $('.butDiv').toggle(paging.selectedVideos.length > 0);
    });

    $('body').on('click', '.variant', function(event) {
        event.preventDefault();
        var variantId = $(event.target).attr('data-variant-id');
        var videoId = $(event.target).attr('data-video-idx');
        var variant = oCurrentVideoList[videoId].variants[variantId];

        var content = "<div>";
        content += "<p><strong>Video Name:</strong><br />" + variant.language + "</p>";

        if (variant.description)
            content += "<p><strong>Description:</strong><br />" + variant.description + "</p>";

        if (variant.long_description)
            content += "<p><strong>Long Description:</strong><br />" + variant.long_description + "</p>";

        if (variant.custom_fields && JSON.stringify(variant.custom_fields) !== '{}') {
            content += "<p><strong>Custom Fields:</strong></p>";
            for (const prop in variant.custom_fields) {
                content += "<details open>"
                content += "<summary>" + prop + "</summary>";
                content += "<p>" + variant.custom_fields[prop] + "</p>";
                content += "</details>";
            }
        }

        content += "</div>";

        showPopup('Variant Details for Language: ' + variant.language,
                content,
                'OK',
                '',
                function(dialog) {
                    dialog.hide();
                });
    });

});

function showPopup(title, message, btnPrimaryText, btnSecondaryText, onSuccess, onCancel) {
    var $popup = $('.pml-dialog');
    $popup.find('.pml-dialog_header').text(title || 'Confirmation');
    $popup.find('.pml-dialog_content').html(message);
    $popup.find('.pml-dialog_footer .btn-primary')
        .text(btnPrimaryText)
        .off('click')
        .on('click', function(event) {
            event.preventDefault();
            if (onSuccess)
                onSuccess($popup);
        });
    if (btnSecondaryText) {
        $popup.find('.pml-dialog_footer .btn-secondary')
        .text(btnSecondaryText)
        .show()
        .off('click')
        .on('click', function(event) {
            event.preventDefault();
            if (onCancel)
                onCancel($popup);
            $popup.hide();
        });
    } else {
        $popup.find('.pml-dialog_footer .btn-secondary').hide();
    }

    $popup.show();
}

function suggestLabelsForVideo(data) {
    $('.pml-dialog .autocomplete').empty();
    if (data.items.length > 0) {
        $.each(data.items, function(i, n) {
            if (n.includes($('.label-add-input input').val())) {
                $('.pml-dialog .autocomplete')
                .append(
                    $('<li class="autocomplete-item" data-name="'+n+'">'+n+'<img src="/etc/designs/cs/brightcove/shared/img/add.svg" /></li>')
                    .click(function(event) {
                        // assign a click handler to add that to the playlist
                        var $item = $(event.target);

                        console.log($item);

                        // make sure we have the autocomplete item and not the SVG icon
                        if ($item.localName == 'img') {
                            $item = $item.parent();
                        }
                        var label = $item.attr('data-name');

                        // add the item to the list
                        $('.pml-dialog .label-listing')
                            .append($('<li><span><span class="handle"></span>'+label+'</span><a href="#"><img src="/etc/designs/cs/brightcove/shared/img/delete.svg" /></a></li>'));

                        // clear the search
                        $('.pml-dialog .label-add-input input').val();
                    })
                )
            }
        })
    }
}

function suggestVideosForPlaylist(data) {
    $('.pml-dialog .autocomplete').empty();
    if (data.items.length > 0) {
        $.each(data.items, function(i, n) {
            $('.pml-dialog .autocomplete')
            .append(
                $('<li class="autocomplete-item" data-name="'+n.name+'" data-id="'+n.id+'">'+n.name+'<img src="/etc/designs/cs/brightcove/shared/img/add.svg" /></li>')
                .click(function(event) {
                    // assign a click handler to add that to the playlist
                    var $item = $(event.target);

                    // make sure we have the autocomplete item and not the SVG icon
                    if ($item.localName == 'img') {
                        $item = $item.parent();
                    }
                    var videoId = $item.attr('data-id');
                    var videoName = $item.attr('data-name');

                    // add the item to the list
                    $('.pml-dialog .playlist-listing')
                        .append($('<li data-id="'+videoId+'"><span><span class="handle"></span>'+videoName+'</span><a href="#" data-video-id="'+videoId+'"><img src="/etc/designs/cs/brightcove/shared/img/delete.svg" /></a></li>'));

                    // clear the search
                    $('.pml-dialog .playlist-add-input input').val();
                })
            )
        })
    }
}

function moveVideoToFolder() {
    $('.folder-selector').toggleClass('open');
}

function getMoveVideoToFolderUrl(video_id, folder_id) {
    loadStart();
    return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=move_video_to_folder&callback=showAllVideosCallBack&folder=' + folder_id + '&video=' + video_id;
}

function getFolderListingUrl(id) {
    loadStart();
    var sort_by = $("#trHeader th.sortable").not("NONE").attr("data-sortby");
    var sort_type = $("#trHeader th.sortable").not("NONE").attr("data-sorttype");
    return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=get_videos_in_folder&callback=showAllVideosCallBack&folder=' + id + '&sort=' + sort_type + sort_by
                    + '&limit=' + paging.size + '&start=' + paging.generic;
}

function getLabelListingUrl(id) {
    loadStart();
    var sort_by = $("#trHeader th.sortable").not("NONE").attr("data-sortby");
    var sort_type = $("#trHeader th.sortable").not("NONE").attr("data-sorttype");
    return apiLocation +
                    '.js?account_id='+$("#selAccount").val()+'&a=get_videos_with_label&callback=showAllVideosCallBack&label=' + id + '&sort=' + sort_type + sort_by
                    + '&limit=' + paging.size + '&start=' + paging.generic;
}

function loadFolders() {
    // first set up the select change event
    $('#fldr_list').on('change', function() {
        // reset the label search
        $('#label_list').val('all');

        $('.butDiv').hide();
        var selected = $(this).val();
        if (selected == 'all') {
            Load(getAllVideosURL());
        } else {
            console.log('search videos by folder=' + selected);
            Load(getFolderListingUrl(selected));
        }
    });

    // now make the API call to load the folder options
    var data = {
        a: 'list_folders',
        callback: 'loadFolderCallback'
    };
    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: data,
        async: true,
        success: function (data)
        {
            // do something here?
        }
    });
}

function loadFolderCallback(data) {
    var $folder_select = $('#fldr_list');
    var $move_folder_list = $('.folder-selector .menu-options');
    $.each(data.items, function (i, n) {
        $folder_select
            .append($('<option>', { value : n.id }).text(n.name));
        $move_folder_list
            .append($('<li>', { class: 'menu-option', 'data-folder-id' : n.id })
                        .text(n.name)
                    );
    });
}

function loadLabels() {
    // first set up the select change event
    $('#label_list').on('change', function() {
        // reset the folder search
        $('#fldr_list').val('all');

        $('.butDiv').hide();
        var selected = $(this).val();
        if (selected == 'all') {
            Load(getAllVideosURL());
        } else if (selected == 'create') {
            var $message =
                $('<p>Label Name:</p>')
                .append($('<input class="input-label-name" type="text" autofocus />'));
            showPopup('Create New Label',
                $message.prop('outerHTML'),
                'Create',
                'Cancel',
                function(dialog) {

                    // do some basic validationå
                    var labelName = $('.input-label-name').val();
                    if (labelName == '' || !labelName.startsWith('/')) {
                        $('.input-label-name').addClass('error');
                    } else {
                        $('.input-label-name').removeClass('error');
                        // call the API here.
                        var data = {
                            a: 'create_label',
                            label: labelName
                        };
                        $.ajax({
                            type: 'GET',
                            url: '/bin/brightcove/api.js',
                            data: data,
                            async: true,
                            success: function (data)
                            {
                                // do something here?
                            }
                        });
                        dialog.hide();
                        location.reload();
                    }
                },
                function(dialog) {
                    // do nothing here
                    triggerLabelClick('all');
                })
        } else {
            console.log('search videos by label=' + selected);
            Load(getLabelListingUrl(selected));
        }
    });

    // now make the API call to load the folder options
    var data = {
        a: 'list_labels',
        callback: 'loadLabelCallback'
    };
    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: data,
        async: true,
        success: function (data)
        {
            // do something here?
        }
    });
}

function editLabels(event) {
    var data = {
        videoId: document.getElementById('divMeta.previewDiv').value,
        items: $(document.getElementById('divMeta.labels')).find('a').map(function() {
            return $(this).text()
          })
          .get()
    };
    console.log(data.items);
    var $search = $('<form autocomplete="off" class="label-add-input"><input type="text" placeholder="Search for a label to add" /><ul class="autocomplete list-unstyled"></ul></form>').prop('outerHTML');
    var $message = $('<ul class="label-listing list-unstyled" data-label-id="'+data.videoId+'" id="edit-labels-sortable">');
    data.items.forEach(function(item, index) {
        $message
            .append($('<li data-id="'+item+'"><span><span class="handle"></span>'+item+'</span><a href="#" data-label-id="'+item+'"><img src="/etc/designs/cs/brightcove/shared/img/delete.svg" /></a></li>'));
    });
    showPopup('Edit Labels', $search + $message.prop('outerHTML'), 'Update', 'Cancel', function(event) {
        var playlistData = {
            a: 'update_labels',
            labels: $('#edit-labels-sortable')
                        .find('li')
                        .map(function(item) { return $(this).text() }).get(),
            videoId: data.videoId
        };

        $.ajax({
            type: 'GET',
            url: '/bin/brightcove/api.js',
            data: $.param(playlistData, true),
            async: true,
            success: function (data)
            {
                location.reload();
            }
        });

        event.hide();
    }, null);
    var el = document.getElementById("edit-labels-sortable");
    $sortable = Sortable.create(el);
}

function callback(data) {
    // generic callback
}

function loadLabelCallback(data) {
    var $label_select = $('#label_list');
    $.each(data.items, function (i, n) {
        console.log(n);
        $label_select
            .append($('<option>', { value : n }).html(n));
    });
    $label_select.append($('<option>', { value : 'create' }).text('+ Create New Label'))
}

function triggerLabelClick(selected) {
    $('#label_list').val(selected).trigger('change');
}

function editPlaylistHandler(event) {
    event.preventDefault();
    var data = {
        a: 'list_videos_in_playlist',
        callback: 'editPlaylistListingCallback',
        query: $(event.target).attr('data-playlist-id')
    };

    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: data,
        async: true,
        success: function (data)
        {
            // do something here?
        }
    });

}

function editPlaylistListingCallback(data) {
    var $search = $('<form autocomplete="off" class="playlist-add-input"><input type="text" placeholder="Search for a video to add" /><ul class="autocomplete"></ul></form>').prop('outerHTML');
    var $message = $('<ul class="playlist-listing list-unstyled" data-playlist-id="'+data.playlist+'" id="edit-playlist-sortable">');
    data.items.forEach(function(item, index) {
        $message
            .append($('<li data-id="'+item.id+'"><span><span class="handle"></span>'+item.name+'</span><a href="#" data-video-id="'+item.id+'"><img src="/etc/designs/cs/brightcove/shared/img/delete.svg" /></a></li>'));
    });
    showPopup('Edit Playlist', $search + $message.prop('outerHTML'), 'Update', 'Cancel', function(event) {
        var playlistData = {
            a: 'update_playlist',
            videos: $sortable.toArray(),
            playlistId: data.playlist
        };

        $.ajax({
            type: 'GET',
            url: '/bin/brightcove/api.js',
            data: $.param(playlistData, true),
            async: true,
            success: function (data)
            {
                // do we need to do something here?
            }
        });

        event.hide();
    }, null);
    var el = document.getElementById("edit-playlist-sortable");
    $sortable = Sortable.create(el);
}

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
            "<tr style=\"cursor:pointer;\" class=\"" + (n.clip_source_video_id ? 'state-clip' : '') + " state-" + n.state.toLowerCase() + "\" id=\"" + (i) + "\"> \
            <td>\
                <input type=\"checkbox\" value=\"" + (n.id) + "\" id=\"" + (i) + "\" data-folder-id=\"" + ((n.folder_id) ? n.folder_id : '') + "\" onclick=\"checkCheck()\">\
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
        $("tr#"+i,"#tbData").on('click', function () {
            showMetaData(this.id);
            $("#tdMeta").show();
            $('body').trigger('brc:checked');
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
            </td><td><a href\"#\" data-playlist-id=\"" + n.id + "\" class=\"edit-playlist\">"
            + n.name +
            "</a></td><td>\
                <center>---</center>\
            </td><td>"
            + ((n.reference_id) ? n.reference_id : '') +
            "</td><td>"
            + n.id +
            "<span class=\"playlist-actions\"><a href=\"#\" data-playlist=\"" + n.id + "\" data-playlist-name=\"" + n.name + "\"><img src=\"/etc/designs/cs/brightcove/shared/img/delete.svg\" /></span>" +
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
        ).children("tr").on('click', function () {
                showMetaData(this.id);
                $('body').trigger('brc:checked');
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

function showVariants(v, idx) {
    if (v) {

        var elVariants = document.getElementById('divMeta.variants');

        // immediately update the text if there are no variants present
        if (!v.variants) {
            elVariants.textContent = "No variants.";
            return;
        }

        // now we show the variants
        elVariants.innerHTML = '';
        for (var x = 0; x < v.variants.length; x++) {
            var link = '<a href="#" class="variant" data-variant-id="'
                        + x + '" data-video-idx="' + idx + '">'
                        + v.variants[x].language + '</a>';
            elVariants.innerHTML += link;
        }
    }
}

function showMetaData(idx) {



    $("tr.select").removeClass("select");
    idx = oCurrentVideoList.length > idx ? idx : 0;
    $("#tbData>tr:eq(" + idx + ")").addClass("select");


    //CURRENTLY
    var v = oCurrentVideoList[idx];

    showVariants(v, idx);

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
    document.getElementById('divMeta.shortDescription').innerHTML = "<pre style=\"white-space: pre-wrap;\">" + (v.description != null ? v.description : "") + "</pre>";

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

    var labelsObject = "";
    if (v.labels) {
        var labels = v.labels.toString().split(',');
        for (var k = 0; k < labels.length; k++) {
            if (k > 0) {
                labelsObject += ', ';
            }
            labelsObject += '<a href="#" style="cursor:pointer;color:blue;text-decoration:underline"' +
                'onclick="triggerLabelClick(\'' + labels[k] + '\'); return false;" >' + labels[k] + '</a>';
        }
    }
    document.getElementById('divMeta.labels').innerHTML = labelsObject;

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

    $("#screen, #" + id).fadeIn("fast");
}

//close an open overlay and hide the screen, if a form is passed, reset it.
function closeBox(id, form) {
    //Don't close the screen if another window is open
    var strSelect = '#' + id + (($("div.overlay:visible").length > 1) ? "" : ",#screen");
    $(strSelect).fadeOut("fast");

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
    // first cleanup any existing dialogs
    var elem = document.querySelector('#upload_poster_dialog');
    if (elem) {
        elem.parentNode.removeChild(elem);
    }

    var dialog = new Coral.Dialog().set({
        id: 'upload_poster_dialog',
        header: {
          innerHTML: 'Update Poster Image'
        },
        content: {
          innerHTML: '<form class="coral-Form coral-Form--vertical">' +
          '<div class="coral-Form-fieldwrapper">' +
          '<label class="coral-Form-fieldlabel" id="label-vertical-textfield-0">Poster Source URL</label>' +
          '<input is="coral-textfield" class="coral-Form-field" placeholder="https://" name="name" id="upload_poster_dialog_field_source" labelledby="label-vertical-textfield-0"' +
          'value="' + document.getElementById('divMeta.videoStillURL').src + '"' +
          '></div></form>'
        },
        footer: {
          innerHTML: '<button is="coral-button" id="upload_poster_dialog_click" variant="primary">Upload</button><button is="coral-button" variant="quiet" coral-close>Cancel</button>'
        }
    });
    dialog.on('click', '#upload_poster_dialog_click', function() {
        if ($('#upload_poster_dialog_field_source').val() != '') {
            var fields = {
                limit: paging.size,
                start: paging.generic,
                id: document.getElementById('divMeta.id').innerHTML,
                a: 'upload_image',
                account_id: $("#selAccount").val(),
                poster_source: $('#upload_poster_dialog_field_source').val()
            }
            console.log(fields);
            $.ajax({
                url: apiLocation + '.js',
                type: 'POST',
                data: fields,
                success: function ( data ){
                    window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                    Load(getAllVideosURL());
                    dialog.hide();
                    location.reload();
                },
                error: function ( data )
                {
                    console.log(data);
                    alert('Oops! There was an error with your submission. Please try again.');
                }
            });
        } else {
            alert('Please provide a valid poster image source URL.');
        }

    });
    document.body.appendChild(dialog);
    dialog.show();

}

function uploadThumbnail()
{
    // first cleanup any existing dialogs
    var elem = document.querySelector('#upload_thumbnail_dialog');
    if (elem) {
        elem.parentNode.removeChild(elem);
    }

    var dialog = new Coral.Dialog().set({
        id: 'upload_thumbnail_dialog',
        header: {
          innerHTML: 'Update Thumbnail'
        },
        content: {
          innerHTML: '<form class="coral-Form coral-Form--vertical">' +
          '<div class="coral-Form-fieldwrapper">' +
          '<label class="coral-Form-fieldlabel" id="label-vertical-textfield-0">Thumbnail Source URL</label>' +
          '<input is="coral-textfield" class="coral-Form-field" placeholder="https://" name="name" id="upload_thumbnail_dialog_field_source" labelledby="label-vertical-textfield-0"' +
          'value="' + document.getElementById('divMeta.thumbnailURL').src + '"' +
          '></div></form>'
        },
        footer: {
          innerHTML: '<button is="coral-button" id="upload_thumbnail_dialog_click" variant="primary">Upload</button><button is="coral-button" variant="quiet" coral-close>Cancel</button>'
        }
    });
    dialog.on('click', '#upload_thumbnail_dialog_click', function() {
        if ($('#upload_thumbnail_dialog_field_source').val() != '') {
            var fields = {
                limit: paging.size,
                start: paging.generic,
                id: document.getElementById('divMeta.id').innerHTML,
                a: 'upload_image',
                account_id: $("#selAccount").val(),
                thumbnail_source: $('#upload_thumbnail_dialog_field_source').val()
            }
            console.log(fields);
            $.ajax({
                url: apiLocation + '.js',
                type: 'POST',
                data: fields,
                success: function ( data ){
                    window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                    Load(getAllVideosURL());
                    dialog.hide();
                    location.reload();
                },
                error: function ( data )
                {
                    console.log(data);
                    alert('Oops! There was an error with your submission. Please try again.');
                }
            });
        } else {
            alert('Please provide a valid thumbnail source URL.');
        }

    });
    document.body.appendChild(dialog);
    dialog.show();

}

function uploadtrack()
{
    var default_tracks = $("#divMeta\\.text_tracks_table tr.default_track");


    var elem = document.querySelector('#upload_text_track_dialog');
    if (elem) {
        elem.parentNode.removeChild(elem);
    }

    var language_options = [
        {value: "ar", content : {textContent: 'ar'}},
        {value: "ar-AE", content : {textContent: 'ar-AE'}},
        {value: "ar-BH", content : {textContent: 'ar-BH'}},
        {value: "ar-DZ", content : {textContent: 'ar-DZ'}},
        {value: "ar-EG", content : {textContent: 'ar-EG'}},
        {value: "ar-IQ", content : {textContent: 'ar-IQ'}},
        {value: "ar-JO", content : {textContent: 'ar-JO'}},
        {value: "ar-KW", content : {textContent: 'ar-KW'}},
        {value: "ar-LB", content : {textContent: 'ar-LB'}},
        {value: "ar-LY", content : {textContent: 'ar-LY'}},
        {value: "ar-MA", content : {textContent: 'ar-MA'}},
        {value: "ar-OM", content : {textContent: 'ar-OM'}},
        {value: "ar-QA", content : {textContent: 'ar-QA'}},
        {value: "ar-SA", content : {textContent: 'ar-SA'}},
        {value: "ar-SD", content : {textContent: 'ar-SD'}},
        {value: "ar-SY", content : {textContent: 'ar-SY'}},
        {value: "ar-TN", content : {textContent: 'ar-TN'}},
        {value: "ar-YE", content : {textContent: 'ar-YE'}},
        {value: "be", content : {textContent: 'be'}},
        {value: "be-BY", content : {textContent: 'be-BY'}},
        {value: "bg", content : {textContent: 'bg'}},
        {value: "bg-BG", content : {textContent: 'bg-BG'}},
        {value: "ca", content : {textContent: 'ca'}},
        {value: "ca-ES", content : {textContent: 'ca-ES'}},
        {value: "cs", content : {textContent: 'cs'}},
        {value: "cs-CZ", content : {textContent: 'cs-CZ'}},
        {value: "da", content : {textContent: 'da'}},
        {value: "da-DK", content : {textContent: 'da-DK'}},
        {value: "de", content : {textContent: 'de'}},
        {value: "de-AT", content : {textContent: 'de-AT'}},
        {value: "de-CH", content : {textContent: 'de-CH'}},
        {value: "de-DE", content : {textContent: 'de-DE'}},
        {value: "de-LU", content : {textContent: 'de-LU'}},
        {value: "el", content : {textContent: 'el'}},
        {value: "el-CY", content : {textContent: 'el-CY'}},
        {value: "el-GR", content : {textContent: 'el-GR'}},
        {value: "en", content : {textContent: 'en'}},
        {value: "en-AU", content : {textContent: 'en-AU'}},
        {value: "en-CA", content : {textContent: 'en-CA'}},
        {value: "en-GB", content : {textContent: 'en-GB'}},
        {value: "en-IE", content : {textContent: 'en-IE'}},
        {value: "en-IN", content : {textContent: 'en-IN'}},
        {value: "en-MT", content : {textContent: 'en-MT'}},
        {value: "en-NZ", content : {textContent: 'en-NZ'}},
        {value: "en-PH", content : {textContent: 'en-PH'}},
        {value: "en-SG", content : {textContent: 'en-SG'}},
        {value: "en-US", content : {textContent: 'en-US'}},
        {value: "en-ZA", content : {textContent: 'en-ZA'}},
        {value: "es", content : {textContent: 'es'}},
        {value: "es-AR", content : {textContent: 'es-AR'}},
        {value: "es-BO", content : {textContent: 'es-BO'}},
        {value: "es-CL", content : {textContent: 'es-CL'}},
        {value: "es-CO", content : {textContent: 'es-CO'}},
        {value: "es-CR", content : {textContent: 'es-CR'}},
        {value: "es-DO", content : {textContent: 'es-DO'}},
        {value: "es-EC", content : {textContent: 'es-EC'}},
        {value: "es-ES", content : {textContent: 'es-ES'}},
        {value: "es-GT", content : {textContent: 'es-GT'}},
        {value: "es-HN", content : {textContent: 'es-HN'}},
        {value: "es-MX", content : {textContent: 'es-MX'}},
        {value: "es-NI", content : {textContent: 'es-NI'}},
        {value: "es-PA", content : {textContent: 'es-PA'}},
        {value: "es-PE", content : {textContent: 'es-PE'}},
        {value: "es-PR", content : {textContent: 'es-PR'}},
        {value: "es-PY", content : {textContent: 'es-PY'}},
        {value: "es-SV", content : {textContent: 'es-SV'}},
        {value: "es-US", content : {textContent: 'es-US'}},
        {value: "es-UY", content : {textContent: 'es-UY'}},
        {value: "es-VE", content : {textContent: 'es-VE'}},
        {value: "et", content : {textContent: 'et'}},
        {value: "et-EE", content : {textContent: 'et-EE'}},
        {value: "fi", content : {textContent: 'fi'}},
        {value: "fi-FI", content : {textContent: 'fi-FI'}},
        {value: "fr", content : {textContent: 'fr'}},
        {value: "fr-BE", content : {textContent: 'fr-BE'}},
        {value: "fr-CA", content : {textContent: 'fr-CA'}},
        {value: "fr-CH", content : {textContent: 'fr-CH'}},
        {value: "fr-FR", content : {textContent: 'fr-FR'}},
        {value: "fr-LU", content : {textContent: 'fr-LU'}},
        {value: "ga", content : {textContent: 'ga'}},
        {value: "ga-IE", content : {textContent: 'ga-IE'}},
        {value: "he", content : {textContent: 'he'}},
        {value: "he-IL", content : {textContent: 'he-IL'}},
        {value: "hi-IN", content : {textContent: 'hi-IN'}},
        {value: "hr", content : {textContent: 'hr'}},
        {value: "hr-HR", content : {textContent: 'hr-HR'}},
        {value: "hu", content : {textContent: 'hu'}},
        {value: "hu-HU", content : {textContent: 'hu-HU'}},
        {value: "id", content : {textContent: 'id'}},
        {value: "id-ID", content : {textContent: 'id-ID'}},
        {value: "is", content : {textContent: 'is'}},
        {value: "is-IS", content : {textContent: 'is-IS'}},
        {value: "it", content : {textContent: 'it'}},
        {value: "it-CH", content : {textContent: 'it-CH'}},
        {value: "it-IT", content : {textContent: 'it-IT'}},
        {value: "ja", content : {textContent: 'ja'}},
        {value: "ja-JP", content : {textContent: 'ja-JP'}},
        {value: "ja-JP-u-ca-japanese-x-lvariant-JP", content : {textContent: 'ja-JP-u-ca-japanese-x-lvariant-JP'}},
        {value: "ko", content : {textContent: 'ko'}},
        {value: "ko-KR", content : {textContent: 'ko-KR'}},
        {value: "lt", content : {textContent: 'lt'}},
        {value: "lt-LT", content : {textContent: 'lt-LT'}},
        {value: "lv", content : {textContent: 'lv'}},
        {value: "lv-LV", content : {textContent: 'lv-LV'}},
        {value: "mk", content : {textContent: 'mk'}},
        {value: "mk-MK", content : {textContent: 'mk-MK'}},
        {value: "ms", content : {textContent: 'ms'}},
        {value: "ms-MY", content : {textContent: 'ms-MY'}},
        {value: "mt", content : {textContent: 'mt'}},
        {value: "mt-MT", content : {textContent: 'mt-MT'}},
        {value: "nl", content : {textContent: 'nl'}},
        {value: "nl-BE", content : {textContent: 'nl-BE'}},
        {value: "nl-NL", content : {textContent: 'nl-NL'}},
        {value: "nn-NO", content : {textContent: 'nn-NO'}},
        {value: "no", content : {textContent: 'no'}},
        {value: "no-NO", content : {textContent: 'no-NO'}},
        {value: "pl", content : {textContent: 'pl'}},
        {value: "pl-PL", content : {textContent: 'pl-PL'}},
        {value: "pt", content : {textContent: 'pt'}},
        {value: "pt-BR", content : {textContent: 'pt-BR'}},
        {value: "pt-PT", content : {textContent: 'pt-PT'}},
        {value: "ro", content : {textContent: 'ro'}},
        {value: "ro-RO", content : {textContent: 'ro-RO'}},
        {value: "ru", content : {textContent: 'ru'}},
        {value: "ru-RU", content : {textContent: 'ru-RU'}},
        {value: "sk", content : {textContent: 'sk'}},
        {value: "sk-SK", content : {textContent: 'sk-SK'}},
        {value: "sl", content : {textContent: 'sl'}},
        {value: "sl-SI", content : {textContent: 'sl-SI'}},
        {value: "sq", content : {textContent: 'sq'}},
        {value: "sq-AL", content : {textContent: 'sq-AL'}},
        {value: "sr", content : {textContent: 'sr'}},
        {value: "sr-BA", content : {textContent: 'sr-BA'}},
        {value: "sr-CS", content : {textContent: 'sr-CS'}},
        {value: "sr-La", content : {textContent: 'sr-La'}},
        {value: "tn", content : {textContent: 'tn'}},
        {value: "sr-La", content : {textContent: 'sr-La'}},
        {value: "tn-BA", content : {textContent: 'tn-BA'}},
        {value: "sr-La", content : {textContent: 'sr-La'}},
        {value: "tn-ME", content : {textContent: 'tn-ME'}},
        {value: "sr-La", content : {textContent: 'sr-La'}},
        {value: "tn-RS", content : {textContent: 'tn-RS'}},
        {value: "sr-ME", content : {textContent: 'sr-ME'}},
        {value: "sr-RS", content : {textContent: 'sr-RS'}},
        {value: "sv", content : {textContent: 'sv'}},
        {value: "sv-SE", content : {textContent: 'sv-SE'}},
        {value: "th", content : {textContent: 'th'}},
        {value: "th-TH", content : {textContent: 'th-TH'}},
        {value: "th-TH-u-nu-thai-x-lvariant-TH", content : {textContent: 'th-TH-u-nu-thai-x-lvariant-TH'}},
        {value: "ar", content : {textContent: 'ar'}},
        {value: "ia", content : {textContent: 'ia'}},
        {value: "nt-TH", content : {textContent: 'nt-TH'}},
        {value: "tr", content : {textContent: 'tr'}},
        {value: "tr-TR", content : {textContent: 'tr-TR'}},
        {value: "uk", content : {textContent: 'uk'}},
        {value: "uk-UA", content : {textContent: 'uk-UA'}},
        {value: "vi", content : {textContent: 'vi'}},
        {value: "vi-VN", content : {textContent: 'vi-VN'}},
        {value: "zh", content : {textContent: 'zh'}},
        {value: "zh-CN", content : {textContent: 'zh-CN'}},
        {value: "zh-HK", content : {textContent: 'zh-HK'}},
        {value: "zh-SG", content : {textContent: 'zh-SG'}},
        {value: "zh-TW", content : {textContent: 'zh-TW'}}
    ];

    var kind_options = [
        {
            value: "subtitles",
            content : {
                textContent: 'Subtitles'
            }
        },
        {
            value: "descriptions",
            content : {
                textContent: 'Description'
            }
        },
        {
            value: "chapters",
            content : {
                textContent: 'Chapters'
            }
        },
        {
            value: "metadata",
            content : {
                textContent: 'Metadata'
            }
        },
        {
            value: "captions",
            content : {
                textContent: 'Captions'
            }
        }
    ];

    var dialog = new Coral.Dialog().set({
        id: 'upload_text_track_dialog',
        header: {
          innerHTML: 'Upload Text Track'
        },
        content: {
          innerHTML: '<div class="coral-Form coral-Form--vertical" id="upload_text_track_form">' +
          '<div class="coral-Form-fieldwrapper">' +
          '<label class="coral-Form-fieldlabel" id="label-vertical-textfield-0">Language</label>' +
          '<coral-select name="text_track_language_field" placeholder="Language" id="text_track_language_field"></coral-select>' +
          '</div>' +
          '<div class="coral-Form-fieldwrapper">' +
          '<label class="coral-Form-fieldlabel" id="label-vertical-textfield-1">Label</label>' +
          '<input is="coral-textfield" class="coral-Form-field" placeholder="" name="name" id="text_track_label_field" labelledby="label-vertical-textfield-1" value="">' +
          '</div>' +
          '<div class="coral-Form-fieldwrapper">' +
          '<label class="coral-Form-fieldlabel" id="label-vertical-textfield-2">Kind</label>' +
          '<coral-select name="text_track_type_field" placeholder="Text Type" id="text_track_type_field"></coral-select>' +
          '</div>' +
          '<div class="coral-Form-fieldwrapper">' +
          '<coral-checkbox value="true" id="text_track_default_field">Make Default Track</coral-checkbox>' +
          '</div>' +
          '<div class="or_field_split">' +
          '<div class="coral-Form-fieldwrapper">' +
          '<label class="coral-Form-fieldlabel" id="label-vertical-3">Source URL</label>' +
          '<input is="coral-textfield" class="coral-Form-field" placeholder="" name="name" id="text_track_source_url_field" labelledby="label-vertical-3" value="">' +
          '</div>' +
          '<div class="coral-Form-fieldwrapper">' +
          '<coral-fileupload accept="text/*" name="file" action="#">' +
          '<button class="coral3-Button coral3-Button--secondary" is="coral-button" coral-fileupload-select>Select Track File</button>' +
          '</coral-fileupload>' +
          '</div>' +
          '</div>' +
          '<p>Please note you can only provide a Source URL <strong>OR</strong> a Source File.<br />File should be in a valid *.vtt format.</p>' +
          '</div>'
        },
        footer: {
          innerHTML: '<button is="coral-button" id="upload_text_track_dialog_click" variant="primary">Upload</button><button is="coral-button" variant="quiet" coral-close>Cancel</button>'
        }
    });

    dialog.on('coral-overlay:open', function() {
        console.log('dialog is ready');
        var select_field_track_type = $('#text_track_type_field').get(0);
        select_field_track_type.addEventListener('coral-select:showitems', function(event) {
            select_field_track_type.items.clear();
            kind_options.forEach(function(value, index) {
                select_field_track_type.items.add(value);
            });
        });
        var select_field_track_language = $('#text_track_language_field').get(0);
        select_field_track_language.addEventListener('coral-select:showitems', function(event) {
            select_field_track_language.items.clear();
            language_options.forEach(function(value, index) {
                select_field_track_language.items.add(value);
            });
        });
    });

    dialog.on('click', '#upload_text_track_dialog_click', function() {
        // add validation in below
        if (true) {
            var fields = {
                limit: paging.size,
                start: paging.generic,
                id: document.getElementById('divMeta.id').innerHTML,
                track_lang: $('#text_track_language_field').get(0).value,
                track_label: $('#text_track_label_field').get(0).value,
                //track_mime_type: 'text/webvtt',
                //track_mime_type: null,
                track_kind: $('#text_track_type_field').get(0).value,
                track_default: $('#text_track_default_field').get(0).value,
                track_filepath: $('.coral3-FileUpload-input').get(0).value,
                track_source: $('#text_track_source_url_field').get(0).value,
                a: 'upload_text_track',
                account_id: $("#selAccount").val(),
            }
            console.log(fields);
            $.ajax({
                url: apiLocation + '.js',
                type: 'POST',
                data: fields,
                success: function ( data ){
                    window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                    Load(getAllVideosURL());
                    dialog.hide();
                    location.reload();
                },
                error: function ( data )
                {
                    console.log(data);
                    alert('Oops! There was an error with your text track submission. Please try again.');
                }
            });
        } else {
            alert('Please provide values for all fields.');
        }

    });

    document.body.appendChild(dialog);
    dialog.show();

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
    $("#loading").slideDown("fast");
    $(".loading").show();
}

function loadEnd()
{
    $('html,body').scrollTop(0);
    $("#syncdbutton").css("color", "#333333");
    $("#syncdbutton").html('SYNC DATABASE');
    $("#syncdbutton").prop('disabled', false);
    $("#loading").slideUp("fast");
    $(".loading").hide();
}

function syncStart()
{
    $("#syncdbutton").css("color", "#6D8CAE");
    $("#syncdbutton").html('LOADING SYNC');
    $("#syncdbutton").prop('disabled', true);
    $("#loading").slideDown("fast");
    $(".loading").show();

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

    for (var i = 3; i < l; i++) {
        if (inputTags[i].checked) {
            $("#createPlstVideoTable").append(
                '<tr ><td>' + oCurrentVideoList[i - 4].name +
                '</td><td style="width: 25%;" >' + oCurrentVideoList[i - 4].id + '</td></tr>'
            );

            if (1 != idx) {
                form.playlist.value += ',';
            }
            form.playlist.value += oCurrentVideoList[i - 4].id;
            idx++;
        }
    }
    if (1 == idx) {
        alert("Please select at least one video to create a playlist.");
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
    var checkedTags = [];
    var selChek = document.getElementById('checkToggle');
    var l = inputTags.length
    for (var i = 2; i < l; i++) {
        if (true == inputTags[i].checked) {
            checkedTags.push(inputTags[i]);
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
