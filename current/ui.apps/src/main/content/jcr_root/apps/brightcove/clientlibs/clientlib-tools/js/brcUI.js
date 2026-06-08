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

    // If we just reloaded after switching accounts, surface a toast.
    try {
        var switchedAlias = window.sessionStorage && sessionStorage.getItem('brc_account_switched');
        if (switchedAlias) {
            sessionStorage.removeItem('brc_account_switched');
            brcToast('Switched to ' + switchedAlias);
        }
    } catch (e) { /* sessionStorage unavailable — toast skipped */ }
});

function showTableSpinner() {
    $('#tableSpinner').removeAttr('hidden');
}

function hideTableSpinner() {
    $('#tableSpinner').attr('hidden', '');
}

// BCON-142: visual indicator + clear-all for the filter panel.
function isAnyFilterActive() {
    var l = $('#label_list').val();
    var f = $('#fldr_list').val();
    var c = $('#filter_clips').is(':checked');
    return (l && l !== 'all') || (f && f !== 'all') || c;
}

function updateFilterIndicator() {
    var active = isAnyFilterActive();
    $('#filterToggle').toggleClass('has-active-filters', active);
    if (active) {
        $('#filterClearAll').removeAttr('hidden');
    } else {
        $('#filterClearAll').attr('hidden', '');
    }
}

function brcToast(message) {
    var existing = document.getElementById('brcToast');
    if (existing) existing.parentNode.removeChild(existing);
    var $toast = $('<div class="brc-toast" id="brcToast" role="status" aria-live="polite">'
        + '<span class="brc-toast-icon" aria-hidden="true">✓</span>'
        + '<span class="brc-toast-msg"></span></div>');
    $toast.find('.brc-toast-msg').text(message);
    $('body').append($toast);
    // Force reflow so the entry transition runs.
    void $toast[0].offsetHeight;
    $toast.addClass('is-visible');
    setTimeout(function () {
        $toast.removeClass('is-visible');
        setTimeout(function () { $toast.remove(); }, 250);
    }, 3000);
}




var $ACTIVE_TRACKS;
var $sortable;
var brc_admin = brc_admin || {};
var _mtfAllFolders = [];
var _mtfSelectedFolderId = null;
var _currentLabels = [];
var _uttLanguageOptions = null; // populated lazily from uploadtrack()
var _uttLangValid = false;      // true when a suggestion has been selected or exact match typed

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Create Playlist modal state
var _cpVideos = []; // [{id, name}]

// Edit Playlist modal state
var _epPlaylistId   = null;
var _epPlaylistType = null;
var _epVideos       = [];   // [{id, name}] — current ordered list
var _epSortable     = null;
var _epSearchDebounce = null;

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Create Playlist modal state
var _cpVideos = []; // [{id, name}]

// Edit Playlist modal state
var _epPlaylistId   = null;
var _epPlaylistType = null;
var _epVideos       = [];   // [{id, name}] — current ordered list
var _epSortable     = null;
var _epSearchDebounce = null;


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

    $('.brc-tab').on('click', function () {
        var $tab = $(this);
        // No early-return on already-active: programmatic `.click()` callers
        // (e.g. createPlaylistSubmit() jumping back to the playlist list after
        // creation) need the Load() to fire even if the tab is visually active.

        searchVal = '';
        $('.brc-tab').removeClass('is-active').attr('aria-selected', 'false');
        $tab.addClass('is-active').attr('aria-selected', 'true');

        // Reset the video-side filter UI when switching tabs so the
        // active-filter dot doesn't lie about the unfiltered list we're
        // about to load.
        $('#label_list').val('all');
        $('#fldr_list').val('all');
        $('#filter_clips').prop('checked', false);
        updateFilterIndicator();
        $('#emptyState').attr('hidden', '');

        showTableSpinner();

        if ($tab.attr('id') === 'allVideos') {
            Load(getAllVideosURL());
        } else if ($tab.attr('id') === 'allPlaylists') {
            Load(getAllPlaylistsURL());
        }
    });

    // Account switcher popover
    $('#accountTrigger').on('click', function (e) {
        e.stopPropagation();
        var $popover = $('#accountPopover');
        var willOpen = $popover.is('[hidden]');
        if (willOpen) {
            $popover.removeAttr('hidden');
        } else {
            $popover.attr('hidden', '');
        }
        $(this).attr('aria-expanded', willOpen ? 'true' : 'false');
    });

    $('#accountTrigger').on('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            $(this).trigger('click');
        }
    });

    $(document).on('click', function (e) {
        var $popover = $('#accountPopover');
        if ($popover.is('[hidden]')) return;
        if (!$(e.target).closest('#accountPopover, #accountTrigger').length) {
            $popover.attr('hidden', '');
            $('#accountTrigger').attr('aria-expanded', 'false');
        }
    });

    $(document).on('keydown', function (e) {
        if (e.key === 'Escape' && !$('#accountPopover').is('[hidden]')) {
            $('#accountPopover').attr('hidden', '');
            $('#accountTrigger').attr('aria-expanded', 'false').focus();
        }
    });

    // Filter panel toggle
    $('#filterToggle').on('click', function () {
        var $panel = $('#filterPanel');
        var willOpen = $panel.is('[hidden]');
        if (willOpen) {
            $panel.removeAttr('hidden');
        } else {
            $panel.attr('hidden', '');
        }
        $(this).attr('aria-expanded', willOpen ? 'true' : 'false');
    });

    $('.brc-account-row').on('click', function () {
        var $row = $(this);
        if ($row.hasClass('is-active')) {
            $('#accountPopover').attr('hidden', '');
            $('#accountTrigger').attr('aria-expanded', 'false');
            return;
        }
        var accountId = $row.attr('data-account-id');
        var accountAlias = $row.attr('data-account-alias') || accountId;

        // Switching reloads the whole page, so confirm before doing it rather
        // than switching on a single click (BCON-178). Close the popover and
        // ask the user to confirm; only switch on confirmation.
        $('#accountPopover').attr('hidden', '');
        $('#accountTrigger').attr('aria-expanded', 'false');

        // Build the message via jQuery so the alias is text-escaped.
        var $confirmMsg = $('<p>').text('Switch to "' + accountAlias + '"? The page will reload.');

        showPopup('Switch account', $confirmMsg.prop('outerHTML'), 'Switch', 'Cancel',
            function () {
                // Confirmed — set the brc_act cookie via CQ.Ext if available,
                // otherwise fall back to document.cookie, then reload.
                if (CQ && CQ.Ext) {
                    CQ.Ext.util.Cookies.set('brc_act', accountId);
                } else {
                    document.cookie = 'brc_act=' + encodeURIComponent(accountId) + '; path=/';
                }
                try {
                    if (window.sessionStorage) {
                        sessionStorage.setItem('brc_account_switched', accountAlias);
                    }
                } catch (e) { /* sessionStorage unavailable — toast won't appear, switch still happens */ }
                window.location.reload();
            },
            null /* Cancel just closes the dialog; no switch */);
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
        // Surface the empty state if the clips filter hides everything.
        var visibleRows = $('#tbData tr:visible').length;
        if (visibleRows === 0 && event.currentTarget.checked) {
            $('#emptyStateTitle').text('No clips in this list');
            $('#emptyStateHint').text('Uncheck "Show only clips" to see all videos.');
            $('#emptyState').removeAttr('hidden');
        } else {
            $('#emptyState').attr('hidden', '');
        }
        updateFilterIndicator();
    });

    $('#filterClearAll').on('click', function () {
        $('#label_list').val('all');
        $('#fldr_list').val('all');
        $('#filter_clips').prop('checked', false);
        $('#tbData tr').show();
        // Hide the clips-empty-state immediately so it doesn't linger
        // alongside the now-visible rows while Load() is in flight.
        $('#emptyState').attr('hidden', '');
        updateFilterIndicator();
        Load(getAllVideosURL());
    });

    $('#tbData').on('click', '.edit-playlist', function(event) {
        editPlaylistHandler(event);
    })

    $('#searchDiv_pl').on('click', '.btn-create-playlist', function(event) {
        openCreatePlaylistModal([]);
    });

    $('.pml-dialog_content').on('click', '.playlist-listing li a', function(event) {
        $(event.target).parents('li').remove();
    })

    $('.pml-dialog_content').on('click', '.label-listing li a', function(event) {
        $(event.target).parents('li').remove();
    })

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
        // Scope to row checkboxes only — the legacy `getElementsByTagName('input')`
        // walk picked up #search/#search_pl/#filter_clips after the BCON-121
        // restructure put the filter panel inside #listTable, inflating selection
        // counts and quietly toggling the clips filter on select-all.
        paging.selectedVideos = [];
        $('#tbData input[type="checkbox"]').each(function () {
            if (this.checked) {
                paging.selectedVideos.push(this);
                $(this).closest('tr').addClass('is-selected');
            } else {
                $(this).closest('tr').removeClass('is-selected');
            }
        });
        var n = paging.selectedVideos.length;
        $('#bulkCount').text(n);
        var isVideos = window.brcCurrentView === 'videos';
        var isPlaylists = window.brcCurrentView === 'playlists';
        if ((isVideos || isPlaylists) && n > 0) {
            $('#bulkActionBar').removeAttr('hidden');
            $('#bulkCreatePlaylist, .brc-bulk-move-wrapper').toggle(isVideos);
            $('#bulkDeletePlaylists').toggle(isPlaylists);
        } else {
            $('#bulkActionBar').attr('hidden', '');
        }
        // Original .butDiv kept for the playlist drill-down view (Remove From Playlist).
        $('.butDiv').toggle(window.brcCurrentView === 'playlist' && n > 0);
    });

    $('#bulkCreatePlaylist').on('click', function () { createPlaylistBox(); });
    $('#bulkMoveToFolder').on('click', function () { moveVideoToFolder(); });

    $('#bulkDeletePlaylists').on('click', function () {
        var $checked = $('#tbData input[type="checkbox"]:checked');
        var count = $checked.length;
        if (!count) return;
        var names = $checked.map(function () { return $(this).attr('data-playlist-name'); }).get();
        var ids   = $checked.map(function () { return $(this).val(); }).get();
        // Build the confirmation message via DOM so playlist names are escaped
        // (showPopup renders message via .html()).
        var $msg = $('<div>').append(
            $('<p>').text('Are you sure you want to delete the following ' + count + ' playlist' + (count > 1 ? 's' : '') + '?')
        ).append(
            $('<ul style="margin:8px 0 0 18px;padding:0;">').append(
                names.map(function (n) { return $('<li>').text(n); })
            )
        );
        showPopup(
            'Delete ' + count + ' Playlist' + (count > 1 ? 's' : ''),
            $msg.prop('outerHTML'),
            'Delete',
            'Cancel',
            function (dialog) {
                dialog.hide();
                var remaining = ids.length;
                var onDone = function () {
                    remaining--;
                    if (remaining === 0) { Load(getAllPlaylistsURL()); }
                };
                ids.forEach(function (id) {
                    $.ajax({
                        type: 'GET',
                        url: '/bin/brightcove/api.js',
                        data: { a: 'delete_playlist', playlist: id },
                        async: true,
                        complete: onDone
                    });
                });
            },
            function () {}
        );
    });
    $('#bulkClear').on('click', function () {
        $('#tbData input[type="checkbox"]').prop('checked', false);
        $('#checkToggle').prop('checked', false);
        $('#tbData tr').removeClass('is-selected');
        $('body').trigger('brc:checked');
    });

    $('body').on('click', '.brc-variant-link', function() {
        var variantId = parseInt($(this).attr('data-variant-id'), 10);
        var videoId   = parseInt($(this).attr('data-video-idx'), 10);
        var variant   = oCurrentVideoList[videoId].variants[variantId];

        $('#variantModalTitle').text('Variant Details — ' + variant.language);
        $('#variantModalName').text(variant.language || '—');
        $('#variantModalDesc').text(variant.description || '—');
        $('#variantModalLongDesc').text(variant.long_description || '—');
        var $cf = $('#variantModalCustomFields').empty();
        if (variant.custom_fields && JSON.stringify(variant.custom_fields) !== '{}') {
            var $table = $('<table class="brc-variant-cf-table">');
            for (var prop in variant.custom_fields) {
                $table.append(
                    $('<tr>').append($('<td>').text(prop))
                             .append($('<td>').text(variant.custom_fields[prop]))
                );
            }
            $cf.append($table);
        } else {
            $cf.text('—');
        }
        $('#variantDetailsModal').removeAttr('hidden');
    });

    $(document).on('click', '.brc-variant-modal-close, .brc-variant-modal-ok', function() {
        $('#variantDetailsModal').attr('hidden', '');
    });
    $(document).on('click', '#variantDetailsModal', function(e) {
        if ($(e.target).is('#variantDetailsModal')) $(this).attr('hidden', '');
    });

    // Clear search button — Videos
    $('#search').on('input', function() {
        $('#searchClear').toggle(this.value !== '' && this.value !== 'Search Videos');
    });

    $('#searchClear').on('click', function() {
        document.getElementById('search').value = '';
        document.getElementById('selField').value = 'every_field';
        $(this).hide();
        searchVal = '';
        searchField = 'every_field';
        Load(getAllVideosURL());
    });

    // Clear search button — Playlists
    $('#search_pl').on('input', function() {
        $('#searchClear_pl').toggle(this.value !== '' && this.value !== 'Search Playlists');
    });

    $('#searchClear_pl').on('click', function() {
        document.getElementById('search_pl').value = '';
        document.getElementById('selField_pl').value = 'every_field';
        $(this).hide();
        togglePlSearchHint('every_field');
        searchVal = '';
        searchField = 'every_field';
        Load(getAllPlaylistsURL());
    });

    // Enter in the playlist search input triggers the search.
    $('#search_pl').on('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            document.getElementById('searchBut_pl').click();
        }
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
                    $('<li class="autocomplete-item" data-name="'+n+'">'+n+'<img src="/apps/brightcove/clientlibs/clientlib-tools/img/shared/img/add.svg" /></li>')
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
                            .append($('<li><span><span class="handle"></span>'+label+'</span><a href="#"><img src="/apps/brightcove/clientlibs/clientlib-tools/img/shared/img/delete.svg" /></a></li>'));

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
                $('<li class="autocomplete-item" data-name="'+n.name+'" data-id="'+n.id+'">'+n.name+'<img src="/apps/brightcove/clientlibs/clientlib-tools/img/shared/img/add.svg" /></li>')
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
                        .append($('<li data-id="'+videoId+'"><span><span class="handle"></span>'+videoName+'</span><a href="#" data-video-id="'+videoId+'"><img src="/apps/brightcove/clientlibs/clientlib-tools/img/shared/img/delete.svg" /></a></li>'));

                    // clear the search
                    $('.pml-dialog .playlist-add-input input').val();
                })
            )
        })
    }
}

function moveVideoToFolder() {
    openMoveToFolderModal();
}

function openMoveToFolderModal() {
    var count = paging.selectedVideos.length;
    $('#mtfSubtitle').text(count + ' video' + (count !== 1 ? 's' : '') + ' selected');
    $('#mtfSearch').val('');
    _mtfSelectedFolderId = null;
    $('#mtfMove').prop('disabled', true);

    if (_mtfAllFolders.length > 0) {
        renderMtfFolders(_mtfAllFolders);
    } else {
        $('#mtfFolderList').html('<li class="brc-mtf-empty">Loading\u2026</li>');
        $.ajax({
            type: 'GET',
            url: '/bin/brightcove/api.js',
            data: { a: 'list_folders', account_id: $('#selAccount').val(), callback: 'mtfFolderLoadCallback' },
            async: true
        });
    }

    $('body').addClass('brc-mtf-open');
    $('#moveToFolderModal').removeAttr('hidden');
}

function mtfFolderLoadCallback(data) {
    _mtfAllFolders = (data && data.items) ? data.items : [];
    renderMtfFolders(_mtfAllFolders);
}

function closeMoveToFolderModal() {
    $('#moveToFolderModal').attr('hidden', '');
    $('body').removeClass('brc-mtf-open');
    _mtfSelectedFolderId = null;
}

function renderMtfFolders(folders) {
    var q = $('#mtfSearch').val().toLowerCase();
    var filtered = q ? folders.filter(function (f) {
        return f.name.toLowerCase().indexOf(q) !== -1;
    }) : folders;

    var $list = $('#mtfFolderList').empty();

    if (filtered.length === 0) {
        $list.html('<li class="brc-mtf-empty">No folders found.</li>');
        return;
    }

    var folderSvg = '<svg class="brc-mtf-folder-icon" width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">'
        + '<path d="M1.5 4.5A1 1 0 0 1 2.5 3.5H6L7.5 5H13.5A1 1 0 0 1 14.5 6V12.5A1 1 0 0 1 13.5 13.5H2.5A1 1 0 0 1 1.5 12.5V4.5Z" stroke="#6b7280" stroke-width="1.25"/>'
        + '</svg>';
    var checkSvg = '<svg class="brc-mtf-check" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">'
        + '<path d="M3 8l3.5 3.5L13 5" stroke="#1a1a18" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" fill="none"/>'
        + '</svg>';

    $.each(filtered, function (i, folder) {
        var isSelected = folder.id === _mtfSelectedFolderId;
        var $li = $('<li>')
            .addClass('brc-mtf-list-item' + (isSelected ? ' is-selected' : ''))
            .attr('data-folder-id', folder.id)
            .append($(folderSvg))
            .append($('<span class="brc-mtf-folder-name">').text(folder.name))
            .append($(checkSvg));
        $list.append($li);
    });
}

$(function () {
    $(document).on('click', '.brc-mtf-list-item', function () {
        var folderId = $(this).attr('data-folder-id');
        if (_mtfSelectedFolderId === folderId) {
            _mtfSelectedFolderId = null;
            $(this).removeClass('is-selected');
            $('#mtfMove').prop('disabled', true);
        } else {
            _mtfSelectedFolderId = folderId;
            $('.brc-mtf-list-item').removeClass('is-selected');
            $(this).addClass('is-selected');
            $('#mtfMove').prop('disabled', false);
        }
    });

    $('#mtfSearch').on('input', function () {
        renderMtfFolders(_mtfAllFolders);
    });

    $('#mtfClose, #mtfCancel').on('click', function () {
        closeMoveToFolderModal();
    });

    $('#moveToFolderModal').on('click', function (e) {
        if (e.target === this) closeMoveToFolderModal();
    });

    $('#mtfMove').on('click', function () {
        if ($(this).prop('disabled') || !_mtfSelectedFolderId) return;
        var folderId = _mtfSelectedFolderId;
        var accountId = $('#selAccount').val();
        var selectedCount = paging.selectedVideos.length;
        var folderMatch = _mtfAllFolders.filter(function (f) { return f.id === folderId; })[0];
        var folderName = folderMatch ? folderMatch.name : folderId;
        var requests = [];
        $.each(paging.selectedVideos, function (i, checkbox) {
            requests.push($.ajax({
                type: 'GET',
                url: '/bin/brightcove/api.js',
                data: {
                    a: 'move_video_to_folder',
                    folder: folderId,
                    video: $(checkbox).val(),
                    account_id: accountId
                },
                async: true
            }));
        });
        closeMoveToFolderModal();
        $.when.apply($, requests).always(function () {
            $('#fldr_list').change();
            var toastMsg = (selectedCount === 1 ? 'Video' : 'Videos') + ' moved to ' + folderName;
            brcToast(toastMsg);
        });
    });

    // ── Create Playlist Modal event handlers ────────────────────────────────

    $('#cpClose, #cpCancel').on('click', function () {
        closeCreatePlaylistModal();
    });

    $('#createPlaylistModal').on('click', function (e) {
        if (e.target === this) closeCreatePlaylistModal();
    });

    $('#cpTitle_input').on('input', function () {
        cpUpdateButton();
    });

    $('#cpSubmitBtn').on('click', function () {
        if (!$(this).prop('disabled')) cpSubmit();
    });

    // ── Upload Text Track Modal event handlers ───────────────────────────────

    function closeUttModal() {
        $('#uploadTextTrackModal').attr('hidden', '');
        document.body.style.overflow = '';
    }

    function uttCheckUploadBtn() {
        var langOk   = _uttLangValid;
        var sourceOk = $('#uttSourceUrl').val().trim().length > 0 ||
                       ($('#uttFile')[0] && $('#uttFile')[0].files.length > 0);
        $('#uttUpload').prop('disabled', !(langOk && sourceOk));
    }

    $('#uttClose, #uttCancel').on('click', function () {
        closeUttModal();
    });

    $('#uploadTextTrackModal').on('click', function (e) {
        if (e.target === this) closeUttModal();
    });

    // ── Language autocomplete ────────────────────────────────────────────────
    $('#uttLanguage').on('input', function () {
        var query = $(this).val().trim().toLowerCase();
        _uttLangValid = false;
        if (!query || !_uttLanguageOptions) {
            $('#uttLangSuggestions').attr('hidden', '').empty();
            uttCheckUploadBtn();
            return;
        }
        var matches = _uttLanguageOptions.filter(function (opt) {
            return opt.toLowerCase().indexOf(query) !== -1;
        });
        if (matches.length === 0) {
            $('#uttLangSuggestions').attr('hidden', '').empty();
        } else {
            var $list = $('#uttLangSuggestions').empty().removeAttr('hidden');
            matches.slice(0, 50).forEach(function (lang) {
                $('<li class="brc-tt-suggestion-item">').text(lang)
                    .on('mousedown', function (e) {
                        e.preventDefault(); // prevent blur before click fires
                        $('#uttLanguage').val(lang);
                        _uttLangValid = true;
                        $('#uttLangSuggestions').attr('hidden', '').empty();
                        uttCheckUploadBtn();
                    })
                    .appendTo($list);
            });
            // Exact match counts as valid even without clicking
            if (matches.indexOf($('#uttLanguage').val().trim()) !== -1) {
                _uttLangValid = true;
            }
        }
        uttCheckUploadBtn();
    });

    $('#uttLanguage').on('blur', function () {
        // Small delay so mousedown on a suggestion fires first
        setTimeout(function () {
            $('#uttLangSuggestions').attr('hidden', '').empty();
            // Re-validate: if typed value exactly matches a known option, accept it
            if (_uttLanguageOptions) {
                var val = $('#uttLanguage').val().trim();
                _uttLangValid = _uttLanguageOptions.indexOf(val) !== -1;
            } else {
                _uttLangValid = false;
            }
            uttCheckUploadBtn();
        }, 150);
    });

    // ── File / URL mutual exclusion ─────────────────────────────────────────
    $('#uttFileBtn').on('click', function () {
        $('#uttFile').trigger('click');
    });

    // Clicking the filename in the pill re-opens the file picker
    $('#uttFileName').on('click', function () {
        $('#uttFile').trigger('click');
    });

    // X in the pill clears the file and restores the button
    $('#uttFileClear').on('click', function () {
        $('#uttFile').val('');
        $('#uttFilePill').attr('hidden', '');
        $('#uttFileBtn').show();
        $('#uttSourceUrl').prop('disabled', false);
        uttCheckUploadBtn();
    });

    $('#uttFile').on('change', function () {
        var file = this.files && this.files[0];
        if (file) {
            $('#uttSourceUrl').val('').prop('disabled', true);
            $('#uttFileName').text(file.name);
            $('#uttFilePill').removeAttr('hidden');
            $('#uttFileBtn').hide();
        }
        uttCheckUploadBtn();
    });

    $('#uttSourceUrl').on('input', function () {
        if ($(this).val().trim() === '') {
            $('#uttFile').val('');
            $(this).prop('disabled', false);
        }
        uttCheckUploadBtn();
    });

    $('#uttUpload').on('click', function () {
        var fields = {
            limit: paging.size,
            start: paging.generic,
            id: document.getElementById('divMeta.id').innerHTML,
            track_lang: $('#uttLanguage').val(),
            track_label: $('#uttLabel').val(),
            track_kind: $('#uttKind').val(),
            track_default: $('#uttDefault').is(':checked') ? 'true' : 'false',
            track_filepath: '',
            track_source: $('#uttSourceUrl').val(),
            a: 'upload_text_track',
            account_id: $('#selAccount').val()
        };

        function doUpload() {
            $.ajax({
                url: apiLocation + '.js',
                type: 'POST',
                data: fields,
                success: function () {
                    window.selectedVideoId = document.getElementById('divMeta.id').innerHTML;
                    Load(getAllVideosURL());
                    closeUttModal();
                    location.reload();
                },
                error: function () {
                    alert('Oops! There was an error with your text track submission. Please try again.');
                }
            });
        }

        var fileInput = $('#uttFile')[0];
        var file = fileInput && fileInput.files && fileInput.files[0];
        if (file) {
            // Read file content as text so the server receives the actual VTT bytes
            var reader = new FileReader();
            reader.onload = function (e) {
                fields.track_filepath = e.target.result;
                doUpload();
            };
            reader.onerror = function () {
                alert('Oops! Could not read the selected file. Please try again.');
            };
            reader.readAsText(file);
        } else {
            doUpload();
        }
    });

    // ── Video Preview Modal event handlers ──────────────────────────────────

    $('#vpClose, #vpCloseBtn').on('click', function () {
        stopPreview();
    });

    $('#videoPreviewModal').on('click', function (e) {
        if (e.target === this) stopPreview();
    });

    // ── Edit Playlist Modal event handlers ──────────────────────────────────

    // Close buttons
    $('#epClose, #epCancel').on('click', function () {
        closeEditPlaylistModal();
    });

    // Overlay backdrop click closes modal; any click outside the search field/results closes the suggestions
    $('#editPlaylistModal').on('click', function (e) {
        if (e.target === this) closeEditPlaylistModal();
        if (!$(e.target).closest('#epVideoSearch, #epSearchResults').length) {
            $('#epVideoSearch').val('');
            $('#epSearchResults').empty().removeClass('is-visible');
        }
    });

    // Playlist name input — enable/disable Update button
    $('#epPlaylistName').on('input', function () {
        epUpdateButtons();
    });

    // Video search — debounced 750 ms
    $('#epVideoSearch').on('input', function () {
        var query = $(this).val().trim();
        clearTimeout(_epSearchDebounce);
        if (query.length === 0) {
            $('#epSearchResults').empty().removeClass('is-visible');
            return;
        }
        _epSearchDebounce = setTimeout(function () {
            $.ajax({
                type: 'GET',
                url: '/bin/brightcove/api.js',
                data: {
                    a: 'search_videos',
                    callback: 'epVideoSearchCallback',
                    query: query,
                    limit: 20
                },
                async: true
            });
        }, 750);
    });

    // Click a search result → add to playlist
    $(document).on('click', '.brc-ep-search-result-item', function () {
        if ($(this).hasClass('is-added')) return;
        var vid  = $(this).attr('data-video-id');
        var name = $(this).attr('data-video-name') || vid;
        epAddVideo(vid, name);
    });

    // Click delete on a playlist item → remove + immediate save
    $(document).on('click', '.brc-ep-item-delete', function () {
        if ($(this).prop('disabled')) return;
        var vid = $(this).attr('data-video-id');
        epRemoveAndSave(vid);
    });

    // Update Playlist button
    $('#epUpdate').on('click', function () {
        if ($(this).prop('disabled')) return;
        epSavePlaylist(true);
    });

    // ── Create Playlist Modal event handlers ────────────────────────────────

    $('#cpClose, #cpCancel').on('click', function () {
        closeCreatePlaylistModal();
    });

    $('#createPlaylistModal').on('click', function (e) {
        if (e.target === this) closeCreatePlaylistModal();
    });

    $('#cpTitle_input').on('input', function () {
        cpUpdateButton();
    });

    $('#cpSubmitBtn').on('click', function () {
        if (!$(this).prop('disabled')) cpSubmit();
    });

    // ── Edit Playlist Modal event handlers ──────────────────────────────────

    // Close buttons
    $('#epClose, #epCancel').on('click', function () {
        closeEditPlaylistModal();
    });

    // Overlay backdrop click closes modal; any click outside the search field/results closes the suggestions
    $('#editPlaylistModal').on('click', function (e) {
        if (e.target === this) closeEditPlaylistModal();
        if (!$(e.target).closest('#epVideoSearch, #epSearchResults').length) {
            $('#epVideoSearch').val('');
            $('#epSearchResults').empty().removeClass('is-visible');
        }
    });

    // Playlist name input — enable/disable Update button
    $('#epPlaylistName').on('input', function () {
        epUpdateButtons();
    });

    // Video search — debounced 750 ms
    $('#epVideoSearch').on('input', function () {
        var query = $(this).val().trim();
        clearTimeout(_epSearchDebounce);
        if (query.length === 0) {
            $('#epSearchResults').empty().removeClass('is-visible');
            return;
        }
        _epSearchDebounce = setTimeout(function () {
            $.ajax({
                type: 'GET',
                url: '/bin/brightcove/api.js',
                data: {
                    a: 'search_videos',
                    callback: 'epVideoSearchCallback',
                    query: query,
                    limit: 20
                },
                async: true
            });
        }, 750);
    });

    // Click a search result → add to playlist
    $(document).on('click', '.brc-ep-search-result-item', function () {
        if ($(this).hasClass('is-added')) return;
        var vid  = $(this).attr('data-video-id');
        var name = $(this).attr('data-video-name') || vid;
        epAddVideo(vid, name);
    });

    // Click delete on a playlist item → remove + immediate save
    $(document).on('click', '.brc-ep-item-delete', function () {
        if ($(this).prop('disabled')) return;
        var vid = $(this).attr('data-video-id');
        epRemoveAndSave(vid);
    });

    // Update Playlist button
    $('#epUpdate').on('click', function () {
        if ($(this).prop('disabled')) return;
        epSavePlaylist(true);
    });
});

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
        updateFilterIndicator();
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
    _mtfAllFolders = (data && data.items) ? data.items : [];
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
                $('<div>')
                .append($('<p>Label Name:</p>'))
                .append($('<input class="input-label-name" type="text" autofocus />'))
                .append($('<p class="input-label-error" style="display:none;color:#d7373f;margin-top:6px;font-size:12px;"></p>'));
            showPopup('Create New Label',
                $message.prop('outerHTML'),
                'Create',
                'Cancel',
                function(dialog) {
                    var $input = $('.input-label-name');
                    var $err   = $('.input-label-error');
                    var labelName = ($input.val() || '').trim();

                    // Only an empty name is a hard error — give the user an
                    // actual message instead of a silently-red box (BCON-182).
                    if (labelName === '') {
                        $input.addClass('error');
                        $err.text('Please enter a label name.').show();
                        return; // keep the dialog open
                    }

                    // Brightcove labels are hierarchical paths and must start
                    // with '/'. Be forgiving and prepend it when the user omits
                    // it, rather than rejecting an otherwise-valid name.
                    if (labelName.charAt(0) !== '/') {
                        labelName = '/' + labelName;
                    }

                    $input.removeClass('error');
                    $err.hide();

                    $.ajax({
                        type: 'GET',
                        url: '/bin/brightcove/api.js',
                        data: { a: 'create_label', label: labelName },
                        async: true,
                        // Inspect the raw response in `complete` rather than
                        // relying on jQuery's success/error split: BrcApi writes
                        // "true" on success and a body containing {"error":<code>}
                        // on failure (409 = the path already exists).
                        complete: function (jqXHR) {
                            var body = (jqXHR && jqXHR.responseText) ? jqXHR.responseText : '';
                            if (body.indexOf('"error"') !== -1) {
                                $input.addClass('error');
                                $err.text(body.indexOf('409') !== -1
                                    ? 'That label already exists.'
                                    : 'Could not create the label. Please try again.').show();
                                return; // keep the dialog open
                            }
                            dialog.hide();
                            // Brightcove's GET /labels is an async search index
                            // that lags well behind creation, so reloading would
                            // re-fetch a list that does NOT yet include the
                            // just-created label and the user would think nothing
                            // happened (BCON-182). Add it to the filter dropdown
                            // optimistically and confirm with a toast.
                            var exists = $('#label_list option').filter(function () {
                                return this.value === labelName;
                            }).length > 0;
                            if (!exists) {
                                var $newOpt = $('<option>', { value: labelName }).text(labelName);
                                var $createOpt = $('#label_list option[value="create"]');
                                if ($createOpt.length) { $createOpt.before($newOpt); }
                                else { $('#label_list').append($newOpt); }
                            }
                            // Keep showing all videos rather than filtering to the
                            // brand-new (empty) label.
                            $('#label_list').val('all');
                            if (typeof brcToast === 'function') {
                                brcToast("Label '" + labelName + "' created");
                            }
                        }
                    });
                },
                function(dialog) {
                    // do nothing here
                    triggerLabelClick('all');
                })
        } else {
            console.log('search videos by label=' + selected);
            Load(getLabelListingUrl(selected));
        }
        updateFilterIndicator();
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

function callback(data) {
    // generic callback
}

function loadLabelCallback(data) {
    var $label_select = $('#label_list');
    $.each(data.items, function (i, n) {
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
    var $link = $(event.currentTarget);
    var playlistId   = $link.attr('data-playlist-id');
    var playlistName = $link.attr('data-playlist-name') || '';
    var playlistType = $link.attr('data-playlist-type') || '';
    openEditPlaylistModal(playlistId, playlistName, playlistType);
}

// Called via JSONP from list_videos_in_playlist
function editPlaylistListingCallback(data) {
    _epVideos = (data.items || []).map(function(v) {
        return { id: v.id, name: v.name || v.id };
    });
    epRenderPlaylistItems();
    epUpdateButtons();
}

// ─── Edit Playlist Modal ──────────────────────────────────────────────────────

function openEditPlaylistModal(playlistId, playlistName, playlistType) {
    _epPlaylistId   = playlistId;
    _epPlaylistType = playlistType;
    _epVideos       = [];

    // Populate name field
    $('#epPlaylistName').val(playlistName);

    // Smart vs explicit: hide Add Videos section for smart
    var isSmart = (playlistType !== 'EXPLICIT');
    if (isSmart) {
        $('#epVideosSection').hide();
    } else {
        $('#epVideosSection').show();
        // Clear search
        $('#epVideoSearch').val('');
        $('#epSearchResults').empty().removeClass('is-visible');
    }

    // Clear playlist items, show empty state while loading
    $('#epPlaylistItems').empty();
    epUpdateButtons();

    // Show modal + lock scroll
    $('#editPlaylistModal').removeAttr('hidden');
    document.body.style.overflow = 'hidden';

    // For explicit playlists, load current video list via JSONP
    if (!isSmart) {
        $.ajax({
            type: 'GET',
            url: '/bin/brightcove/api.js',
            data: {
                a: 'list_videos_in_playlist',
                callback: 'editPlaylistListingCallback',
                query: playlistId
            },
            async: true
        });
    }
}

function closeEditPlaylistModal() {
    $('#editPlaylistModal').attr('hidden', '');
    document.body.style.overflow = '';
    if (_epSortable) {
        _epSortable.destroy();
        _epSortable = null;
    }
    _epPlaylistId   = null;
    _epPlaylistType = null;
    _epVideos       = [];
    clearTimeout(_epSearchDebounce);
}

function epRenderPlaylistItems() {
    var $list = $('#epPlaylistItems').empty();

    if (_epVideos.length === 0) {
        // Show blank space (empty bordered box, no message)
        return;
    }

    _epVideos.forEach(function(v) {
        // Use jQuery DOM methods so v.name is always text-node–escaped; raw
        // HTML concatenation would let a name containing " or < break out of
        // attributes or inject elements.
        var $item = $('<li class="brc-ep-playlist-item">');
        $item.append($('<span class="brc-ep-item-handle">').attr('title', 'Drag to reorder').html('&#9776;'));
        $item.append($('<span class="brc-ep-item-name">').attr('title', v.name).text(v.name));
        $item.append(
            $('<button type="button" class="brc-ep-item-delete" title="Remove">')
                .attr('data-video-id', v.id)
                .append('<img src="/apps/brightcove/clientlibs/clientlib-tools/img/shared/img/trash_can.png" alt="Remove" />')
        );
        $list.append($item);
    });

    // Re-initialise Sortable
    if (_epSortable) { _epSortable.destroy(); }
    _epSortable = Sortable.create($list[0], {
        handle: '.brc-ep-item-handle',
        animation: 150,
        ghostClass: 'sortable-ghost',
        onEnd: function() {
            // Sync _epVideos order from DOM
            var newOrder = [];
            $list.find('.brc-ep-item-delete').each(function() {
                var vid = $(this).attr('data-video-id');
                var match = _epVideos.filter(function(v){ return v.id === vid; })[0];
                if (match) newOrder.push(match);
            });
            _epVideos = newOrder;
            epUpdateButtons();
        }
    });
}

function epVideoIds() {
    return _epVideos.map(function(v){ return v.id; });
}

function epUpdateButtons() {
    var nameVal = $('#epPlaylistName').val().trim();
    var enabled = nameVal.length > 0;
    $('#epUpdate').prop('disabled', !enabled);
    // delete buttons follow the same disabled state as update
    $('.brc-ep-item-delete').prop('disabled', !enabled);
}

function epAddVideo(id, name) {
    // No-op if already in playlist
    var already = _epVideos.some(function(v){ return v.id === id; });
    if (already) return;
    _epVideos.push({ id: id, name: name });
    epRenderPlaylistItems();
    // Re-mark search results
    epMarkAddedInResults();
    epUpdateButtons();
}

function epMarkAddedInResults() {
    var addedIds = epVideoIds();
    $('#epSearchResults .brc-ep-search-result-item').each(function() {
        var vid = $(this).attr('data-video-id');
        if (addedIds.indexOf(vid) !== -1) {
            $(this).addClass('is-added');
        } else {
            $(this).removeClass('is-added');
        }
    });
}

function epRemoveAndSave(videoId) {
    _epVideos = _epVideos.filter(function(v){ return v.id !== videoId; });
    epRenderPlaylistItems();
    epMarkAddedInResults();
    epUpdateButtons();
    // Immediately persist the updated list
    epSavePlaylist(false);
}

function epSetSaving(saving) {
    $('#epUpdate, #epCancel').prop('disabled', saving);
}

function epSavePlaylist(showToast) {
    var playlistName = $('#epPlaylistName').val().trim();
    // Guard: treat an unset type as smart so we never accidentally wipe videos
    // on a playlist whose type we don't know.
    var isExplicit = (_epPlaylistType === 'EXPLICIT');

    var playlistData = {
        a: 'update_playlist',
        playlistId: _epPlaylistId,
        playlistName: playlistName
    };

    // Build the query string manually for the videos portion so we bypass
    // jQuery's $.param behaviour of dropping empty arrays entirely.  When the
    // list is empty we send clearVideos=true; BrcApi converts that to an empty
    // String[] which CmsAPI serialises as "video_ids": [] in the PATCH body,
    // telling Brightcove to clear the playlist.  Simply passing videos: [] to
    // $.param would produce no parameter at all, causing the server to receive
    // null and leave the existing videos untouched.
    var qs = $.param(playlistData);
    if (isExplicit) {
        var ids = epVideoIds();
        if (ids.length > 0) {
            qs += '&' + ids.map(function(id) {
                return 'videos=' + encodeURIComponent(id);
            }).join('&');
        } else {
            qs += '&clearVideos=true';
        }
    }

    epSetSaving(true);

    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: qs,
        async: true,
        success: function() {
            if (showToast) {
                closeEditPlaylistModal();
                brcToast('Playlist updated');
            } else {
                epSetSaving(false);
            }
        },
        error: function() {
            epSetSaving(false);
        }
    });
}

// Video search callback (JSONP)
function epVideoSearchCallback(data) {
    var $results = $('#epSearchResults').empty();
    var addedIds  = epVideoIds();
    var items     = (data && data.items) ? data.items : [];

    if (items.length === 0) {
        $results.append('<li class="brc-ep-search-empty">No videos found</li>');
    } else {
        items.forEach(function(v) {
            var isAdded = addedIds.indexOf(v.id) !== -1;
            var displayName = v.name || v.id;
            var $item = $('<li>')
                .addClass('brc-ep-search-result-item' + (isAdded ? ' is-added' : ''))
                .attr('data-video-id', v.id)
                .attr('data-video-name', displayName)
                .append($('<span class="brc-ep-search-result-name">').text(displayName))
                .append($('<span class="brc-ep-search-result-id">').text(v.id));
            $results.append($item);
        });
    }
    $results.addClass('is-visible');
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
        if (window.brcCurrentView === 'playlists') {
            var asc = sortType === '';
            // Brightcove playlist IDs are integer strings of varying lengths
            // (e.g. 5822937673001 vs 1860563059155019833). Sorting them via
            // localeCompare gives lexicographic order, which puts shorter
            // (smaller) IDs after longer (larger) ones. Compare by length
            // first to get correct numeric order. JS Number can't safely
            // hold 19-digit IDs (exceeds 2^53), so we stay in string space.
            oCurrentPlaylistList.sort(function (a, b) {
                var av = a[sortBy] != null ? String(a[sortBy]) : '';
                var bv = b[sortBy] != null ? String(b[sortBy]) : '';
                var cmp;
                if (sortBy === 'id') {
                    cmp = av.length !== bv.length ? (av.length - bv.length) : (av < bv ? -1 : av > bv ? 1 : 0);
                } else {
                    cmp = av.localeCompare(bv);
                }
                return asc ? cmp : -cmp;
            });
            buildPlaylistList();
        } else if (sortBy === 'id' && window.brcCurrentView === 'videos') {
            // Brightcove video search does not support sort=id: the CMS API
            // rejects it and returns zero results, which blanked the entire
            // list (BCON-183). Sort the loaded videos client-side by numeric
            // id instead, mirroring the playlist path above. IDs can exceed
            // 2^53 so compare in string space: shorter string = smaller
            // number, then lexicographic for equal lengths.
            var ascId = sortType === '';
            oCurrentVideoList.sort(function (a, b) {
                var av = a.id != null ? String(a.id) : '';
                var bv = b.id != null ? String(b.id) : '';
                var cmp = av.length !== bv.length ? (av.length - bv.length) : (av < bv ? -1 : av > bv ? 1 : 0);
                return ascId ? cmp : -cmp;
            });
            buildMainVideoList(document.getElementById('headTitle').innerHTML);
        } else {
            Load(getAllVideosURLOrdered(sortBy, sortType));
        }
    }
}
function buildMainVideoList(title) {

    window.brcCurrentView = 'videos';
    paging.selectedVideos = [];
    $('#bulkActionBar').attr('hidden', '');
    $('#bulkCount').text(0);
    $('#checkToggle').prop('checked', false);

    //Wipe out the old results
    $("#tbData").empty();
    if (!$("#nameCol").hasClass("ASC") && !$("#nameCol").hasClass("DESC") && !$("#nameCol").hasClass("NONE")) {
        $("#trHeader th.sortable").add("NONE");
        $("#nameCol").addClass("ASC").attr("data-sortType", "");
    }
    // Display video count
    document.getElementById('divVideoCount').innerHTML = oCurrentVideoList.length;
    document.getElementById('nameCol').innerHTML = "Name<span class='order'></span>";
    document.getElementById('headTitle').innerHTML = title;
    document.getElementById('search').value = searchVal ? searchVal : "Search Videos";
    $('#searchClear').toggle(!!searchVal && searchVal !== 'Search Videos');
    document.getElementById('tdMeta').style.display = "none";
    document.getElementById('searchDiv').style.display = "inline-flex";
    document.getElementById('searchDiv_pl').style.display = "none";
    $('#filterToggle').show();

    document.getElementById('checkToggle').style.display = "inline-block";
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
            + ((n.reference_id) ? n.reference_id : '—') +
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
        $('#emptyState').attr('hidden', '');
    }
    else {
        closeBox("tdMeta");
        var hasFilter = (typeof searchVal !== 'undefined' && searchVal && searchVal !== 'Search Videos');
        $('#emptyStateTitle').text(hasFilter ? 'No videos match your search' : 'No videos found');
        $('#emptyStateHint').text(hasFilter ? 'Try clearing the search or adjusting your filters.' : 'Sync the database or add videos in Brightcove.');
        $('#emptyState').removeAttr('hidden');
    }

    // Re-apply the DOM-only clips filter if the checkbox is still active —
    // any folder/label change rebuilds rows fresh and would otherwise show
    // all videos despite the clips-only indicator still being on.
    if ($('#filter_clips').is(':checked')) {
        $('#tbData tr').hide();
        $('#tbData tr.state-clip').show();
        if ($('#tbData tr:visible').length === 0 && oCurrentVideoList.length > 0) {
            $('#emptyStateTitle').text('No clips in this list');
            $('#emptyStateHint').text('Uncheck "Show only clips" to see all videos.');
            $('#emptyState').removeAttr('hidden');
        }
    }

    hideTableSpinner();
}

function buildPlaylistList() {

    window.brcCurrentView = 'playlists';
    paging.selectedVideos = [];
    $('#bulkActionBar').attr('hidden', '');
    $('#bulkCount').text(0);
    $('#checkToggle').prop('checked', false);

    //Wipe out the old results
    $("#tbData").empty();
    if (!$("#nameCol").hasClass("ASC") && !$("#nameCol").hasClass("DESC") && !$("#nameCol").hasClass("NONE")) {
        $("#trHeader th.sortable").addClass("NONE");
        $("#nameCol").removeClass("NONE").addClass("ASC").attr("data-sortType", "");
    }

    // Display Playlist count
    document.getElementById('divVideoCount').innerHTML = oCurrentPlaylistList.length;
    document.getElementById('nameCol').innerHTML = "Name<span class='order'></span>";
    document.getElementById('headTitle').innerHTML = "All Playlists";
    document.getElementById('search_pl').value = searchVal ? searchVal : "Search Playlists";
    $('#searchClear_pl').toggle(!!searchVal && searchVal !== 'Search Playlists');
    document.getElementById('tdMeta').style.display = "none";
    document.getElementById('searchDiv').style.display = "none";
    document.getElementById('searchDiv_pl').style.display = "inline-flex";
    togglePlSearchHint(document.getElementById('selField_pl').value);
    // Filter panel (LABELS / FOLDER / CLIPS ONLY) is video-only.
    $('#filterToggle').hide();
    $('#filterPanel').attr('hidden', '');
    $('#filterToggle').attr('aria-expanded', 'false');
    document.getElementById('checkToggle').style.display = "inline-block";
    document.getElementById('pagination').style.display = "none";
    $("span[name=buttonRow]").hide();
    $(":button[name=delFromPlstButton]").hide();

    //For each retrieved playlist, add a row to the table
    $.each(oCurrentPlaylistList, function (i, n) {
        var dateStr = '—';
        if (n.updated_at) {
            var modDate = new Date(n.updated_at);
            if (!isNaN(modDate.getTime())) {
                dateStr = (modDate.getMonth() + 1) + '/' + modDate.getDate() + '/' + modDate.getFullYear();
            }
        }
        var $row = $('<tr>', { 'id': i, style: 'cursor:pointer;' });
        $row.append(
            $('<td>').append(
                $('<input>', {
                    type: 'checkbox',
                    value: n.id,
                    'data-playlist-name': n.name,
                    onclick: 'checkCheck()'
                })
            )
        );
        $row.append(
            $('<td>').append(
                $('<button>', {
                    type: 'button',
                    class: 'edit-playlist brc-playlist-name-btn',
                    'data-playlist-id': n.id,
                    'data-playlist-name': n.name,
                    'data-playlist-type': n.type || ''
                }).text(n.name)
            )
        );
        $row.append($('<td>').text(dateStr));
        $row.append($('<td>').text(n.reference_id ? n.reference_id : '—'));
        $row.append($('<td>').text(n.id));
        $('#tbData').append($row);
    });

    //Zebra stripe the table
    $("#tbData>tr:even").addClass("oddLine");

    //And add a hover effect
    $("#tbData>tr").hover(function () {
        $(this).addClass("hover");
    }, function () {
        $(this).removeClass("hover");
    });

    if (oCurrentPlaylistList.length > 0) {
        $('#emptyState').attr('hidden', '');
    } else {
        var hasFilter = (typeof searchVal !== 'undefined' && searchVal && searchVal !== 'Search Playlists');
        $('#emptyStateTitle').text(hasFilter ? 'No playlists match your search' : 'No playlists found');
        $('#emptyStateHint').text(hasFilter ? 'Try clearing the search.' : 'Create a playlist in Brightcove to see it here.');
        $('#emptyState').removeAttr('hidden');
    }

    hideTableSpinner();
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
    window.brcCurrentView = 'playlist';
    $('#bulkActionBar').attr('hidden', '');

    //Wipe out the old results
    $("#tbData").empty();

    document.getElementById('divVideoCount').innerHTML = oCurrentVideoList.length;
    document.getElementById('nameCol').innerHTML = "Name<span class='order'></span>";
    document.getElementById('headTitle').innerHTML = oCurrentPlaylistList.name;
    document.getElementById('search').value = "Search Videos";
    $('#searchClear').hide();
    document.getElementById('searchDiv').style.display = "inline-flex";
    document.getElementById('searchDiv_pl').style.display = "none";

    document.getElementById('checkToggle').style.display = "inline-block"
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
            + ((n.reference_id) ? n.reference_id : '—') +
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
        $('#emptyState').attr('hidden', '');
    }
    else {
        closeBox("tdMeta");
        $('#emptyStateTitle').text('No videos in this playlist');
        $('#emptyStateHint').text('Add videos to this playlist in Brightcove to see them here.');
        $('#emptyState').removeAttr('hidden');
    }

}

function showVariants(v, idx) {
    var $container = $('#divMeta\\.variants').empty();
    if (!v || !v.variants || v.variants.length === 0) {
        $container.text('—');
        return;
    }
    v.variants.forEach(function(variant, i) {
        $('<button>')
            .addClass('brc-variant-link')
            .attr('data-variant-id', i)
            .attr('data-video-idx', idx)
            .text(variant.language)
            .appendTo($container);
    });
}

function showMetaData(idx) {
    $("tr.select").removeClass("select");
    idx = oCurrentVideoList.length > idx ? idx : 0;
    $("#tbData>tr:eq(" + idx + ")").addClass("select");

    var v = oCurrentVideoList[idx];

    showVariants(v, idx);

    // Panel header
    document.getElementById('divMeta.name').innerHTML = v.name;
    var modDate = new Date(v.updated_at);
    document.getElementById('divMeta.lastModifiedDate').innerHTML = 'Updated ' + (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();

    // Poster preview
    var $posterPreview = $('#divMeta\\.posterPreview').empty();
    var posterSrc = (v.images && v.images.poster && v.images.poster.src) ? v.images.poster.src : null;
    if (posterSrc) {
        $posterPreview.append($('<img>').attr('src', posterSrc));
        $('#posterUrlBtn').text('ENTER URL');
    } else {
        $posterPreview.append(_cameraIcon());
        $('#posterUrlBtn').text('ENTER URL');
    }

    // Thumbnail preview
    var $thumbPreview = $('#divMeta\\.thumbPreview').empty();
    var thumbSrc = v.thumbnailURL || null;
    if (thumbSrc) {
        $thumbPreview.append($('<img>').attr('src', thumbSrc));
        $('#thumbUrlBtn').text('ENTER URL');
    } else {
        $thumbPreview.append(_cameraIcon());
        $('#thumbUrlBtn').text('ENTER URL');
    }

    // Duration
    var sec = String((Math.floor(v.duration * .001)) % 60);
    sec.length < 2 ? sec = sec + "0" : sec;
    document.getElementById('divMeta.length').innerHTML = Math.floor(v.duration / 60000) + ":" + sec;

    document.getElementById('divMeta.id').innerHTML = v.id;
    document.getElementById('divMeta.shortDescription').textContent = (v.description != null ? v.description : "");

    // Tags
    var tagsObject = "";
    if ("" != v.tags) {
        var tags = v.tags.toString().split(',');
        for (var k = 0; k < tags.length; k++) {
            if (k > 0) tagsObject += ', ';
            tagsObject += '<a style="cursor:pointer;color:blue;text-decoration:underline"' +
                'onclick="searchVal=\'' + tags[k].replace(/\'/gi, "\\\'") + '\';Load(findByTag(\'' + tags[k] + '\'))" >' + tags[k] + '</a>';
        }
    }
    document.getElementById('divMeta.tags').innerHTML = tagsObject;

    // Labels — pill system
    _currentLabels = v.labels ? (Array.isArray(v.labels) ? v.labels.slice() : v.labels.toString().split(',').filter(Boolean)) : [];
    renderLabelPills();
    $('#labelInput').val('');

    // Link
    var linkText = (v.link != null && "" != v.link.text && null != v.link.text) ? v.link.text : (v.link != null && v.link.url != null) ? v.link.url : "";
    var linkURL = (v.link != null && v.link.url != null) ? v.link.url : "";
    document.getElementById('divMeta.linkURL').innerHTML = linkText;
    document.getElementById('divMeta.linkURL').href = linkURL;
    document.getElementById('divMeta.linkText').innerHTML = linkText;

    document.getElementById('divMeta.economics').innerHTML = v.economics;

    modDate = new Date(v.published_at);
    document.getElementById('divMeta.publishedDate').innerHTML = (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();
    document.getElementById('divMeta.referenceId').innerHTML = (v.reference_id != null) ? v.reference_id : "";
    $('#divMeta\\.folder').text(v.folder_id ? v.folder_id : 'All Videos');

    $('#divMeta\\.folder').text(v.folder_id ? v.folder_id : 'All Videos');

    // Text tracks
    $ACTIVE_TRACKS = v.text_tracks != null ? v.text_tracks : "";
    var arr = v.text_tracks != null ? v.text_tracks : "";
    document.getElementById('divMeta.text_tracks').innerHTML = "";

    if (arr.length > 0) {
        var tableTmpl = "<table class=\"tg\"><thead><tr><th class=\"tg-uqo3\">LABEL</th><th class=\"tg-uqo3\">LANGUAGE</th> <th class=\"tg-uqo3\">TYPE</th> <th class=\"tg-uqo3\">DELETE</th> </tr> </thead><tbody id=\"divMeta.text_tracks_table\"></tbody></table>";
        document.getElementById('divMeta.text_tracks').innerHTML = tableTmpl;
        for (var x = 0; x < arr.length; x++) {
            var cur = arr[x];
            var defTrack = cur["default"] ? "default_track" : "";
            document.getElementById('divMeta.text_tracks_table').innerHTML += "<tr class='texttrackrow " + defTrack + "'><td class=\"tg-baqh \">" + cur.label + "</td><td class=\"tg-baqh\">" + cur.srclang + "</td><td class=\"tg-baqh\">" + cur.kind + "</td><td class=\"tg-baqh delete_button\" onClick=\"deleteTrack('" + cur.id + "','" + v.id + "')\">X</td></tr>";
        }
    }
}

function _cameraIcon() {
    return $('<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">' +
        '<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" stroke="#6b7280" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>' +
        '<circle cx="12" cy="13" r="4" stroke="#6b7280" stroke-width="2"/>' +
        '</svg>');
}

function renderLabelPills() {
    var $container = $('#divMeta\\.labels').empty();
    _currentLabels.forEach(function(label) {
        var $pill = $('<span class="brc-label-pill">')
            .append($('<span>').text(label))
            .append(
                $('<button class="brc-label-pill-remove" type="button" aria-label="Remove">').text('×')
                    .on('click', function() {
                        var i = _currentLabels.indexOf(label);
                        if (i > -1) _currentLabels.splice(i, 1);
                        $(this).closest('.brc-label-pill').remove();
                    })
            );
        $container.append($pill);
    });
}

$(document).on('keydown', '#labelInput', function(e) {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    var val = $(this).val().trim();
    if (!val || _currentLabels.indexOf(val) > -1) return;
    _currentLabels.push(val);
    renderLabelPills();
    $(this).val('');
});

function saveLabels() {
    var videoId = $('#divMeta\\.id').text().trim();
    if (!videoId) return;
    var savedLabels = _currentLabels.slice();
    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: $.param({ a: 'update_labels', labels: savedLabels, videoId: videoId }, true),
        async: true,
        success: function() {
            var idx = parseInt($('tr.select').attr('id'), 10);
            if (!isNaN(idx) && oCurrentVideoList[idx]) {
                oCurrentVideoList[idx].labels = savedLabels;
            }
            brcToast('Labels saved');
        }
    });
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

            // Panel header
            document.getElementById('divMeta.name').innerHTML = v.name;
            var modDate = new Date(v.updated_at);
            document.getElementById('divMeta.lastModifiedDate').innerHTML = 'Updated ' + (modDate.getMonth() + 1) + "/" + modDate.getDate() + "/" + modDate.getFullYear();

            // Poster preview
            var $posterPreview = $('#divMeta\\.posterPreview').empty();
            var posterSrc = (v.images && v.images.poster && v.images.poster.src) ? v.images.poster.src : null;
            if (posterSrc) { $posterPreview.append($('<img>').attr('src', posterSrc)); $('#posterUrlBtn').text('ENTER URL'); }
            else { $posterPreview.append(_cameraIcon()); $('#posterUrlBtn').text('ENTER URL'); }

            // Thumbnail preview
            var $thumbPreview = $('#divMeta\\.thumbPreview').empty();
            var thumbSrc = (v.images && v.images.thumbnail && v.images.thumbnail.src) ? v.images.thumbnail.src : null;
            if (thumbSrc) { $thumbPreview.append($('<img>').attr('src', thumbSrc)); $('#thumbUrlBtn').text('ENTER URL'); }
            else { $thumbPreview.append(_cameraIcon()); $('#thumbUrlBtn').text('ENTER URL'); }

            //v.length is the running time of the video in ms
            var sec = String((Math.floor(v.duration * .001)) % 60); //The number of seconds not part of a whole minute
            sec.length < 2 ? sec = sec + "0" : sec;  //Make sure  the one's place 0 is included.
            document.getElementById('divMeta.length').innerHTML = Math.floor(v.duration / 60000) + ":" + sec;

            document.getElementById('divMeta.id').innerHTML = v.id;
            document.getElementById('divMeta.shortDescription').textContent = (v.description != null ? v.description : "");

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

            $('#divMeta\\.folder').text(v.folder_id ? v.folder_id : 'All Videos');

            _currentLabels = v.labels ? (Array.isArray(v.labels) ? v.labels.slice() : v.labels.toString().split(',').filter(Boolean)) : [];
            renderLabelPills();
            $('#labelInput').val('');

            var vidIdx = 0;
            for (var i = 0; i < oCurrentVideoList.length; i++) {
                if (String(oCurrentVideoList[i].id) === String(v.id)) { vidIdx = i; break; }
            }
            showVariants(v, vidIdx);

            $ACTIVE_TRACKS = v.text_tracks != null ? v.text_tracks : "";
            var arr = v.text_tracks != null ? v.text_tracks : "";
            document.getElementById('divMeta.text_tracks').innerHTML = "";

            if (arr.length > 0) {
                var tableTmpl = "<table class=\"tg\"><thead><tr><th class=\"tg-uqo3\">LABEL</th><th class=\"tg-uqo3\">LANGUAGE</th> <th class=\"tg-uqo3\">TYPE</th> <th class=\"tg-uqo3\">DELETE</th> </tr> </thead><tbody id=\"divMeta.text_tracks_table\"></tbody></table>";
                document.getElementById('divMeta.text_tracks').innerHTML = tableTmpl;
                for (var x = 0; x < arr.length; x++) {
                    var cur = arr[x];
                    var defTrack = cur["default"] ? "default_track" : "";
                    document.getElementById('divMeta.text_tracks_table').innerHTML += "<tr class='texttrackrow " + defTrack + "'><td class=\"tg-baqh \">" + cur.label + "</td><td class=\"tg-baqh\">" + cur.srclang + "</td><td class=\"tg-baqh\">" + cur.kind + "</td><td class=\"tg-baqh delete_button\" onClick=\"deleteTrack('" + cur.id + "','" + v.id + "')\">X</td></tr>";
                }
            }
            $('#divMeta\\.folder').text(v.folder_id ? v.folder_id : 'All Videos');

            _currentLabels = v.labels ? (Array.isArray(v.labels) ? v.labels.slice() : v.labels.toString().split(',').filter(Boolean)) : [];
            renderLabelPills();
            $('#labelInput').val('');

            var vidIdx = 0;
            for (var i = 0; i < oCurrentVideoList.length; i++) {
                if (String(oCurrentVideoList[i].id) === String(v.id)) { vidIdx = i; break; }
            }
            showVariants(v, vidIdx);

            $("#tdMeta").show();
        },
        error: function () {
            alert("Error");
        }
    });
}


function _cameraIcon() {
    return $('<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">' +
        '<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" stroke="#6b7280" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>' +
        '<circle cx="12" cy="13" r="4" stroke="#6b7280" stroke-width="2"/>' +
        '</svg>');
}

function renderLabelPills() {
    var $container = $('#divMeta\\.labels').empty();
    _currentLabels.forEach(function(label) {
        var $pill = $('<span class="brc-label-pill">')
            .append($('<span>').text(label))
            .append(
                $('<button class="brc-label-pill-remove" type="button" aria-label="Remove">').text('×')
                    .on('click', function() {
                        var i = _currentLabels.indexOf(label);
                        if (i > -1) _currentLabels.splice(i, 1);
                        $(this).closest('.brc-label-pill').remove();
                    })
            );
        $container.append($pill);
    });
}

$(document).on('keydown', '#labelInput', function(e) {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    var val = $(this).val().trim();
    if (!val || _currentLabels.indexOf(val) > -1) return;
    _currentLabels.push(val);
    renderLabelPills();
    $(this).val('');
});

function saveLabels() {
    var videoId = $('#divMeta\\.id').text().trim();
    if (!videoId) return;
    var savedLabels = _currentLabels.slice();
    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: $.param({ a: 'update_labels', labels: savedLabels, videoId: videoId }, true),
        async: true,
        success: function() {
            var idx = parseInt($('tr.select').attr('id'), 10);
            if (!isNaN(idx) && oCurrentVideoList[idx]) {
                oCurrentVideoList[idx].labels = savedLabels;
            }
            brcToast('Labels saved');
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
        },
        error: function () {
            // Without this, a failed sync leaves #syncdbutton stuck in
            // .is-loading + disabled forever (the old loadEnd() reset
            // the button as a side effect; that side effect was removed
            // when loadEnd stopped clobbering the new SVG/label markup).
            syncEnd();
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
          'value="' + ($('#divMeta\\.posterPreview img').attr('src') || '') + '"' +
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
                success: function () {
                    var url = $('#upload_poster_dialog_field_source').val();
                    $('#divMeta\\.posterPreview').empty().append($('<img>').attr('src', url));
                    $('#posterUrlBtn').text('ENTER URL');
                    dialog.hide();
                    brcToast('Poster updated');
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
          'value="' + ($('#divMeta\\.thumbPreview img').attr('src') || '') + '"' +
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
                success: function () {
                    var url = $('#upload_thumbnail_dialog_field_source').val();
                    $('#divMeta\\.thumbPreview').empty().append($('<img>').attr('src', url));
                    $('#thumbUrlBtn').text('ENTER URL');
                    dialog.hide();
                    brcToast('Thumbnail updated');
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

    // Build language options list once
    if (!_uttLanguageOptions) {
        _uttLanguageOptions = language_options.map(function(opt) {
            return opt.content.textContent;
        });
    }

    // Reset form fields and state
    _uttLangValid = false;
    $('#uttLanguage').val('');
    $('#uttLangSuggestions').attr('hidden', '').empty();
    $('#uttLabel').val('');
    $('#uttKind').val('subtitles');
    $('#uttDefault').prop('checked', false);
    $('#uttSourceUrl').val('').prop('disabled', false);
    $('#uttFile').val('');
    $('#uttFilePill').attr('hidden', '');
    $('#uttFileName').text('');
    $('#uttFileBtn').show();
    $('#uttUpload').prop('disabled', true);

    $('#uploadTextTrackModal').removeAttr('hidden');
    document.body.style.overflow = 'hidden';
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

// BCON-148: loadStart/loadEnd now use the scoped table spinner (BCON-141)
// instead of the old top-right toast. The toast has been removed.
function loadStart()
{
    showTableSpinner();
}

function loadEnd()
{
    $('html,body').scrollTop(0);
    hideTableSpinner();
}

function syncStart()
{
    $("#syncdbutton").addClass('is-loading').prop('disabled', true);
    $("#syncdbutton .brc-sync-btn-label").text('Loading sync');
    $('#syncOverlay').removeAttr('hidden');
}

function syncEnd() {
    $('#syncOverlay').attr('hidden', '');
    $("#syncdbutton").removeClass('is-loading').prop('disabled', false);
    $("#syncdbutton .brc-sync-btn-label").text('Sync database');
}



// ─── Create Playlist Modal ────────────────────────────────────────────────────

function openCreatePlaylistModal(videos) {
    _cpVideos = videos || [];
    $('#cpTitle_input').val('');
    $('#cpDescription').val('');
    cpRenderVideoTable();
    cpUpdateButton();
    $('#createPlaylistModal').removeAttr('hidden');
    document.body.style.overflow = 'hidden';
    setTimeout(function() { $('#cpTitle_input').focus(); }, 50);
}

function closeCreatePlaylistModal() {
    $('#createPlaylistModal').attr('hidden', '');
    document.body.style.overflow = '';
    _cpVideos = [];
}

function cpRenderVideoTable() {
    var $tbody = $('#cpVideoTableBody').empty();
    _cpVideos.forEach(function(v) {
        $tbody.append(
            '<tr><td>' + escapeHtml(v.name) + '</td><td>' + v.id + '</td></tr>'
        );
    });
}

function cpUpdateButton() {
    var hasTitle = $('#cpTitle_input').val().trim().length > 0;
    $('#cpSubmitBtn').prop('disabled', !hasTitle);
}

function cpSubmit() {
    var title = $('#cpTitle_input').val().trim();
    if (!title) return;

    $('#cpSubmitBtn, #cpCancel').prop('disabled', true);

    var videoIds = _cpVideos.map(function(v) { return v.id; }).join(',');
    $.ajax({
        type: 'GET',
        url: '/bin/brightcove/api.js',
        data: {
            a: 'create_playlist',
            account_id: $('#selAccount').val(),
            'plst.name': title,
            'plst.shortDescription': $('#cpDescription').val().trim(),
            'plst.referenceId': '',
            playlist: videoIds
        },
        success: function() {
            closeCreatePlaylistModal();
            brcToast('Playlist created');
            $('#allPlaylists').click();
        },
        error: function() {
            $('#cpSubmitBtn, #cpCancel').prop('disabled', false);
        }
    });
}

function createPlaylistBox() {
    var videos = [];
    $('#tbData input[type="checkbox"]').each(function () {
        if (!this.checked) return;
        var videoIdx = parseInt(this.id, 10);
        var v = oCurrentVideoList[videoIdx];
        if (v) videos.push({ id: v.id, name: v.name });
    });
    if (videos.length === 0) {
        alert("Please select at least one video to create a playlist.");
        return;
    }
    openCreatePlaylistModal(videos);
}

/*show a preview of the selected video
 * this works by embedding an iframe into an existing hidden div.
 * the iframe opens the brightcove player and passes the requested videoId.
 *In console 1, click players, get publishing code.  Copy the Player URL
 * and assign it to the variable previewPlayerLoc at the top of this document.
 * In the publishing module click get code and select Player URL.
 */
function doPreview(id) {
    var title = document.getElementById('divMeta.name').innerHTML;
    $('#vpTitle').text(title);

    var $player = $('#vpPlayer').empty();
    var iframe = document.createElement('iframe');
    iframe.setAttribute('src', brc_admin.previewPlayerLoc + id);
    iframe.setAttribute('frameborder', 0);
    iframe.setAttribute('scrolling', 'no');
    iframe.setAttribute('allowfullscreen', true);
    iframe.setAttribute('id', 'previewPlayer');
    $player.append(iframe);

    $('#videoPreviewModal').removeAttr('hidden');
    document.body.style.overflow = 'hidden';
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
    $('#videoPreviewModal').attr('hidden', '');
    $('#vpPlayer').empty();
    document.body.style.overflow = '';
}

//type should be playlists or videos
function doPageList(total, type) {
	if (type !== "Playlists") {
	    if (total > paging.size) {
	        var numOpt = Math.ceil(total / paging.size);
	        var options = "";
	        for (var i = 0; i < numOpt; i++) {
	            options += '<option style="width:100%" id="' + i + '">';
	            if (paging.generic == i) {
	                num = (numOpt - 1 == i) ? (total - i * paging.size) : paging.size;
	                document.getElementById('divVideoCount').innerHTML = num;
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
	    } else {
	        document.getElementById('divVideoCount').innerHTML = total;
	        $("div[name=pageDiv]").hide();
	    }
	}
}

function changePage(num) {
    paging.generic = num * paging.size;
    Load(paging.currentFunction());
}

function checkCheck() {
    // Master #checkToggle reflects "are all row checkboxes checked?"
    var $rows = $('#tbData input[type="checkbox"]');
    var total = $rows.length;
    var checked = $rows.filter(':checked').length;
    document.getElementById('checkToggle').checked = (total > 0 && checked === total);
    $('body').trigger('brc:checked');
}

function toggleSelect(check) {
    if (check.checked) {
        checkAll();
    } else {
        checkNone();
    }
    $('body').trigger('brc:checked');
}

function checkAll() {
    $('#tbData input[type="checkbox"]').prop('checked', true);
}

function checkNone() {
    $('#tbData input[type="checkbox"]').prop('checked', false);
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
